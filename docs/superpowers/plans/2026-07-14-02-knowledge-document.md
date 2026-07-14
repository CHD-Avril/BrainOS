# BrainOS Phase 2 Knowledge and Document Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver shared knowledge-base CRUD and a reliable PDF/DOCX/TXT/Markdown ingestion pipeline that produces queryable Chroma vectors with visible processing states.

**Architecture:** Keep business metadata in MySQL, original files in a configured local directory, and chunks/embeddings in one Chroma collection filtered by knowledge-base metadata. Isolate storage, parsing, embedding and vector indexing behind ports so each transition and compensation path is testable.

**Tech Stack:** Spring Boot 3.5.16, MyBatis-Plus, Apache Tika, Spring AI 1.1.8 Chroma Vector Store, Qwen OpenAI-compatible Embedding API, Vue 3, Element Plus, Testcontainers, Vitest.

## Global Constraints

- Accept only PDF, DOCX, TXT, Markdown; maximum size is 20MB; OCR is excluded.
- A knowledge-base name is globally unique and 2-60 trimmed characters.
- Document IDs in Chroma use `documentId:chunkIndex`; metadata keys are fixed to `knowledgeBaseId`, `documentId`, `fileName`, `pageNumber`, `chunkIndex`.
- A document may only transition `PARSING -> INDEXING -> READY` or into `FAILED`; retry starts at `PARSING` after vector cleanup.
- File validation checks extension, MIME, normalized file name, size and per-knowledge-base SHA-256 uniqueness.
- UI remains PC-only and follows `design-system/MASTER.md`.

---

### Task 1: Knowledge-Base Domain and API

**Files:**
- Create: `backend/src/main/java/com/brainos/knowledge/domain/KnowledgeBase.java`
- Create: `backend/src/main/java/com/brainos/knowledge/persistence/KnowledgeBaseMapper.java`
- Create: `backend/src/main/java/com/brainos/knowledge/application/KnowledgeBaseService.java`
- Create: `backend/src/main/java/com/brainos/knowledge/api/KnowledgeBaseController.java`
- Create: `backend/src/main/java/com/brainos/knowledge/api/KnowledgeBaseCreateRequest.java`
- Create: `backend/src/main/java/com/brainos/knowledge/api/KnowledgeBaseView.java`
- Create: `backend/src/test/java/com/brainos/knowledge/KnowledgeBaseServiceTest.java`

**Interfaces:**
- Produces: `create`, `list`, `get`, `update`, `delete` on `KnowledgeBaseService`.
- Produces: `KnowledgeBaseCleanupPort.cleanup(Long knowledgeBaseId)` for later document/rag integration.

- [ ] **Step 1: Write failing uniqueness and normalization tests**

```java
@Test
void createTrimsNameAndRejectsDuplicate() {
    FakeKnowledgeBases repository = new FakeKnowledgeBases();
    KnowledgeBaseService service = new KnowledgeBaseService(repository, id -> {});
    KnowledgeBaseView created = service.create(new CreateKnowledgeBaseCommand("  产品手册  ", "资料", 1L));
    assertThat(created.name()).isEqualTo("产品手册");
    assertThrows(DuplicateKnowledgeBaseException.class,
        () -> service.create(new CreateKnowledgeBaseCommand("产品手册", "重复", 2L)));
}
```

- [ ] **Step 2: Run RED**

Run: `cd backend && ./mvnw -Dtest=KnowledgeBaseServiceTest test`

Expected: compilation fails because knowledge service types do not exist.

- [ ] **Step 3: Implement domain, mapper, service and controller**

Validate the normalized name before persistence, translate duplicate-key races into `CONFLICT`, return computed document counts in list views, and call the cleanup port before deleting the MySQL row.

```java
@Transactional
public KnowledgeBaseView create(CreateKnowledgeBaseCommand command) {
    String name = command.name().trim();
    if (name.length() < 2 || name.length() > 60) throw new InvalidKnowledgeBaseNameException();
    if (repository.existsByName(name)) throw new DuplicateKnowledgeBaseException(name);
    KnowledgeBase saved = repository.save(KnowledgeBase.create(name, command.description().trim(), command.userId()));
    return mapper.toView(saved, 0L, 0L);
}

@Transactional
public void delete(long id) {
    repository.getRequired(id);
    cleanup.cleanup(id);
    repository.delete(id);
}
```

