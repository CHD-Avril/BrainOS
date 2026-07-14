package com.brainos.document.parsing;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

@Component
public final class TikaDocumentParser implements DocumentParserPort {

    static final String DOCX_MIME =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String PDF_MIME = "application/pdf";
    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of(
        PDF_MIME,
        DOCX_MIME,
        "text/plain",
        "text/markdown"
    );
    private static final int MAX_EXTRACTED_CHARACTERS = 10 * 1024 * 1024;

    @Override
    public List<ParsedSection> parse(Path path, String mimeType) {
        if (!SUPPORTED_MIME_TYPES.contains(mimeType)) {
            throw new UnsupportedDocumentTypeException(mimeType);
        }
        if (mimeType.startsWith("text/") && !textSourceContainsUsableCharacters(path)) {
            throw new NoUsableTextException();
        }

        List<ParsedSection> sections = PDF_MIME.equals(mimeType)
            ? parsePdf(path)
            : List.of(new ParsedSection(parseWithTika(path, mimeType), null));
        List<ParsedSection> usable = sections.stream()
            .map(section -> new ParsedSection(normalize(section.text()), section.pageNumber()))
            .filter(section -> !section.text().isBlank())
            .toList();
        if (usable.isEmpty()) {
            throw new NoUsableTextException();
        }
        return usable;
    }

    private boolean textSourceContainsUsableCharacters(Path path) {
        try {
            return !normalize(Files.readString(path)).isBlank();
        } catch (IOException exception) {
            throw new DocumentParsingException("文档解析失败", exception);
        }
    }

    private List<ParsedSection> parsePdf(Path path) {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<ParsedSection> sections = new ArrayList<>();
            int extractedCharacters = 0;
            for (int pageNumber = 1; pageNumber <= document.getNumberOfPages(); pageNumber++) {
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                String text = stripper.getText(document);
                extractedCharacters += text.length();
                if (extractedCharacters > MAX_EXTRACTED_CHARACTERS) {
                    throw new DocumentParsingException("文档解析结果过大");
                }
                sections.add(new ParsedSection(text, pageNumber));
            }
            return sections;
        } catch (IOException exception) {
            throw new DocumentParsingException("文档解析失败", exception);
        }
    }

    private String parseWithTika(Path path, String mimeType) {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(MAX_EXTRACTED_CHARACTERS);
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, mimeType);
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "document");
        ParseContext context = new ParseContext();
        context.set(EmbeddedDocumentExtractor.class, new RejectEmbeddedDocuments());

        try (InputStream input = Files.newInputStream(path)) {
            parser.parse(input, handler, metadata, context);
            return handler.toString();
        } catch (IOException | SAXException | TikaException exception) {
            throw new DocumentParsingException("文档解析失败", exception);
        }
    }

    static String normalize(String value) {
        StringBuilder normalized = new StringBuilder(value.length());
        boolean previousCarriageReturn = false;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\r') {
                normalized.append('\n');
                previousCarriageReturn = true;
                continue;
            }
            if (codePoint == '\n') {
                if (!previousCarriageReturn) {
                    normalized.append('\n');
                }
                previousCarriageReturn = false;
                continue;
            }
            previousCarriageReturn = false;
            int type = Character.getType(codePoint);
            if (type == Character.SPACE_SEPARATOR) {
                normalized.append(' ');
            } else if (type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR) {
                normalized.append('\n');
            } else if (codePoint == '\t') {
                normalized.append(' ');
            } else if (!Character.isISOControl(codePoint)) {
                normalized.appendCodePoint(codePoint);
            }
        }

        return normalized.toString()
            .replaceAll("[ ]+", " ")
            .replaceAll(" *\\n *", "\n")
            .replaceAll("\\n{3,}", "\n\n")
            .strip();
    }

    private static final class RejectEmbeddedDocuments implements EmbeddedDocumentExtractor {

        @Override
        public boolean shouldParseEmbedded(Metadata metadata) {
            return false;
        }

        @Override
        public void parseEmbedded(
            InputStream stream,
            ContentHandler handler,
            Metadata metadata,
            boolean outputHtml
        ) {
            // Embedded and externally linked content is intentionally excluded.
        }
    }
}
