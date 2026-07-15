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
                引用规则：
                1. 每个事实结论后必须标注真正包含该事实的[来源N]。
                2. 引用编号严格对应下方来源编号；禁止因为某来源排在第一位就默认引用[来源1]。
                3. 输出前逐句核对：被引用来源的原文必须直接支持该句；不支持就更换编号或删除该句。
                4. 回答应简洁、清楚，不要添加来源外的建议，也不要给出相互矛盾的表述。
                5. 无法从来源确认时必须明确说明，不要猜测。

                %s
                """.formatted(sources.toString().strip());
        return new Prompt(List.of(new SystemMessage(system), new UserMessage(question)));
    }
}
