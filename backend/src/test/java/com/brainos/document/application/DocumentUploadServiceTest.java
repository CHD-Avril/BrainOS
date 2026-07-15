package com.brainos.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brainos.common.api.ApiException;
import com.brainos.document.domain.DocumentRepository;
import com.brainos.document.domain.DocumentStatus;
import com.brainos.document.domain.DocumentView;
import com.brainos.document.domain.KnowledgeDocument;
import com.brainos.document.storage.LocalFileStorage;
import com.brainos.knowledge.domain.KnowledgeBase;
import com.brainos.knowledge.domain.KnowledgeBaseRepository;
import com.brainos.knowledge.domain.KnowledgeBaseView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;

class DocumentUploadServiceTest {

    @TempDir Path root;

    @ParameterizedTest
    @MethodSource("supportedDocuments")
    void storesSupportedDocumentsInParsingState(
            String fileName, String contentType, byte[] content, String canonicalMime) {
        FakeDocuments documents = new FakeDocuments();
        DocumentUploadService service = service(documents);

        DocumentView uploaded = service.upload(
                1L, new MockMultipartFile("file", fileName, contentType, content), 7L);

        assertThat(uploaded.originalName()).isEqualTo(fileName);
        assertThat(uploaded.mimeType()).isEqualTo(canonicalMime);
        assertThat(uploaded.status()).isEqualTo(DocumentStatus.PARSING);
        assertThat(uploaded.knowledgeBaseId()).isEqualTo(1L);
        assertThat(uploaded.uploadedBy()).isEqualTo(7L);
        assertThat(uploaded.sha256()).hasSize(64);
        assertThat(Path.of(uploaded.storagePath())).isRegularFile().startsWith(root.resolve("1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"../secret.pdf", "folder/manual.pdf", "folder\\manual.pdf",
            "manual.exe", "manual", "manual.pdf\u0000.txt"})
    void rejectsUnsafeOrUnsupportedNamesWithoutWriting(String fileName) throws IOException {
        DocumentUploadService service = service(new FakeDocuments());

        assertValidation(() -> service.upload(
                1L,
                new MockMultipartFile("file", fileName, "application/pdf", pdf()),
                7L));

        assertThat(regularFiles()).isEmpty();
    }

    @Test
    void rejectsEmptyDeclaredOversizeAndMimeMismatch() throws IOException {
        DocumentUploadService service = service(new FakeDocuments());

        assertValidation(() -> service.upload(
                1L, new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]), 7L));
        assertValidation(() -> service.upload(
                1L,
                new SizedMultipartFile("large.txt", "text/plain", DocumentUploadService.MAX_BYTES + 1),
                7L));
        assertValidation(() -> service.upload(
                1L,
                new MockMultipartFile("file", "notes.txt", "image/png", "hello".getBytes()),
                7L));
        assertValidation(() -> service.upload(
                1L,
                new MockMultipartFile("file", "fake.pdf", "application/pdf", "hello".getBytes()),
                7L));
        assertValidation(() -> service.upload(
                1L,
                new MockMultipartFile(
                        "file", "x".repeat(252) + ".txt", "text/plain", "hello".getBytes()),
                7L));

