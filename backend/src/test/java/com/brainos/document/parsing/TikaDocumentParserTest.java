package com.brainos.document.parsing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TikaDocumentParserTest {

    private static final String DOCX_MIME =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @TempDir
    Path tempDir;

    private final DocumentParserPort parser = new TikaDocumentParser();

    @Test
    void parsesPlainTextAndNormalizesLineEndingsWithoutLosingParagraphs() throws IOException {
        Path file = tempDir.resolve("handbook.txt");
        Files.writeString(file, " First\r\nline\r\n\r\nSecond\u00a0paragraph ", StandardCharsets.UTF_8);

        List<ParsedSection> sections = parser.parse(file, "text/plain");

        assertThat(sections).containsExactly(new ParsedSection("First\nline\n\nSecond paragraph", null));
    }

    @Test
    void parsesMarkdownAsTextWithoutGuessingFromExtension() throws IOException {
        Path file = tempDir.resolve("guide.bin");
        Files.writeString(file, "# Guide\n\nUse the portal.", StandardCharsets.UTF_8);

        assertThat(parser.parse(file, "text/markdown"))
            .containsExactly(new ParsedSection("# Guide\n\nUse the portal.", null));
    }

    @Test
    void parsesDocxWithNoPageNumber() throws IOException {
        Path file = tempDir.resolve("policy.data");
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("Travel policy");
            document.createParagraph().createRun().setText("Approval is required.");
            try (var output = Files.newOutputStream(file)) {
                document.write(output);
            }
        }

        List<ParsedSection> sections = parser.parse(file, DOCX_MIME);

        assertThat(sections).hasSize(1);
        assertThat(sections.getFirst().text()).contains("Travel policy", "Approval is required.");
        assertThat(sections.getFirst().pageNumber()).isNull();
    }

    @Test
    void parsesPdfIntoRealOneBasedPages() throws IOException {
        Path file = createPdf("First page handbook", "Second page benefits");

        List<ParsedSection> sections = parser.parse(file, "application/pdf");

        assertThat(sections).containsExactly(
            new ParsedSection("First page handbook", 1),
            new ParsedSection("Second page benefits", 2)
        );
    }

    @Test
    void rejectsBlankOrControlOnlyDocumentsWithStableMessage() throws IOException {
        Path blankText = tempDir.resolve("blank.txt");
        Files.writeString(blankText, " \r\n\u0001\u0002\t", StandardCharsets.UTF_8);
        Path blankPdf = createPdf((String) null);

        assertNoUsableText(blankText, "text/plain");
        assertNoUsableText(blankPdf, "application/pdf");
    }

    @Test
    void rejectsUnsupportedMimeEvenWhenExtensionLooksSupported() throws IOException {
        Path file = tempDir.resolve("looks-safe.txt");
        Files.writeString(file, "content", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parser.parse(file, "application/octet-stream"))
            .isInstanceOf(UnsupportedDocumentTypeException.class);
    }

    private void assertNoUsableText(Path file, String mimeType) {
        assertThatThrownBy(() -> parser.parse(file, mimeType))
            .isInstanceOf(NoUsableTextException.class)
            .hasMessage("未提取到可用文本");
    }

    private Path createPdf(String... pageTexts) throws IOException {
        Path file = tempDir.resolve("document-" + System.nanoTime() + ".pdf");
        try (PDDocument document = new PDDocument()) {
            for (String text : pageTexts) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (text != null) {
                    try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                        content.beginText();
                        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        content.newLineAtOffset(72, 720);
                        content.showText(text);
                        content.endText();
                    }
                }
            }
            document.save(file.toFile());
        }
        try (PDDocument ignored = Loader.loadPDF(file.toFile())) {
            assertThat(ignored.getNumberOfPages()).isEqualTo(pageTexts.length);
        }
        return file;
    }
}
