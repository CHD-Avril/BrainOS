package com.brainos.document.storage;

import java.io.InputStream;
import java.nio.file.Path;

public interface FileStoragePort {

    StoredFile store(long knowledgeBaseId, InputStream content, String extension, long maxBytes);

    void delete(Path path);
}
