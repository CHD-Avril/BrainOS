package com.brainos.document.indexing;

import com.brainos.ai.embedding.EmbeddingPort;
import com.brainos.document.chunking.DocumentChunk;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaFilterExpressionConverter;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.web.client.ResourceAccessException;

public final class SpringAiVectorIndex implements VectorIndexPort {

    public static final String COLLECTION_NAME = "brainos_documents";
    private static final String LOCAL_TENANT = "default_tenant";
    private static final String LOCAL_DATABASE = "default_database";
    private static final String DISTANCE_METADATA_KEY = "hnsw:space";
    private static final String DISTANCE_FUNCTION = "cosine";
    private static final int MAX_TOP_K = 100;
    private static final int DELETE_MAX_ATTEMPTS = 3;
    private static final long DELETE_RETRY_DELAY_MILLIS = 200L;

    private final ChromaApi chromaApi;
    private final String tenant;
    private final String database;
    private final EmbeddingPort embeddings;
    private volatile String collectionId;

    public SpringAiVectorIndex(String baseUrl, EmbeddingPort embeddings) {
        this(baseUrl, "", LOCAL_TENANT, LOCAL_DATABASE, embeddings);
    }

    public SpringAiVectorIndex(
            String baseUrl,
            String apiKey,
            String tenant,
            String database,
            EmbeddingPort embeddings) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Chroma base URL must not be blank");
        }
        if (tenant == null || tenant.isBlank()) {
            throw new IllegalArgumentException("Chroma tenant must not be blank");
        }
        if (database == null || database.isBlank()) {
            throw new IllegalArgumentException("Chroma database must not be blank");
        }
        ChromaApi api = ChromaApi.builder().baseUrl(stripTrailingSlash(baseUrl)).build();
        if (apiKey != null && !apiKey.isBlank()) {
            api = api.withKeyToken(apiKey);
        }
        this.chromaApi = api;
        this.tenant = tenant;
        this.database = database;
        this.embeddings = Objects.requireNonNull(embeddings, "embeddings must not be null");
    }

    @Override
    public void replaceDocument(long documentId, List<DocumentChunk> chunks) {
        requirePositive(documentId, "documentId");
        List<DocumentChunk> safeChunks = validateChunks(documentId, chunks);
        deleteDocument(documentId);
        if (safeChunks.isEmpty()) {
            return;
        }

        List<float[]> vectors = embeddings.embedAll(
                safeChunks.stream().map(DocumentChunk::text).toList());
        validateVectors(vectors, safeChunks.size());
        chromaApi.upsertEmbeddings(
                tenant,
                database,
                collectionId(),
                new ChromaApi.AddEmbeddingsRequest(
                        safeChunks.stream().map(DocumentChunk::id).toList(),
                        vectors,
                        safeChunks.stream().map(DocumentChunk::metadata).toList(),
                        safeChunks.stream().map(DocumentChunk::text).toList()));
    }

    @Override
    public List<RetrievedChunk> search(
            long knowledgeBaseId, float[] query, int topK, double threshold) {
        requirePositive(knowledgeBaseId, "knowledgeBaseId");
        validateQuery(query, topK, threshold);
        Map<String, Object> where = whereEquals("knowledgeBaseId", knowledgeBaseId);
        ChromaApi.QueryResponse response = chromaApi.queryCollection(
                tenant,
                database,
                collectionId(),
                new ChromaApi.QueryRequest(query.clone(), topK, where));
        return toResults(response, threshold);
    }

    @Override
    public void deleteDocument(long documentId) {
        requirePositive(documentId, "documentId");
        ChromaApi.DeleteEmbeddingsRequest request = new ChromaApi.DeleteEmbeddingsRequest(
                null, whereEquals("documentId", documentId));
        for (int attempt = 1; attempt <= DELETE_MAX_ATTEMPTS; attempt++) {
            try {
                chromaApi.deleteEmbeddings(tenant, database, collectionId(), request);
                return;
            } catch (ResourceAccessException exception) {
                if (attempt == DELETE_MAX_ATTEMPTS) {
                    throw exception;
                }
                pauseBeforeDeleteRetry(attempt);
            }
        }
    }

    private static void pauseBeforeDeleteRetry(int attempt) {
        try {
            Thread.sleep(DELETE_RETRY_DELAY_MILLIS * attempt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Chroma delete retry interrupted", exception);
        }
    }

    private List<RetrievedChunk> toResults(ChromaApi.QueryResponse response, double threshold) {
        if (response == null || response.ids() == null || response.ids().isEmpty()) {
            return List.of();
        }
        List<String> ids = first(response.ids());
        List<String> texts = first(response.documents());
        List<Map<String, Object>> metadata = first(response.metadata());
        List<Double> distances = first(response.distances());
        List<RetrievedChunk> results = new ArrayList<>(ids.size());
        for (int index = 0; index < ids.size(); index++) {
            double score = 1d - distances.get(index);
            if (score < threshold) {
                continue;
            }
            Map<String, Object> values = metadata.get(index);
            int page = number(values, "pageNumber").intValue();
            results.add(new RetrievedChunk(
                    ids.get(index),
                    texts.get(index),
                    number(values, "knowledgeBaseId").longValue(),
                    number(values, "documentId").longValue(),
                    string(values, "fileName"),
                    page < 0 ? null : page,
                    number(values, "chunkIndex").intValue(),
                    score));
        }
        return List.copyOf(results);
    }

    private Map<String, Object> whereEquals(String key, long value) {
        Filter.Expression expression = new FilterExpressionBuilder().eq(key, value).build();
        String json = new ChromaFilterExpressionConverter().convertExpression(expression);
        return chromaApi.where(json);
    }

    private String collectionId() {
        String current = collectionId;
        if (current == null) {
            synchronized (this) {
                current = collectionId;
                if (current == null) {
                    ChromaApi.Collection collection = chromaApi.getCollection(
                            tenant, database, COLLECTION_NAME);
                    if (collection == null) {
                        collection = chromaApi.createCollection(
                                tenant,
                                database,
                                new ChromaApi.CreateCollectionRequest(
                                        COLLECTION_NAME,
                                        Map.of(DISTANCE_METADATA_KEY, DISTANCE_FUNCTION)));
                    }
                    if (collection == null || collection.id() == null) {
                        throw new IllegalStateException("Chroma collection initialization failed");
                    }
                    if (collection.metadata() == null
                            || !DISTANCE_FUNCTION.equals(collection.metadata().get(DISTANCE_METADATA_KEY))) {
                        throw new IllegalStateException("Chroma collection distance function must be cosine");
                    }
                    current = collection.id();
                    collectionId = current;
                }
            }
        }
        return current;
    }

    private static List<DocumentChunk> validateChunks(long documentId, List<DocumentChunk> chunks) {
        if (chunks == null) {
            throw new IllegalArgumentException("chunks must not be null");
        }
        Set<String> ids = new HashSet<>();
        for (DocumentChunk chunk : chunks) {
            if (chunk == null
                    || chunk.documentId() == null
                    || chunk.documentId() != documentId
                    || chunk.knowledgeBaseId() == null
                    || chunk.knowledgeBaseId() <= 0
                    || chunk.chunkIndex() < 0
                    || !Objects.equals(chunk.id(), documentId + ":" + chunk.chunkIndex())
                    || chunk.text() == null
                    || chunk.text().isBlank()
                    || chunk.fileName() == null
                    || chunk.fileName().isBlank()
                    || !ids.add(chunk.id())) {
                throw new IllegalArgumentException("invalid document chunk");
            }
        }
        return List.copyOf(chunks);
    }

    private static void validateVectors(List<float[]> vectors, int expectedSize) {
        if (vectors == null || vectors.size() != expectedSize || vectors.isEmpty()) {
            throw new IllegalStateException("embedding result count mismatch");
        }
        int dimensions = vectors.getFirst() == null ? 0 : vectors.getFirst().length;
        if (dimensions == 0) {
            throw new IllegalStateException("embedding result is empty");
        }
        for (float[] vector : vectors) {
            if (vector == null || vector.length != dimensions) {
                throw new IllegalStateException("embedding dimensions mismatch");
            }
            for (float coordinate : vector) {
                if (!Float.isFinite(coordinate)) {
                    throw new IllegalStateException("embedding contains a non-finite value");
                }
            }
        }
    }

    private static void validateQuery(float[] query, int topK, double threshold) {
        if (query == null || query.length == 0) {
            throw new IllegalArgumentException("query vector must not be empty");
        }
        for (float coordinate : query) {
            if (!Float.isFinite(coordinate)) {
                throw new IllegalArgumentException("query vector must be finite");
            }
        }
        if (topK < 1 || topK > MAX_TOP_K) {
            throw new IllegalArgumentException("topK must be between 1 and " + MAX_TOP_K);
        }
        if (!Double.isFinite(threshold) || threshold < 0d || threshold > 1d) {
            throw new IllegalArgumentException("threshold must be between 0 and 1");
        }
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static Number number(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("Chroma metadata is missing " + key);
        }
        return number;
    }

    private static String string(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (!(value instanceof String text)) {
            throw new IllegalStateException("Chroma metadata is missing " + key);
        }
        return text;
    }

    private static <T> List<T> first(List<List<T>> values) {
        return values == null || values.isEmpty() || values.getFirst() == null
                ? List.of()
                : values.getFirst();
    }

    private static String stripTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
