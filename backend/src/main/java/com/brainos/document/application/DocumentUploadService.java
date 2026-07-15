package com.brainos.document.application;

import com.brainos.admin.audit.AuditEvent;
import com.brainos.admin.audit.AuditRecorder;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.tika.Tika;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class DocumentUploadService {

    public static final long MAX_BYTES = 20L * 1024L * 1024L;
    private static final Set<String> EXTENSIONS = Set.of("pdf", "docx", "txt", "md", "markdown");
    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String CONTENT_TYPES_NAMESPACE =
            "http://schemas.openxmlformats.org/package/2006/content-types";
    private static final String RELATIONSHIPS_NAMESPACE =
            "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String WORD_NAMESPACE =
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String MAIN_DOCUMENT_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
    private static final long MAX_DOCX_UNCOMPRESSED_BYTES = 100L * 1024L * 1024L;
    private static final long MAX_DOCX_ENTRY_BYTES = 25L * 1024L * 1024L;
    private static final double MAX_DOCX_COMPRESSION_RATIO = 100.0;
    private static final int MAX_DOCX_ENTRIES = 2048;

    private final KnowledgeBaseRepository knowledgeBases;
    private final DocumentRepository documents;
    private final FileStoragePort storage;
    private final List<DocumentIndexingDispatcher> dispatchers;
    private final AuditRecorder audit;
    private final Tika tika = new Tika();

    @Autowired
    public DocumentUploadService(
            KnowledgeBaseRepository knowledgeBases,
            DocumentRepository documents,
            FileStoragePort storage,
            List<DocumentIndexingDispatcher> dispatchers,
            AuditRecorder audit) {
        this.knowledgeBases = knowledgeBases;
        this.documents = documents;
        this.storage = storage;
        this.dispatchers = List.copyOf(dispatchers);
        this.audit = audit;
    }

    public DocumentUploadService(
            KnowledgeBaseRepository knowledgeBases,
            DocumentRepository documents,
            FileStoragePort storage,
            List<DocumentIndexingDispatcher> dispatchers) {
        this(knowledgeBases, documents, storage, dispatchers, event -> {});
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
            audit.record(AuditEvent.documentUploaded(userId, document.id()));
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
                || originalName.chars().anyMatch(Character::isISOControl)) {
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
            return switch (extension) {
                case "pdf" -> {
                    String detected = tika.detect(stored.path());
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

    private void validateDocx(StoredFile stored) throws IOException {
        try (ZipFile zip = new ZipFile(stored.path().toFile())) {
            validateZipSafety(zip);
            ZipEntry contentTypes = zip.getEntry("[Content_Types].xml");
            ZipEntry relationships = zip.getEntry("_rels/.rels");
            ZipEntry document = zip.getEntry("word/document.xml");
            if (contentTypes == null || relationships == null || document == null) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR);
            }
            if (!DOCX_MIME.equals(tika.detect(stored.path()))) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR);
            }
            validateContentTypes(parseXml(zip, contentTypes));
            validateRelationships(parseXml(zip, relationships));
            Element documentRoot = parseXml(zip, document).getDocumentElement();
            if (!WORD_NAMESPACE.equals(documentRoot.getNamespaceURI())
                    || !"document".equals(documentRoot.getLocalName())) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR);
            }
        }
    }

    private static void validateZipSafety(ZipFile zip) throws IOException {
        int entryCount = 0;
        long totalUncompressed = 0;
        Set<String> names = new HashSet<>();
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            entryCount++;
            if (entryCount > MAX_DOCX_ENTRIES || !names.add(entry.getName())) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR);
            }
            validateZipEntryName(entry.getName());
            if (entry.isDirectory()) {
                continue;
            }
            long declaredSize = entry.getSize();
            long compressedSize = entry.getCompressedSize();
            if (declaredSize > MAX_DOCX_ENTRY_BYTES
                    || (declaredSize > 0
                            && compressedSize > 0
                            && (double) declaredSize / compressedSize > MAX_DOCX_COMPRESSION_RATIO)) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR);
            }
            long actualSize = 0;
            try (var input = zip.getInputStream(entry)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    actualSize += read;
                    totalUncompressed += read;
                    if (actualSize > MAX_DOCX_ENTRY_BYTES
                            || totalUncompressed > MAX_DOCX_UNCOMPRESSED_BYTES) {
                        throw new ApiException(ErrorCode.VALIDATION_ERROR);
                    }
                }
            }
        }
    }

    private static void validateZipEntryName(String name) {
        if (name.isBlank()
                || name.startsWith("/")
                || name.startsWith("\\")
                || name.contains("\\")
                || name.matches("^[A-Za-z]:.*")) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
        for (String segment : name.split("/")) {
            if (segment.equals("..")) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR);
            }
        }
    }

    private static org.w3c.dom.Document parseXml(ZipFile zip, ZipEntry entry) throws IOException {
        try (var input = zip.getInputStream(entry)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(input);
        } catch (ParserConfigurationException | SAXException exception) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private static void validateContentTypes(org.w3c.dom.Document xml) {
        Element root = xml.getDocumentElement();
        if (!CONTENT_TYPES_NAMESPACE.equals(root.getNamespaceURI())
                || !"Types".equals(root.getLocalName())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
        NodeList overrides = root.getElementsByTagNameNS(CONTENT_TYPES_NAMESPACE, "Override");
        for (int index = 0; index < overrides.getLength(); index++) {
            Element override = (Element) overrides.item(index);
            if ("/word/document.xml".equals(override.getAttribute("PartName"))
                    && MAIN_DOCUMENT_CONTENT_TYPE.equals(override.getAttribute("ContentType"))) {
                return;
            }
        }
        throw new ApiException(ErrorCode.VALIDATION_ERROR);
    }

    private static void validateRelationships(org.w3c.dom.Document xml) {
        Element root = xml.getDocumentElement();
        if (!RELATIONSHIPS_NAMESPACE.equals(root.getNamespaceURI())
                || !"Relationships".equals(root.getLocalName())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
        NodeList relationships =
                root.getElementsByTagNameNS(RELATIONSHIPS_NAMESPACE, "Relationship");
        for (int index = 0; index < relationships.getLength(); index++) {
            Element relationship = (Element) relationships.item(index);
            String type = relationship.getAttribute("Type");
            String target = relationship.getAttribute("Target");
            String targetMode = relationship.getAttribute("TargetMode");
            if (type.endsWith("/officeDocument")
                    && "word/document.xml".equals(target)
                    && !"External".equalsIgnoreCase(targetMode)) {
                return;
            }
        }
        throw new ApiException(ErrorCode.VALIDATION_ERROR);
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