        assertThat(regularFiles()).isEmpty();
    }

    @Test
    void rejectsFakeUnsafeAndBombDocxPackages() throws IOException {
        DocumentUploadService service = service(new FakeDocuments());
        String mime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

        assertValidation(() -> service.upload(
                1L, new MockMultipartFile("file", "fake.docx", mime, malformedDocx()), 7L));
        assertValidation(() -> service.upload(
                1L,
                new MockMultipartFile(
                        "file", "unsafe.docx", mime, docxWith("../escape.txt", "escape".getBytes())),
                7L));
        assertValidation(() -> service.upload(
                1L,
                new MockMultipartFile(
                        "file", "bomb.docx", mime, docxWith("word/bomb.bin", new byte[1024 * 1024])),
                7L));

        assertThat(regularFiles()).isEmpty();
    }

    @Test
    void acceptsStandardMimeParameters() {
        DocumentUploadService service = service(new FakeDocuments());

        DocumentView uploaded = service.upload(
                1L,
                new MockMultipartFile(
                        "file",
                        "policy.txt",
                        "text/plain; charset=UTF-8",
                        "制度".getBytes(StandardCharsets.UTF_8)),
                7L);

        assertThat(uploaded.mimeType()).isEqualTo("text/plain");
    }

    @Test
    void rejectsDuplicateInsideKnowledgeBaseButAllowsSameContentAcrossBases() {
        FakeDocuments documents = new FakeDocuments();
        DocumentUploadService service = service(documents);
        byte[] content = "same enterprise policy".getBytes(StandardCharsets.UTF_8);

        service.upload(1L, text("policy.txt", content), 7L);
        assertConflict(() -> service.upload(1L, text("copy.txt", content), 7L));
        DocumentView otherBase = service.upload(2L, text("policy.txt", content), 7L);

        assertThat(otherBase.knowledgeBaseId()).isEqualTo(2L);
        assertThat(documents.rows).hasSize(2);
    }

    @Test
    void persistenceRaceDeletesStoredFile() throws IOException {
        FakeDocuments documents = new FakeDocuments();
        documents.failInsert = true;
        DocumentUploadService service = service(documents);

        assertConflict(() -> service.upload(
                1L, text("policy.txt", "content".getBytes(StandardCharsets.UTF_8)), 7L));

        assertThat(regularFiles()).isEmpty();
    }

    @Test
    void missingKnowledgeBaseIsRejectedBeforeStorage() throws IOException {
        DocumentUploadService service = new DocumentUploadService(
                new FakeKnowledgeBases(false),
                new FakeDocuments(),
                new LocalFileStorage(root),
                List.of());

        assertThatThrownBy(() -> service.upload(
                        99L, text("policy.txt", "content".getBytes(StandardCharsets.UTF_8)), 7L))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).errorCode().code())
                .isEqualTo("NOT_FOUND");
        assertThat(regularFiles()).isEmpty();
    }

    private DocumentUploadService service(FakeDocuments documents) {
        return new DocumentUploadService(
                new FakeKnowledgeBases(true), documents, new LocalFileStorage(root), List.of());
    }

    private List<Path> regularFiles() throws IOException {
        if (Files.notExists(root)) {
            return List.of();
        }
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).toList();
        }
    }

    private static MockMultipartFile text(String name, byte[] content) {
        return new MockMultipartFile("file", name, "text/plain", content);
    }

    private static void assertValidation(Runnable action) {
        assertCode(action, "VALIDATION_ERROR");
    }

    private static void assertConflict(Runnable action) {
        assertCode(action, "CONFLICT");
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).errorCode().code())
                .isEqualTo(code);
    }

    private static java.util.stream.Stream<Arguments> supportedDocuments() throws IOException {
        return java.util.stream.Stream.of(
                Arguments.of(
                        "policy.txt",
                        "text/plain",
                        "制度正文".getBytes(StandardCharsets.UTF_8),
                        "text/plain"),
                Arguments.of(
                        "README.md",
                        "text/markdown",
                        "# 制度".getBytes(StandardCharsets.UTF_8),
                        "text/markdown"),
                Arguments.of("manual.pdf", "application/pdf", pdf(), "application/pdf"),
                Arguments.of("manual.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        docx(),
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }

    private static byte[] pdf() {
        return "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF"
                .getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] docx() throws IOException {
        return docxWith(null, null);
    }

    private static byte[] malformedDocx() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                addZipEntry(zip, "[Content_Types].xml", "<Types/>".getBytes(StandardCharsets.UTF_8));
                addZipEntry(zip, "word/document.xml", "<document/>".getBytes(StandardCharsets.UTF_8));
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] docxWith(String extraName, byte[] extraContent) {
        String contentTypes = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels"
                    ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml"
                    ContentType=
                      "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>
                """;
        String relationships = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1"
                    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
                    Target="word/document.xml"/>
                </Relationships>
                """;
        String document = """
                <?xml version="1.0" encoding="UTF-8"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body><w:p><w:r><w:t>企业制度</w:t></w:r></w:p></w:body>
                </w:document>
                """;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            addZipEntry(zip, "[Content_Types].xml", contentTypes.getBytes(StandardCharsets.UTF_8));
            addZipEntry(zip, "_rels/.rels", relationships.getBytes(StandardCharsets.UTF_8));
            addZipEntry(zip, "word/document.xml", document.getBytes(StandardCharsets.UTF_8));
            if (extraName != null) {
                addZipEntry(zip, extraName, extraContent);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        return bytes.toByteArray();
    }

    private static void addZipEntry(ZipOutputStream zip, String name, byte[] content)
            throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    private static final class SizedMultipartFile extends MockMultipartFile {
        private final long size;

        SizedMultipartFile(String name, String contentType, long size) {
            super("file", name, contentType, new byte[] {1});
            this.size = size;
        }

        @Override
        public long getSize() {
            return size;
        }
    }

    private static final class FakeKnowledgeBases implements KnowledgeBaseRepository {
        private final boolean present;

        FakeKnowledgeBases(boolean present) {
            this.present = present;
        }

        @Override public List<KnowledgeBaseView> findAll() { return List.of(); }
        @Override public Optional<KnowledgeBaseView> findById(long id) {
            return present ? Optional.of(new KnowledgeBaseView(
                    id, "知识库", null, 1L, 0L, 0L, Instant.EPOCH, Instant.EPOCH)) : Optional.empty();
        }
        @Override public boolean existsByName(String name, Long excludedId) { return false; }
        @Override
        public void create(KnowledgeBase knowledgeBase) {
            throw new UnsupportedOperationException();
        }
        @Override public int update(long id, String name, String description) { return 0; }
        @Override public int delete(long id) { return 0; }
    }

    private static final class FakeDocuments implements DocumentRepository {
        private final List<KnowledgeDocument> rows = new ArrayList<>();
        private long sequence;
        private boolean failInsert;

        @Override
        public boolean existsByKnowledgeBaseAndSha256(long knowledgeBaseId, String sha256) {
            return rows.stream().anyMatch(row -> row.knowledgeBaseId() == knowledgeBaseId
                    && row.sha256().equals(sha256));
        }

        @Override
        public void create(KnowledgeDocument document) {
            if (failInsert) {
                throw new DataIntegrityViolationException("duplicate");
            }
            document.assignId(++sequence);
            rows.add(document);
        }

        @Override
        public Optional<DocumentView> findById(long id) {
            return rows.stream().filter(row -> row.id() == id).findFirst().map(KnowledgeDocument::toView);
        }

        @Override public List<DocumentView> findAllByKnowledgeBaseId(long knowledgeBaseId) {
            return rows.stream()
                    .filter(row -> row.knowledgeBaseId() == knowledgeBaseId)
                    .map(KnowledgeDocument::toView)
                    .toList();
        }
        @Override public int markParsing(long id) { return 0; }
        @Override public int markIndexing(long id) { return 0; }
        @Override public int markReady(long id, int chunkCount) { return 0; }
        @Override public int markFailed(long id, String failureReason) { return 0; }
        @Override public int delete(long id) { return 0; }
    }
}
