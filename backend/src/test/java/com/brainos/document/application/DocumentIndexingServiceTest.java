package com.brainos.document.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brainos.common.api.ApiException;
import com.brainos.document.chunking.DocumentChunk;
import com.brainos.document.chunking.DocumentChunker;
import com.brainos.document.domain.DocumentRepository;
import com.brainos.document.domain.DocumentStatus;
import com.brainos.document.domain.DocumentView;
import com.brainos.document.indexing.VectorIndexPort;
import com.brainos.document.parsing.DocumentParserPort;
import com.brainos.document.parsing.ParsedSection;
import com.brainos.document.storage.FileStoragePort;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DocumentIndexingServiceTest {

    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final DocumentParserPort parser = mock(DocumentParserPort.class);
    private final DocumentChunker chunker = mock(DocumentChunker.class);
    private final VectorIndexPort vectors = mock(VectorIndexPort.class);
    private final FileStoragePort storage = mock(FileStoragePort.class);
    private final DocumentIndexingDispatcher dispatcher = mock(DocumentIndexingDispatcher.class);
    private DocumentIndexingService service;

    @BeforeEach
    void setUp() {
        service = new DocumentIndexingService(
                documents, parser, chunker, vectors, storage, dispatcher);
        when(documents.markParsing(org.mockito.ArgumentMatchers.anyLong())).thenReturn(1);
        when(documents.markIndexing(org.mockito.ArgumentMatchers.anyLong())).thenReturn(1);
        when(documents.markReady(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(1);
        when(documents.markFailed(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(1);
        when(documents.delete(org.mockito.ArgumentMatchers.anyLong())).thenReturn(1);
    }

    @Test
    void indexesParsingDocumentAndPersistsEachVisibleState() {
        DocumentView document = document(DocumentStatus.PARSING, null);
        List<ParsedSection> sections = List.of(new ParsedSection("企业请假制度", 1));
        List<DocumentChunk> chunks = List.of(new DocumentChunk(
                "44:0", "企业请假制度", 7L, 44L, "policy.txt", 1, 0));
        when(documents.findById(44L)).thenReturn(Optional.of(document));
        when(parser.parse(Path.of("/tmp/policy.txt"), "text/plain")).thenReturn(sections);
        when(chunker.chunk(7L, 44L, "policy.txt", sections)).thenReturn(chunks);

        service.index(44L);

        verify(documents).markIndexing(44L);
        verify(vectors).replaceDocument(44L, chunks);
        verify(documents).markReady(44L, 1);
        verify(documents, never()).markFailed(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void providerFailureMarksFailedAndRemovesPartialVectors() {
        DocumentView document = document(DocumentStatus.PARSING, null);
        List<ParsedSection> sections = List.of(new ParsedSection("企业请假制度", 1));
        List<DocumentChunk> chunks = List.of(new DocumentChunk(
                "44:0", "企业请假制度", 7L, 44L, "policy.txt", 1, 0));
        when(documents.findById(44L)).thenReturn(Optional.of(document));
        when(parser.parse(Path.of("/tmp/policy.txt"), "text/plain")).thenReturn(sections);
        when(chunker.chunk(7L, 44L, "policy.txt", sections)).thenReturn(chunks);
        doThrow(new RuntimeException("provider unavailable"))
                .when(vectors).replaceDocument(44L, chunks);

        service.index(44L);

        verify(vectors).deleteDocument(44L);
        verify(documents).markFailed(44L, "文档处理失败，请重试");
        verify(documents, never()).markReady(44L, 1);
    }

    @Test
    void retryCleansOldVectorsResetsStateAndDispatches() {
        when(documents.findById(44L))
                .thenReturn(Optional.of(document(DocumentStatus.FAILED, "旧失败")));

        service.retry(7L, 44L);

        var ordered = inOrder(documents, vectors, dispatcher);
        ordered.verify(documents).findById(44L);
        ordered.verify(documents).markParsing(44L);
        ordered.verify(vectors).deleteDocument(44L);
        ordered.verify(dispatcher).submit(44L);
    }

    @Test
    void losingConcurrentRetryNeverDeletesVectors() {
        when(documents.findById(44L))
                .thenReturn(Optional.of(document(DocumentStatus.FAILED, "旧失败")));
        when(documents.markParsing(44L)).thenReturn(0);

        assertThatThrownBy(() -> service.retry(7L, 44L))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).errorCode().code())
                .isEqualTo("CONFLICT");

        verify(vectors, never()).deleteDocument(44L);
        verify(dispatcher, never()).submit(44L);
    }

    @Test
    void deleteCleansVectorsAndFileBeforeDatabaseRow() {
        when(documents.findById(44L))
                .thenReturn(Optional.of(document(DocumentStatus.READY, null)));

        service.delete(7L, 44L);

        verify(vectors).deleteDocument(44L);
        verify(storage).delete(Path.of("/tmp/policy.txt"));
        verify(documents).delete(44L);
    }

    @Test
    void knowledgeBaseCleanupUsesTheSameDocumentCleanupPath() {
        DocumentView document = document(DocumentStatus.READY, null);
        when(documents.findAllByKnowledgeBaseId(7L)).thenReturn(List.of(document));
        when(documents.findById(44L)).thenReturn(Optional.of(document));

        service.cleanup(7L);

        verify(vectors).deleteDocument(44L);
        verify(storage).delete(Path.of("/tmp/policy.txt"));
        verify(documents).delete(44L);
    }

    @Test
    void documentCannotBeAccessedThroughAnotherKnowledgeBase() {
        when(documents.findById(44L))
                .thenReturn(Optional.of(document(DocumentStatus.READY, null)));

        assertThatThrownBy(() -> service.get(8L, 44L))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).errorCode().code())
                .isEqualTo("NOT_FOUND");
    }

    private static DocumentView document(DocumentStatus status, String failureReason) {
        return new DocumentView(
                44L,
                7L,
                "policy.txt",
                "/tmp/policy.txt",
                "text/plain",
                12L,
                "a".repeat(64),
                status,
                0,
                failureReason,
                2L,
                Instant.parse("2026-07-14T00:00:00Z"),
                Instant.parse("2026-07-14T00:00:00Z"));
    }
}
