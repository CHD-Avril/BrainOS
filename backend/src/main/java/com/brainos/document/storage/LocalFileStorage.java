package com.brainos.document.storage;

import com.brainos.common.api.ApiException;
import com.brainos.common.api.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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
            Path rootReal = ensureRoot();
            temporary = Files.createTempFile(rootReal, ".upload-", ".tmp");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = copyWithLimit(content, temporary, digest, maxBytes);
            Path directory = rootReal.resolve(Long.toString(knowledgeBaseId));
            Files.createDirectories(directory);
            Path directoryReal = directory.toRealPath();
            requireInsideRoot(directoryReal, rootReal);
            Path destination = directoryReal.resolve(UUID.randomUUID() + "." + extension);
            moveAtomically(temporary, destination);
            return new StoredFile(destination, size, HexFormat.of().formatHex(digest.digest()));
        } catch (ApiException exception) {
            deleteQuietly(temporary);
            throw exception;
        } catch (RuntimeException exception) {
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
        try {
            Path rootReal = ensureRoot();
            Path normalized = path.toAbsolutePath().normalize();
            if (!normalized.startsWith(root) && !normalized.startsWith(rootReal)) {
                throw new IllegalArgumentException("Path is outside storage root");
            }
            if (!Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            if (Files.isSymbolicLink(normalized)) {
                throw new IllegalArgumentException("Symbolic links cannot be deleted as documents");
            }
            Path parentReal = normalized.getParent().toRealPath();
            requireInsideRoot(parentReal, rootReal);
            Path target = parentReal.resolve(normalized.getFileName());
            if (target.equals(rootReal)) {
                throw new IllegalArgumentException("Cannot delete storage root");
            }
            Files.deleteIfExists(target);
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

    private Path ensureRoot() throws IOException {
        Files.createDirectories(root);
        return root.toRealPath();
    }

    private static void requireInsideRoot(Path path, Path rootReal) {
        if (!path.startsWith(rootReal)) {
            throw new IllegalArgumentException("Path is outside storage root");
        }
    }

    private static void moveAtomically(Path source, Path destination) throws IOException {
        Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
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