- [ ] **Step 4: Run GREEN**

Run: `cd backend && ./mvnw -Dtest=KnowledgeBaseServiceTest,KnowledgeBaseControllerIT test`

Expected: create/list/update/delete, duplicate and authorization tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/brainos/knowledge backend/src/test/java/com/brainos/knowledge
git commit -m "feat: manage shared knowledge bases"
```

### Task 2: Upload Validation and Local File Storage

**Files:**
- Create: `backend/src/main/java/com/brainos/document/domain/DocumentStatus.java`
- Create: `backend/src/main/java/com/brainos/document/domain/KnowledgeDocument.java`
- Create: `backend/src/main/java/com/brainos/document/storage/FileStoragePort.java`
- Create: `backend/src/main/java/com/brainos/document/storage/LocalFileStorage.java`
- Create: `backend/src/main/java/com/brainos/document/application/DocumentUploadService.java`
- Create: `backend/src/test/java/com/brainos/document/DocumentUploadServiceTest.java`

**Interfaces:**
- Produces: `DocumentView upload(Long knowledgeBaseId, MultipartFile file, Long userId)`.
- Produces: `StoredFile store(InputStream content, String safeExtension)` and `void delete(Path path)`.

- [ ] **Step 1: Write failing validation tests**

```java
@ParameterizedTest
@ValueSource(strings = {"manual.exe", "../secret.pdf", "image.png"})
void unsafeOrUnsupportedFilesAreRejected(String name) {
    MockMultipartFile file = new MockMultipartFile("file", name, "application/octet-stream", "data".getBytes());
    assertThrows(InvalidDocumentException.class, () -> service.upload(9L, file, 1L));
    assertThat(storage.files()).isEmpty();
}

@Test
void duplicateHashInKnowledgeBaseIsRejected() {
    MockMultipartFile file = textFile("policy.txt", "same content");
    service.upload(9L, file, 1L);
    assertThrows(DuplicateDocumentException.class, () -> service.upload(9L, file, 1L));
}
```

- [ ] **Step 2: Run RED**

Run: `cd backend && ./mvnw -Dtest=DocumentUploadServiceTest test`

Expected: document upload types do not exist.

- [ ] **Step 3: Implement upload and compensation**

Stream the upload once into a temporary file while computing SHA-256, validate the first bytes and MIME through Tika, atomically move to `{storageRoot}/{knowledgeBaseId}/{uuid}.{ext}`, then insert `PARSING`. If persistence fails, delete the stored file.

```java
public DocumentView upload(long knowledgeBaseId, MultipartFile upload, long userId) {
    ValidatedUpload valid = validator.validate(upload, MAX_BYTES, SUPPORTED_TYPES);
    StoredFile stored = storage.store(knowledgeBaseId, valid.stream(), valid.extension());
    try {
        String sha256 = digests.sha256(stored.path());
        if (documents.existsByKnowledgeBaseAndSha256(knowledgeBaseId, sha256)) throw new DuplicateDocumentException();
        KnowledgeDocument saved = documents.save(KnowledgeDocument.parsing(
            knowledgeBaseId, valid.originalName(), stored.path(), valid.mimeType(), valid.size(), sha256, userId));
        executor.submit(saved.id());
        return mapper.toView(saved);
    } catch (RuntimeException error) {
        storage.delete(stored.path());
        throw error;
    }
}
```

- [ ] **Step 4: Run GREEN**

Run: `cd backend && ./mvnw -Dtest=DocumentUploadServiceTest,LocalFileStorageTest test`

Expected: valid formats, 20MB boundary, traversal, MIME mismatch, duplicate and compensation tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/brainos/document backend/src/test/java/com/brainos/document
git commit -m "feat: validate and store uploaded documents"
```

### Task 3: Parsing and Stable Chunking

