package com.brainos.document.storage;

import com.brainos.common.api.ApiException;
import com.brainos.common.api.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class LocalFileStorage implements FileStoragePort {

    private final Path root;

    @Autowired
    public LocalFileStorage(@Value("${brainos.storage.root}") String root) {
        this(Path.of(root));
    }

    public LocalFileStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public StoredFile store(
            long knowledgeBaseId, InputStream content, String extension, long maxBytes) {
        Path temporary = null;
        try {
            Files.createDirectories(root);
            temporary = Files.createTempFile(root, ".upload-", ".tmp");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = copyWithLimit(content, temporary, digest, maxBytes);
            Path directory = root.resolve(Long.toString(knowledgeBaseId)).normalize();
            requireInsideRoot(directory);
            Files.createDirectories(directory);
            Path destination = directory.resolve(UUID.randomUUID() + "." + extension).normalize();
            requireInsideRoot(destination);
            moveAtomically(temporary, destination);
            return new StoredFile(destination, size, HexFormat.of().formatHex(digest.digest()));
        } catch (ApiException exception) {
            deleteQuietly(temporary);
            throw exception;
        } catch (IOException | NoSuchAlgorithmException exception) {
            deleteQuietly(temporary);
            throw new IllegalStateException("文档存储失败", exception);
        }
    }

    @Override
    public void delete(Path path) {
        if (path == null) {
            return;
        }
        Path normalized = path.toAbsolutePath().normalize();
        requireInsideRoot(normalized);
        if (normalized.equals(root)) {
            throw new IllegalArgumentException("Cannot delete storage root");
        }
        try {
            Files.deleteIfExists(normalized);
        } catch (IOException exception) {
            throw new IllegalStateException("文档删除失败", exception);
        }
    }

    private static long copyWithLimit(
            InputStream content, Path destination, MessageDigest digest, long maxBytes)
            throws IOException {
        long size = 0;
        byte[] buffer = new byte[8192];
        try (var output = Files.newOutputStream(destination)) {
            int read;
            while ((read = content.read(buffer)) != -1) {
                size += read;
                if (size > maxBytes) {
                    throw new ApiException(ErrorCode.VALIDATION_ERROR);
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        if (size == 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
        return size;
    }

    private void requireInsideRoot(Path path) {
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Path is outside storage root");
        }
    }

    private static void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination);
        }
    }

    private static void deleteQuietly(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // Preserve the original storage error.
            }
        }
    }
}
