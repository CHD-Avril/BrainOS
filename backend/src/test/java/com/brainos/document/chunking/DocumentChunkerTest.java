package com.brainos.document.chunking;

import static org.assertj.core.api.Assertions.assertThat;

import com.brainos.document.parsing.ParsedSection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentChunkerTest {

    @Test
    void producesStableOrderedChunksAndFixedMetadata() {
        DocumentChunker chunker = new DocumentChunker(40, 8);
        List<ParsedSection> sections = List.of(
            new ParsedSection("Alpha policy paragraph.", 2),
            new ParsedSection("Beta procedure paragraph.", null)
        );

        List<DocumentChunk> first = chunker.chunk(7L, 19L, "handbook.pdf", sections);
        List<DocumentChunk> second = chunker.chunk(7L, 19L, "handbook.pdf", sections);

        assertThat(first).isEqualTo(second);
        assertThat(first).extracting(DocumentChunk::id).containsExactly("19:0", "19:1");
        assertThat(first).extracting(DocumentChunk::chunkIndex).containsExactly(0, 1);
        assertThat(first.get(0).metadata()).isEqualTo(Map.of(
            "knowledgeBaseId", 7L,
            "documentId", 19L,
            "fileName", "handbook.pdf",
            "pageNumber", 2,
            "chunkIndex", 0
        ));
        assertThat(first.get(1).metadata().get("pageNumber")).isEqualTo(-1);
    }

    @Test
    void splitsLongSectionsIntoMultipleNonBlankChunksWithStableOverlap() {
        DocumentChunker chunker = new DocumentChunker(80, 16);
        String text = "Enterprise knowledge improves decisions. ".repeat(30);

        List<DocumentChunk> chunks = chunker.chunk(
            3L,
            11L,
            "knowledge.md",
            List.of(new ParsedSection(text, null), new ParsedSection(" \n\t", null))
        );

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.text()).isNotBlank();
            assertThat(chunk.id()).isEqualTo("11:" + chunk.chunkIndex());
            assertThat(chunk.knowledgeBaseId()).isEqualTo(3L);
            assertThat(chunk.documentId()).isEqualTo(11L);
            assertThat(chunk.fileName()).isEqualTo("knowledge.md");
            assertThat(chunk.pageNumber()).isNull();
        });
        assertThat(chunks).extracting(DocumentChunk::chunkIndex)
            .containsExactlyElementsOf(java.util.stream.IntStream.range(0, chunks.size()).boxed().toList());
        for (int index = 1; index < chunks.size(); index++) {
            String previous = chunks.get(index - 1).text();
            assertThat(chunks.get(index).text()).startsWith(previous.substring(previous.length() - 16));
            assertThat(chunks.get(index).text().charAt(16)).isEqualTo('\n');
        }
        assertThat(chunks).isEqualTo(chunker.chunk(
            3L, 11L, "knowledge.md", List.of(new ParsedSection(text, null), new ParsedSection("", null))
        ));
    }
}
