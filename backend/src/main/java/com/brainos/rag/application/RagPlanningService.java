package com.brainos.rag.application;

import com.brainos.common.api.ApiException;
import com.brainos.common.api.ErrorCode;
import com.brainos.rag.retrieval.CitationCandidate;
import com.brainos.rag.retrieval.RagRetriever;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RagPlanningService {

    public static final String NO_RELIABLE_EVIDENCE = "当前知识库中未找到可靠依据";
    private static final int TOP_K = 5;

    private final RagRetriever retriever;
    private final double threshold;

    @Autowired
    public RagPlanningService(
            RagRetriever retriever,
            @Value("${brainos.ai.retrieval-threshold:0.45}") double threshold) {
        if (!Double.isFinite(threshold) || threshold < 0d || threshold > 1d) {
            throw new IllegalArgumentException("retrieval threshold must be between 0 and 1");
        }
        this.retriever = retriever;
        this.threshold = threshold;
    }

    public RagAnswerPlan plan(long knowledgeBaseId, String rawQuestion) {
        String question = normalizeQuestion(rawQuestion);
        List<CitationCandidate> citations = retriever.retrieve(
                knowledgeBaseId, question, TOP_K, threshold);
        if (citations.isEmpty()) {
            return RagAnswerPlan.fallback(NO_RELIABLE_EVIDENCE);
        }
        if (citations.stream().anyMatch(candidate -> candidate.knowledgeBaseId() != knowledgeBaseId)) {
            throw new IllegalStateException("检索结果知识库不一致");
        }
        return RagAnswerPlan.grounded(citations.stream()
                .sorted(Comparator.comparingDouble(CitationCandidate::score).reversed())
                .toList());
    }

    private static String normalizeQuestion(String rawQuestion) {
        if (rawQuestion == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
        String question = rawQuestion.trim();
        if (question.isEmpty() || question.length() > 1000) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
        return question;
    }
}
