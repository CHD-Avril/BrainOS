package com.brainos.rag.model;

import com.brainos.rag.retrieval.CitationCandidate;
import java.util.List;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

@Component
public final class RagPromptFactory {

    public Prompt create(String question, List<CitationCandidate> citations) {
        StringBuilder sources = new StringBuilder();
        for (int index = 0; index < citations.size(); index++) {
            CitationCandidate citation = citations.get(index);
            sources.append("[来源").append(index + 1).append("] ").append(citation.fileName());
            if (citation.pageNumber() != null) {
                sources.append(" 第").append(citation.pageNumber()).append("页");
            }
            sources.append('\n').append(citation.snippet()).append("\n\n");
        }
        String system = """
                你是企业知识库助手。只能依据以下来源回答，不得补充来源之外的事实。
                回答应简洁、清楚，并在关键结论后标注对应的[来源N]。
                无法从来源确认时必须明确说明，不要猜测。

                %s
                """.formatted(sources.toString().strip());
        return new Prompt(List.of(new SystemMessage(system), new UserMessage(question)));
    }
}
