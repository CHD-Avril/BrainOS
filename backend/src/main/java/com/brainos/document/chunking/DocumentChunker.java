package com.brainos.document.chunking;

import com.brainos.document.parsing.DocumentParsingException;
import com.brainos.document.parsing.NoUsableTextException;
import com.brainos.document.parsing.ParsedSection;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class DocumentChunker {

    private static final int MIN_CHUNK_SIZE_CHARACTERS = 20;
    private static final int MIN_CHUNK_LENGTH_TO_EMBED = 5;
    private static final int MAX_CHUNKS = 10_000;
    private static final Pattern MARKDOWN_SECTION =
        Pattern.compile("(?m)(?=^[ \\t]{0,3}#{1,6}[ \\t]+\\S)");

    private final int overlapCharacters;
    private final int maxDocumentChunks;
    private final TokenTextSplitter tokenTextSplitter;

    @Autowired
    public DocumentChunker(
        @Value("${brainos.document.chunk-size:500}") int chunkSize,
        @Value("${brainos.document.overlap:80}") int overlapCharacters
    ) {
        this(chunkSize, overlapCharacters, MAX_CHUNKS);
    }

    DocumentChunker(int chunkSize, int overlapCharacters, int maxDocumentChunks) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        if (overlapCharacters < 0) {
            throw new IllegalArgumentException("overlapCharacters must not be negative");
        }
        if (maxDocumentChunks <= 0) {
            throw new IllegalArgumentException("maxDocumentChunks must be positive");
        }
        this.overlapCharacters = overlapCharacters;
        this.maxDocumentChunks = maxDocumentChunks;
        this.tokenTextSplitter = TokenTextSplitter.builder()
            .withChunkSize(chunkSize)
            .withMinChunkSizeChars(MIN_CHUNK_SIZE_CHARACTERS)
            .withMinChunkLengthToEmbed(MIN_CHUNK_LENGTH_TO_EMBED)
            .withMaxNumChunks(maxDocumentChunks)
            .withKeepSeparator(true)
            .build();
    }

    public List<DocumentChunk> chunk(
        Long knowledgeBaseId,
        Long documentId,
        String fileName,
        List<ParsedSection> sections
    ) {
        List<DocumentChunk> chunks = new ArrayList<>();
        for (ParsedSection section : sections) {
            if (section == null || section.text() == null || section.text().isBlank()) {
                continue;
            }
            for (String logicalUnit : logicalUnits(section.text())) {
                List<String> splitTexts = splitByLength(logicalUnit);
                String previous = null;
                for (String splitText : splitTexts) {
                    if (chunks.size() >= maxDocumentChunks) {
                        throw new DocumentParsingException("文档切片数量过多");
                    }
                    String chunkText = addCharacterOverlap(previous, splitText);
                    int chunkIndex = chunks.size();
                    chunks.add(new DocumentChunk(
                        documentId + ":" + chunkIndex,
                        chunkText,
                        knowledgeBaseId,
                        documentId,
                        fileName,
                        section.pageNumber(),
                        chunkIndex
                    ));
                    previous = chunkText;
                }
            }
        }
        if (chunks.isEmpty()) {
            throw new NoUsableTextException();
        }
        return List.copyOf(chunks);
    }

    private List<String> splitByLength(String text) {
        List<String> splitTexts = tokenTextSplitter.apply(List.of(new Document(text))).stream()
            .map(Document::getText)
            .filter(value -> value != null && !value.isBlank())
            .map(String::strip)
            .toList();
        return splitTexts.isEmpty() ? List.of(text.strip()) : splitTexts;
    }

    private static List<String> logicalUnits(String text) {
        String normalized = text.strip();
        List<String> units = MARKDOWN_SECTION.splitAsStream(normalized)
            .filter(value -> value != null && !value.isBlank())
            .map(String::strip)
            .toList();
        return units.isEmpty() ? List.of(normalized) : units;
    }

    private String addCharacterOverlap(String previous, String current) {
        if (previous == null || overlapCharacters == 0) {
            return current;
        }
        int start = Math.max(0, previous.length() - overlapCharacters);
        return previous.substring(start) + "\n" + current;
    }
}