**Files:**
- Create: `backend/src/main/java/com/brainos/document/parsing/DocumentParserPort.java`
- Create: `backend/src/main/java/com/brainos/document/parsing/TikaDocumentParser.java`
- Create: `backend/src/main/java/com/brainos/document/indexing/DocumentChunk.java`
- Create: `backend/src/main/java/com/brainos/document/indexing/DocumentChunker.java`
- Create: `backend/src/test/java/com/brainos/document/parsing/TikaDocumentParserTest.java`
- Create: `backend/src/test/resources/documents/sample.pdf`
- Create: `backend/src/test/resources/documents/sample.docx`

**Interfaces:**
- Produces: `List<ParsedSection> parse(Path file, String mimeType)` with `text` and `pageNumber`.
- Produces: `List<DocumentChunk> split(Long knowledgeBaseId, Long documentId, String fileName, List<ParsedSection> sections)`.

- [ ] **Step 1: Write failing parsing and ID tests**

```java
@Test
void chunkIdsAndMetadataAreStable() {
    List<DocumentChunk> chunks = chunker.split(3L, 8L, "policy.pdf",
        List.of(new ParsedSection("企业请假制度正文", 2)));
    assertThat(chunks).isNotEmpty();
    assertThat(chunks.getFirst().id()).isEqualTo("8:0");
    assertThat(chunks.getFirst().metadata()).containsEntry("knowledgeBaseId", 3L)
        .containsEntry("documentId", 8L).containsEntry("pageNumber", 2)
        .containsEntry("chunkIndex", 0);
}

@Test
void emptyScannedPdfIsAReadableFailure() {
    assertThatThrownBy(() -> parser.parse(scannedPdf, "application/pdf"))
        .isInstanceOf(NoUsableTextException.class)
        .hasMessage("未提取到可用文本");
}
```

- [ ] **Step 2: Run RED**

Run: `cd backend && ./mvnw -Dtest=TikaDocumentParserTest,DocumentChunkerTest test`

Expected: parsing/chunking types do not exist.

- [ ] **Step 3: Implement parsers and chunker**

Use Tika for type-specific extraction, preserve PDF page numbers when available, normalize whitespace, reject blank results, and split with Spring AI `TokenTextSplitter` using a fixed chunk size/overlap recorded in application configuration.

```java
public List<DocumentChunk> split(long knowledgeBaseId, long documentId, String fileName, List<ParsedSection> sections) {
    AtomicInteger index = new AtomicInteger();
    List<DocumentChunk> chunks = sections.stream()
        .flatMap(section -> splitter.apply(List.of(new Document(section.text()))).stream()
            .map(piece -> DocumentChunk.of(documentId + ":" + index.get(), knowledgeBaseId, documentId,
                fileName, section.pageNumber(), index.getAndIncrement(), piece.getText())))
        .toList();
    if (chunks.isEmpty()) throw new NoUsableTextException();
    return chunks;
}
```

- [ ] **Step 4: Run GREEN**

Run: `cd backend && ./mvnw -Dtest=TikaDocumentParserTest,DocumentChunkerTest test`

