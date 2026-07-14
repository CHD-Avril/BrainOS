package com.brainos.document.domain;

public final class KnowledgeDocument {

    private Long id;
    private final long knowledgeBaseId;
    private final String originalName;
    private final String storagePath;
    private final String mimeType;
    private final long sizeBytes;
    private final String sha256;
    private final DocumentStatus status;
    private final long uploadedBy;

    public KnowledgeDocument(
            Long id,
            long knowledgeBaseId,
            String originalName,
            String storagePath,
            String mimeType,
            long sizeBytes,
            String sha256,
            DocumentStatus status,
            long uploadedBy) {
        this.id = id;
        this.knowledgeBaseId = knowledgeBaseId;
        this.originalName = originalName;
        this.storagePath = storagePath;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.status = status;
        this.uploadedBy = uploadedBy;
    }

    public void assignId(long id) {
        if (this.id != null) {
            throw new IllegalStateException("Document id is already assigned");
        }
        this.id = id;
    }

    public DocumentView toView() {
        return new DocumentView(
                id, knowledgeBaseId, originalName, storagePath, mimeType, sizeBytes, sha256,
                status, 0, null, uploadedBy, null, null);
    }

    public Long id() { return id; }
    public long knowledgeBaseId() { return knowledgeBaseId; }
    public String originalName() { return originalName; }
    public String storagePath() { return storagePath; }
    public String mimeType() { return mimeType; }
    public long sizeBytes() { return sizeBytes; }
    public String sha256() { return sha256; }
    public DocumentStatus status() { return status; }
    public long uploadedBy() { return uploadedBy; }

    public Long getId() { return id; }
    public long getKnowledgeBaseId() { return knowledgeBaseId; }
    public String getOriginalName() { return originalName; }
    public String getStoragePath() { return storagePath; }
    public String getMimeType() { return mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public DocumentStatus getStatus() { return status; }
    public long getUploadedBy() { return uploadedBy; }
}
