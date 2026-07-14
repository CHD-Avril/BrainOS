package com.brainos.rag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brainos.rag.retrieval.CitationCandidate;
import com.brainos.rag.retrieval.RagRetriever;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagPlanningServiceTest {

    private final RagRetriever retriever = mock(RagRetriever.class);
    private final RagPlanningService service = new RagPlanningService(retriever, 0.62d);

    @Test
    void noReliableChunkReturnsFallbackWithoutModelWork() {
        when(retriever.retrieve(7L, "年假有几天？", 5, 0.62d)).thenReturn(List.of());

        RagAnswerPlan plan = service.plan(7L, " 年假有几天？ ");

        assertThat(plan.isFallback()).isTrue();
        assertThat(plan.fallback()).isEqualTo("当前知识库中未找到可靠依据");
        assertThat(plan.citations()).isEmpty();
        verify(retriever).retrieve(7L, "年假有几天？", 5, 0.62d);
    }

    @Test
    void groundedPlanSortsCitationsByScore() {
        when(retriever.retrieve(7L, "年假", 5, 0.62d))
                .thenReturn(List.of(candidate(7L, 0.75d), candidate(7L, 0.92d)));

        RagAnswerPlan plan = service.plan(7L, "年假");

        assertThat(plan.isFallback()).isFalse();
        assertThat(plan.citations()).extracting(CitationCandidate::score).containsExactly(0.92d, 0.75d);
    }

    @Test
    void foreignKnowledgeBaseMetadataIsRejected() {
        when(retriever.retrieve(7L, "年假", 5, 0.62d))
                .thenReturn(List.of(candidate(8L, 0.92d)));

        assertThatThrownBy(() -> service.plan(7L, "年假"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("检索结果知识库不一致");
    }

    private static CitationCandidate candidate(long knowledgeBaseId, double score) {
        return new CitationCandidate(
                knowledgeBaseId, 44L, "44:0", "handbook.pdf", 2, 0, "年假为十天", score);
    }
}
