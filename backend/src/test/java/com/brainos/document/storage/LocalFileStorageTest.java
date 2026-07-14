package com.brainos.document.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brainos.common.api.ApiException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageTest {

    @TempDir Path root;

    @Test
    void streamsToKnowledgeDirectoryWithDigestAndDeletes() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(root);
        byte[] content = "enterprise knowledge".getBytes(StandardCharsets.UTF_8);

        StoredFile stored = storage.store(9L, new ByteArrayInputStream(content), "txt", 1024L);

        assertThat(stored.path()).isRegularFile().startsWith(root.resolve("9"));
        assertThat(stored.path().getFileName().toString()).endsWith(".txt");
        assertThat(stored.sizeBytes()).isEqualTo(content.length);
        assertThat(stored.sha256()).hasSize(64);
        assertThat(Files.readAllBytes(stored.path())).isEqualTo(content);

        storage.delete(stored.path());
        assertThat(stored.path()).doesNotExist();
    }

    @Test
    void actualStreamLimitRemovesTemporaryFile() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(root);

        assertThatThrownBy(() -> storage.store(
                        1L, new ByteArrayInputStream(new byte[11]), "txt", 10L))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).errorCode().code())
                .isEqualTo("VALIDATION_ERROR");

        try (var paths = Files.walk(root)) {
            assertThat(paths.filter(Files::isRegularFile)).isEmpty();
        }
    }

    @Test
    void deleteRejectsPathsOutsideStorageRoot() {
        LocalFileStorage storage = new LocalFileStorage(root);

        assertThatThrownBy(() -> storage.delete(root.resolve("../outside.txt")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
