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
    private static final int TOP_K = 8;
    private static final int MAX_CONTEXT_CANDIDATES = 5;
    private static final double CANDIDATE_THRESHOLD = 0.25d;
    private static final double MAX_SEMANTIC_SCORE_GAP = 0.12d;
    private static final int MIN_EXACT_PHRASE_LENGTH = 4;

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
        double candidateThreshold = Math.min(threshold, CANDIDATE_THRESHOLD);
        List<CitationCandidate> citations = retriever.retrieve(
                knowledgeBaseId, question, TOP_K, candidateThreshold);
        if (citations.isEmpty()) {
            return RagAnswerPlan.fallback(NO_RELIABLE_EVIDENCE);
        }
        if (citations.stream().anyMatch(candidate -> candidate.knowledgeBaseId() != knowledgeBaseId)) {
            throw new IllegalStateException("检索结果知识库不一致");
        }
        List<CitationCandidate> ranked = citations.stream()
                .sorted(Comparator.comparingDouble(CitationCandidate::score).reversed())
                .toList();
        List<CitationCandidate> exactMatches = ranked.stream()
                .filter(candidate -> hasExactPhrase(question, candidate.snippet()))
                .limit(MAX_CONTEXT_CANDIDATES)
                .toList();
        boolean semanticSetIsTrusted = ranked.getFirst().score() >= threshold;
        double semanticCutoff = ranked.getFirst().score() - MAX_SEMANTIC_SCORE_GAP;
        List<CitationCandidate> reliable = !exactMatches.isEmpty()
                ? exactMatches
                : semanticSetIsTrusted
                        ? ranked.stream()
                                .filter(candidate -> candidate.score() >= semanticCutoff)
                                .limit(MAX_CONTEXT_CANDIDATES)
                                .toList()
                        : List.of();
        return reliable.isEmpty()
                ? RagAnswerPlan.fallback(NO_RELIABLE_EVIDENCE)
                : RagAnswerPlan.grounded(reliable);
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

    private static boolean hasExactPhrase(String question, String snippet) {
        String normalizedQuestion = compact(question);
        String normalizedSnippet = compact(snippet);
        if (normalizedQuestion.length() < MIN_EXACT_PHRASE_LENGTH
                || normalizedSnippet.length() < MIN_EXACT_PHRASE_LENGTH) {
            return false;
        }
        int[] previous = new int[normalizedSnippet.length() + 1];
        int[] current = new int[normalizedSnippet.length() + 1];
        for (int questionIndex = 1; questionIndex <= normalizedQuestion.length(); questionIndex++) {
            for (int snippetIndex = 1; snippetIndex <= normalizedSnippet.length(); snippetIndex++) {
                if (normalizedQuestion.charAt(questionIndex - 1)
                        == normalizedSnippet.charAt(snippetIndex - 1)) {
                    current[snippetIndex] = previous[snippetIndex - 1] + 1;
                    if (current[snippetIndex] >= MIN_EXACT_PHRASE_LENGTH) {
                        return true;
                    }
                } else {
                    current[snippetIndex] = 0;
                }
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return false;
    }

    private static String compact(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder normalized = new StringBuilder(value.length());
        value.codePoints()
                .filter(Character::isLetterOrDigit)
                .map(Character::toLowerCase)
                .forEach(normalized::appendCodePoint);
        return normalized.toString();
    }
}
