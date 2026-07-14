package com.brainos.document.application;

import com.brainos.ai.embedding.EmbeddingNotConfiguredException;
import com.brainos.common.api.ApiException;
import com.brainos.common.api.ErrorCode;
import com.brainos.document.chunking.DocumentChunk;
import com.brainos.document.chunking.DocumentChunker;
import com.brainos.document.domain.DocumentRepository;
import com.brainos.document.domain.DocumentStatus;
import com.brainos.document.domain.DocumentView;
import com.brainos.document.indexing.VectorIndexPort;
import com.brainos.document.parsing.DocumentParserPort;
import com.brainos.document.parsing.NoUsableTextException;
import com.brainos.document.storage.FileStoragePort;
import com.brainos.knowledge.application.KnowledgeBaseCleanupPort;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class DocumentIndexingService implements KnowledgeBaseCleanupPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentIndexingService.class);

    private final DocumentRepository documents;
    private final DocumentParserPort parser;
    private final DocumentChunker chunker;
    private final VectorIndexPort vectors;
    private final FileStoragePort storage;
    private final DocumentIndexingDispatcher dispatcher;

    public DocumentIndexingService(
            DocumentRepository documents,
            DocumentParserPort parser,
            DocumentChunker chunker,
            VectorIndexPort vectors,
            FileStoragePort storage,
            DocumentIndexingDispatcher dispatcher) {
        this.documents = documents;
        this.parser = parser;
        this.chunker = chunker;
        this.vectors = vectors;
        this.storage = storage;
        this.dispatcher = dispatcher;
    }

    public void index(long documentId) {
        DocumentView document = required(documentId);
        if (document.status() != DocumentStatus.PARSING) {
            return;
        }
        try {
            var sections = parser.parse(Path.of(document.storagePath()), document.mimeType());
            if (documents.markIndexing(documentId) != 1) {
                return;
            }
            List<DocumentChunk> chunks = chunker.chunk(
                    document.knowledgeBaseId(), document.id(), document.originalName(), sections);
            vectors.replaceDocument(documentId, chunks);
            requireUpdated(documents.markReady(documentId, chunks.size()));
        } catch (RuntimeException exception) {
            cleanupVectors(documentId);
            documents.markFailed(documentId, failureReason(exception));
        }
    }

    public List<DocumentView> list(long knowledgeBaseId) {
        requirePositive(knowledgeBaseId);
        return documents.findAllByKnowledgeBaseId(knowledgeBaseId);
    }

    public DocumentView get(long knowledgeBaseId, long documentId) {
        DocumentView document = required(documentId);
        if (document.knowledgeBaseId() != knowledgeBaseId) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        return document;
    }

    public DocumentView retry(long knowledgeBaseId, long documentId) {
        DocumentView document = get(knowledgeBaseId, documentId);
        if (document.status() != DocumentStatus.FAILED) {
            throw new ApiException(ErrorCode.CONFLICT);
        }
        requireUpdated(documents.markParsing(documentId));
        try {
            vectors.deleteDocument(documentId);
            dispatcher.submit(documentId);
        } catch (RuntimeException exception) {
            markDispatchFailed(documentId);
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
        return required(documentId);
    }

    void markDispatchFailed(long documentId) {
        documents.markFailed(documentId, "索引任务繁忙，请稍后重试");
    }

    public void delete(long knowledgeBaseId, long documentId) {
        DocumentView document = get(knowledgeBaseId, documentId);
        vectors.deleteDocument(documentId);
        storage.delete(Path.of(document.storagePath()));
        requireUpdated(documents.delete(documentId));
    }

    @Override
    public void cleanup(long knowledgeBaseId) {
        for (DocumentView document : list(knowledgeBaseId)) {
            delete(knowledgeBaseId, document.id());
        }
    }

    private DocumentView required(long documentId) {
        requirePositive(documentId);
        return documents.findById(documentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private void cleanupVectors(long documentId) {
        try {
            vectors.deleteDocument(documentId);
        } catch (RuntimeException exception) {
            LOGGER.warn("Chroma 文档清理失败，重试时将再次清理，documentId={}", documentId);
        }
    }

    private static String failureReason(RuntimeException exception) {
        if (exception instanceof NoUsableTextException) {
            return "未提取到可用文本";
        }
        if (exception instanceof EmbeddingNotConfiguredException) {
            return "向量模型未配置";
        }
        return "文档处理失败，请重试";
    }

    private static void requireUpdated(int rows) {
        if (rows != 1) {
            throw new ApiException(ErrorCode.CONFLICT);
        }
    }

    private static void requirePositive(long value) {
        if (value <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