Expected: all four supported formats, page metadata, stable IDs and no-text failure pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/brainos/document backend/src/test/java/com/brainos/document backend/src/test/resources/documents
git commit -m "feat: parse and chunk enterprise documents"
```

### Task 4: Qwen Embedding and Chroma Index

**Files:**
- Create: `backend/src/main/java/com/brainos/document/indexing/EmbeddingPort.java`
- Create: `backend/src/main/java/com/brainos/document/indexing/VectorIndexPort.java`
- Create: `backend/src/main/java/com/brainos/document/indexing/SpringAiVectorIndex.java`
- Create: `backend/src/main/java/com/brainos/document/indexing/AiVectorConfig.java`
- Create: `backend/src/test/java/com/brainos/document/indexing/ChromaVectorIndexIT.java`

**Interfaces:**
- Produces: `void replaceDocument(Long documentId, List<DocumentChunk> chunks)`.
- Produces: `List<RetrievedChunk> search(Long knowledgeBaseId, float[] query, int topK, double threshold)`.
- Produces: `void deleteDocument(Long documentId)`.

- [ ] **Step 1: Write the failing Chroma isolation test**

```java
@Test
void searchNeverLeaksAnotherKnowledgeBase() {
    index.replaceDocument(11L, chunks(1L, 11L, "年假为十天"));
    index.replaceDocument(12L, chunks(2L, 12L, "年假为二十天"));
    List<RetrievedChunk> result = index.search(1L, embedding("年假"), 5, 0.0);
    assertThat(result).allMatch(item -> item.knowledgeBaseId().equals(1L));
    assertThat(result).noneMatch(item -> item.documentId().equals(12L));
}
```

- [ ] **Step 2: Run RED**

Run: `docker compose up -d chroma && cd backend && ./mvnw -Dtest=ChromaVectorIndexIT test`

Expected: vector index implementation is missing.

- [ ] **Step 3: Implement Qwen and Chroma adapters**

Configure a dedicated Spring AI OpenAI-compatible `EmbeddingModel` with the DashScope base URL and `QWEN_API_KEY`. Convert each `DocumentChunk` into a Spring AI `Document` preserving the fixed metadata; delete by `documentId` filter before add; filter search by `knowledgeBaseId`.

```java
@Override
public void replaceDocument(long documentId, List<DocumentChunk> chunks) {
    vectorStore.delete("documentId == " + documentId);
    vectorStore.add(chunks.stream().map(chunk -> new Document(
        chunk.id(), chunk.text(), Map.of(
            "knowledgeBaseId", chunk.knowledgeBaseId(),
            "documentId", chunk.documentId(),
            "fileName", chunk.fileName(),
            "pageNumber", chunk.pageNumber() == null ? -1 : chunk.pageNumber(),
            "chunkIndex", chunk.chunkIndex()))).toList());
}
```

- [ ] **Step 4: Run GREEN**

Run: `cd backend && ./mvnw -Dtest=ChromaVectorIndexIT test`

Expected: add, replace, knowledge isolation, threshold and delete tests pass. Provider network calls use a deterministic local fake embedding model; a separately tagged smoke test may use a real key.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/brainos/document/indexing backend/src/test/java/com/brainos/document/indexing
git commit -m "feat: index document chunks in Chroma"
```

### Task 5: Asynchronous Indexing, Retry, Delete, and API

**Files:**
- Create: `backend/src/main/java/com/brainos/document/application/DocumentIndexingService.java`
- Create: `backend/src/main/java/com/brainos/document/application/DocumentIndexingExecutor.java`
- Create: `backend/src/main/java/com/brainos/document/api/DocumentController.java`
- Create: `backend/src/test/java/com/brainos/document/DocumentIndexingServiceTest.java`

**Interfaces:**
- Produces: `void index(Long documentId)`, `void retry(Long documentId)`, `void delete(Long documentId)`.
- Produces: upload/list/status/retry/delete REST API.

- [ ] **Step 1: Write failing state and compensation test**

```java
@Test
void embeddingFailureMarksFailedAndRemovesPartialVectors() {
    embedding.failWith(new RuntimeException("provider unavailable"));
    service.index(44L);
    KnowledgeDocument document = documents.getRequired(44L);
    assertThat(document.status()).isEqualTo(DocumentStatus.FAILED);
    assertThat(document.failureReason()).isEqualTo("向量模型暂时不可用，请稍后重试");
    assertThat(index.countByDocumentId(44L)).isZero();
}
```

- [ ] **Step 2: Run RED**

Run: `cd backend && ./mvnw -Dtest=DocumentIndexingServiceTest test`

Expected: orchestration types do not exist.

- [ ] **Step 3: Implement orchestration and controller**

Persist each state before the external step, clear `failureReason` on retry, delete old vectors before replacement, cap the executor concurrency, and return polling-friendly views. Map provider and parser exceptions to stable Chinese failure reasons.

```java
public void index(long documentId) {
    KnowledgeDocument document = documents.getRequired(documentId);
    try {
        List<ParsedSection> sections = parser.parse(document.storagePath(), document.mimeType());
        documents.markIndexing(documentId);
        List<DocumentChunk> chunks = chunker.split(document.knowledgeBaseId(), document.id(), document.originalName(), sections);
        vectorIndex.replaceDocument(documentId, chunks);
        documents.markReady(documentId, chunks.size());
    } catch (RuntimeException error) {
        vectorIndex.deleteDocument(documentId);
        documents.markFailed(documentId, failureMessages.forException(error));
    }
}
```

