package com.brainos.document.storage;

import java.nio.file.Path;

public record StoredFile(Path path, long sizeBytes, String sha256) {}
