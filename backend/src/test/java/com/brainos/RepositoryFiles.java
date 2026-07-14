package com.brainos;

import java.nio.file.Files;
import java.nio.file.Path;

public final class RepositoryFiles {

    private static final int MAX_PARENT_LEVELS = 8;

    private RepositoryFiles() {}

    public static Path find(String relativePath) {
        Path directory = Path.of("").toAbsolutePath().normalize();
        for (int level = 0; level <= MAX_PARENT_LEVELS && directory != null; level++) {
            Path candidate = directory.resolve(relativePath).normalize();
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Repository file not found: " + relativePath);
    }
}
