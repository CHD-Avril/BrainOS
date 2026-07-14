package com.brainos.document.application;

import com.brainos.common.api.ApiException;
import com.brainos.common.api.ErrorCode;
import com.brainos.document.domain.DocumentRepository;
import com.brainos.document.domain.DocumentStatus;
import com.brainos.document.domain.DocumentView;
import com.brainos.document.domain.KnowledgeDocument;
import com.brainos.document.storage.FileStoragePort;
import com.brainos.document.storage.StoredFile;
import com.brainos.knowledge.domain.KnowledgeBaseRepository;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipFile;
import org.apache.tika.Tika;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class DocumentUploadService {

    public static final long MAX_BYTES = 20L * 1024L * 1024L;
    private static final Set<String> EXTENSIONS = Set.of("pdf", "docx", "txt", "md", "markdown");
    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final KnowledgeBaseRepository knowledgeBases;
    private final DocumentRepository documents;
    private final FileStoragePort storage;
    private final List<DocumentIndexingDispatcher> dispatchers;
    private final Tika tika = new Tika();

    public DocumentUploadService(
            KnowledgeBaseRepository knowledgeBases,
            DocumentRepository documents,
            FileStoragePort storage,
            List<DocumentIndexingDispatcher> dispatchers) {
        this.knowledgeBases = knowledgeBases;
        this.documents = documents;
        this.storage = storage;
        this.dispatchers = List.copyOf(dispatchers);
    }

    @Transactional
    public DocumentView upload(long knowledgeBaseId, MultipartFile upload, long userId) {
        if (knowledgeBases.findById(knowledgeBaseId).isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        ValidatedName name = validateRequest(upload);
        StoredFile stored;
        try (var content = upload.getInputStream()) {
            stored = storage.store(knowledgeBaseId, content, name.extension(), MAX_BYTES);
        } catch (IOException exception) {
            throw new IllegalStateException("文档读取失败", exception);
        }

        try {
            String canonicalMime = validateContent(stored, name.extension());
            if (documents.existsByKnowledgeBaseAndSha256(knowledgeBaseId, stored.sha256())) {
                throw new ApiException(ErrorCode.CONFLICT);
            }
            KnowledgeDocument document = new KnowledgeDocument(
                    null,
                    knowledgeBaseId,
                    name.originalName(),
                    stored.path().toString(),
                    canonicalMime,
                    stored.sizeBytes(),
                    stored.sha256(),
                    DocumentStatus.PARSING,
                    userId);
            try {
                documents.create(document);
            } catch (DataIntegrityViolationException exception) {
                throw new ApiException(ErrorCode.CONFLICT);
            }
            dispatchers.forEach(dispatcher -> dispatcher.submit(document.id()));
            return documents.findById(document.id()).orElseGet(document::toView);
        } catch (RuntimeException exception) {
            storage.delete(stored.path());
            throw exception;
        }
    }

    private static ValidatedName validateRequest(MultipartFile upload) {
        if (upload == null || upload.isEmpty() || upload.getSize() <= 0 || upload.getSize() > MAX_BYTES) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
        String rawName = upload.getOriginalFilename();
        if (rawName == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
        String originalName = Normalizer.normalize(rawName.trim(), Normalizer.Form.NFKC);
        if (originalName.isBlank()
                || originalName.length() > 255
                || originalName.contains("/")
                || originalName.contains("\\")
                || originalName.contains("..")
                || originalName.chars().anyMatch(character -> character == 0 || character < 32)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
        int dot = originalName.lastIndexOf('.');
        if (dot <= 0 || dot == originalName.length() - 1) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
        String extension = originalName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(extension) || !declaredMimeMatches(extension, upload.getContentType())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
        return new ValidatedName(originalName, extension);
    }

    private String validateContent(StoredFile stored, String extension) {
        try {
            String detected = tika.detect(stored.path());
            return switch (extension) {
                case "pdf" -> {
                    byte[] header = new byte[5];
                    try (var input = Files.newInputStream(stored.path())) {
                        if (input.read(header) != header.length
                                || !"%PDF-".equals(new String(header, StandardCharsets.US_ASCII))
                                || !"application/pdf".equals(detected)) {
                            throw new ApiException(ErrorCode.VALIDATION_ERROR);
                        }
                    }
                    yield "application/pdf";
                }
                case "docx" -> {
                    validateDocx(stored);
                    yield DOCX_MIME;
                }
                case "txt" -> {
                    validateUtf8Text(stored);
                    yield "text/plain";
                }
                case "md", "markdown" -> {
                    validateUtf8Text(stored);
                    yield "text/markdown";
                }
                default -> throw new ApiException(ErrorCode.VALIDATION_ERROR);
            };
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private static void validateDocx(StoredFile stored) throws IOException {
        try (ZipFile zip = new ZipFile(stored.path().toFile())) {
            if (zip.getEntry("[Content_Types].xml") == null
                    || zip.getEntry("word/document.xml") == null) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR);
            }
        }
    }

    private static void validateUtf8Text(StoredFile stored) throws IOException {
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try (var reader = new InputStreamReader(Files.newInputStream(stored.path()), decoder)) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                for (int index = 0; index < read; index++) {
                    char value = buffer[index];
                    if (value == 0 || (Character.isISOControl(value) && !Character.isWhitespace(value))) {
                        throw new ApiException(ErrorCode.VALIDATION_ERROR);
                    }
                }
            }
        }
    }

    private static boolean declaredMimeMatches(String extension, String declaredMime) {
        if (declaredMime == null || declaredMime.isBlank()
                || "application/octet-stream".equalsIgnoreCase(declaredMime)) {
            return true;
        }
        String mime = declaredMime.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "pdf" -> "application/pdf".equals(mime);
            case "docx" -> DOCX_MIME.equals(mime);
            case "txt" -> "text/plain".equals(mime);
            case "md", "markdown" -> "text/markdown".equals(mime) || "text/plain".equals(mime);
            default -> false;
        };
    }

    private record ValidatedName(String originalName, String extension) {}
}
