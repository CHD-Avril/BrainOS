package com.brainos.rag.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.brainos.rag.retrieval.CitationCandidate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagPromptFactoryTest {

    @Test
    void promptNumbersSourcesAndForbidsUnsupportedClaims() {
        var prompt = new RagPromptFactory().create("年假有几天？", List.of(
                candidate("handbook.pdf", 2, "年假为十天"),
                candidate("policy.md", null, "试用期同样适用")));

        assertThat(prompt.getContents())
                .contains("只能依据以下来源回答")
                .contains("无法从来源确认时必须明确说明")
                .contains("每个事实结论后必须标注真正包含该事实的[来源N]")
                .contains("禁止因为某来源排在第一位就默认引用[来源1]")
                .contains("被引用来源的原文必须直接支持该句")
                .contains("不要添加来源外的建议")
                .contains("[来源1] handbook.pdf 第2页")
                .contains("[来源2] policy.md")
                .contains("年假有几天？");
    }

    private static CitationCandidate candidate(String fileName, Integer page, String text) {
        return new CitationCandidate(7L, 44L, "44:0", fileName, page, 0, text, 0.9d);
    }
}