- [ ] **Step 4: Run GREEN and backend regression**

Run: `cd backend && ./mvnw -Dtest=DocumentIndexingServiceTest,DocumentControllerIT test && ./mvnw test`

Expected: success, no-text, provider failure, retry idempotence and delete cleanup pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/brainos/document backend/src/test/java/com/brainos/document
git commit -m "feat: orchestrate document indexing lifecycle"
```

### Task 6: Knowledge and Document Vue Pages

**Files:**
- Create: `frontend/src/features/knowledge/api.ts`, `store.ts`, `KnowledgeListView.vue`, `KnowledgeCard.vue`
- Create: `frontend/src/features/document/api.ts`, `DocumentListView.vue`, `DocumentUpload.vue`, `DocumentStatusTag.vue`
- Create: `frontend/src/features/document/DocumentListView.spec.ts`

**Interfaces:**
- Produces: knowledge routes and `/knowledge-bases/:id/documents`.
- Produces: status polling while any row is `PARSING` or `INDEXING`.

- [ ] **Step 1: Write the failing status-flow test**

```ts
it('polls processing documents and exposes retry for failure', async () => {
  api.listDocuments.mockResolvedValueOnce([row({ status: 'INDEXING' })])
    .mockResolvedValueOnce([row({ status: 'FAILED', failureReason: '未提取到可用文本' })])
  const wrapper = mountDocumentList()
  await flushPromises()
  vi.advanceTimersByTime(2000)
  await flushPromises()
  expect(api.listDocuments).toHaveBeenCalledTimes(2)
  expect(wrapper.get('[data-test="failure-reason"]').text()).toContain('未提取到可用文本')
  expect(wrapper.get('[data-test="retry"]').isVisible()).toBe(true)
})
```

- [ ] **Step 2: Run RED**

Run: `cd frontend && pnpm vitest run src/features/document/DocumentListView.spec.ts`

Expected: page and status components are missing.

- [ ] **Step 3: Implement the pages**

Use the approved three-column knowledge cards, visible new/edit dialogs, an inline upload area and a document table. Show status with text + icon + color; display failure reason and direct retry/delete actions. Stop polling on unmount and when no processing rows remain.

```ts
const refresh = async () => {
  rows.value = await documentApi.list(knowledgeBaseId.value)
  const processing = rows.value.some(row => row.status === 'PARSING' || row.status === 'INDEXING')
  if (processing) timer = window.setTimeout(refresh, 2000)
}

onMounted(refresh)
onUnmounted(() => window.clearTimeout(timer))
```

- [ ] **Step 4: Run GREEN and desktop visual checks**

Run: `cd frontend && pnpm vitest run && pnpm vue-tsc --noEmit && pnpm build`

Expected: component tests pass and pages build without TypeScript errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features frontend/src/router
git commit -m "feat: add knowledge and document workspace"
```

### Task 7: Phase 2 End-to-End Verification

**Files:**
- Create: `frontend/e2e/document-ingestion.spec.ts`
- Create: `fixtures/employee-handbook.md`

- [ ] **Step 1: Write and run the failing main-path E2E**

```ts
test('creates a knowledge base and indexes a document', async ({ page }) => {
  await loginAsAdmin(page)
  await page.getByRole('link', { name: '知识库' }).click()
  await page.getByRole('button', { name: '新建知识库' }).click()
  await page.getByLabel('名称').fill('员工制度')
  await page.getByRole('button', { name: '确定' }).click()
  await page.getByText('员工制度').click()
  await page.getByLabel('上传文档').setInputFiles('../fixtures/employee-handbook.md')
  await expect(page.getByText('可用')).toBeVisible({ timeout: 30_000 })
})
```

Run: `cd frontend && pnpm playwright test e2e/document-ingestion.spec.ts`

Expected before wiring: test fails before READY is visible.

- [ ] **Step 2: Fix only failures revealed by the E2E, then verify**

Run: `bash scripts/verify.sh && cd frontend && pnpm playwright test e2e/document-ingestion.spec.ts`

Expected: full regression and document-ingestion flow pass.

- [ ] **Step 3: Commit**

```bash
git add frontend/e2e fixtures scripts
git commit -m "test: verify document ingestion flow"
```
