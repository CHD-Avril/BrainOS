package com.brainos.knowledge.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeBaseRequestValidationTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void trimsRequestFieldsBeforeBeanValidation() {
        KnowledgeBaseCreateRequest create =
                new KnowledgeBaseCreateRequest("  产品知识库  ", "   ");
        KnowledgeBaseUpdateRequest update =
                new KnowledgeBaseUpdateRequest("  研发知识库  ", "  新描述  ");

        assertThat(create.name()).isEqualTo("产品知识库");
        assertThat(create.description()).isNull();
        assertThat(update.name()).isEqualTo("研发知识库");
        assertThat(update.description()).isEqualTo("新描述");
        assertThat(validator.validate(create)).isEmpty();
        assertThat(validator.validate(update)).isEmpty();
    }

    @Test
    void rejectsTrimmedNamesOutsideTwoToSixtyCharacters() {
        List<String> invalidNames =
                List.of("   ", " a ", "  " + "x".repeat(61) + "  ");

        for (String name : invalidNames) {
            assertThat(validator.validate(new KnowledgeBaseCreateRequest(name, null)))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("name");
            assertThat(validator.validate(new KnowledgeBaseUpdateRequest(name, null)))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("name");
        }
    }

    @Test
    void rejectsTrimmedDescriptionsOverFiveHundredCharacters() {
        String description = "  " + "x".repeat(501) + "  ";

        assertThat(validator.validate(new KnowledgeBaseCreateRequest("有效名称", description)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("description");
        assertThat(validator.validate(new KnowledgeBaseUpdateRequest("有效名称", description)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("description");
    }

    @Test
    void nullNameIsLeftForNotNullConstraint() {
        KnowledgeBaseCreateRequest create = new KnowledgeBaseCreateRequest(null, null);
        KnowledgeBaseUpdateRequest update = new KnowledgeBaseUpdateRequest(null, null);

        assertThat(validator.validate(create))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name");
        assertThat(validator.validate(update))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("name");
    }
}
