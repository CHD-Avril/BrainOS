package com.brainos.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brainos.common.api.ApiException;
import com.brainos.knowledge.domain.KnowledgeBase;
import com.brainos.knowledge.domain.KnowledgeBaseRepository;
import com.brainos.knowledge.domain.KnowledgeBaseView;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KnowledgeBaseServiceTest {

    @Test
    void createsWithTrimmedFieldsAndCreator() {
        FakeRepository repository = new FakeRepository();
        KnowledgeBaseService service = new KnowledgeBaseService(repository, List.of());

        KnowledgeBaseView created = service.create("  产品知识库  ", "  产品资料  ", 42L);

        assertThat(created.name()).isEqualTo("产品知识库");
        assertThat(created.description()).isEqualTo("产品资料");
        assertThat(created.createdBy()).isEqualTo(42L);
        assertThat(created.documentCount()).isZero();
        assertThat(created.readyDocumentCount()).isZero();
    }

    @Test
    void rejectsDuplicateNameBeforeCreate() {
        FakeRepository repository = new FakeRepository();
        repository.create(new KnowledgeBase(null, "共享资料", null, 1L));
        KnowledgeBaseService service = new KnowledgeBaseService(repository, List.of());

        assertThatThrownBy(() -> service.create(" 共享资料 ", null, 42L))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode().code())
                .isEqualTo("CONFLICT");
    }

    @Test
    void updatesTrimmedFieldsWithoutTreatingCurrentNameAsDuplicate() {
        FakeRepository repository = new FakeRepository();
        KnowledgeBase existing = new KnowledgeBase(null, "研发资料", "旧描述", 7L);
        repository.create(existing);
        KnowledgeBaseService service = new KnowledgeBaseService(repository, List.of());

        KnowledgeBaseView updated = service.update(existing.id(), " 研发资料 ", " 新描述 ");

        assertThat(updated.name()).isEqualTo("研发资料");
        assertThat(updated.description()).isEqualTo("新描述");
        assertThat(updated.createdBy()).isEqualTo(7L);
    }

    @Test
    void returnsRepositoryDocumentCounts() {
        FakeRepository repository = new FakeRepository();
        repository.view = new KnowledgeBaseView(
                9L,
                "制度库",
                null,
                1L,
                5L,
                3L,
                Instant.parse("2026-07-14T08:00:00Z"),
                Instant.parse("2026-07-14T09:00:00Z"));
        KnowledgeBaseService service = new KnowledgeBaseService(repository, List.of());

        KnowledgeBaseView result = service.get(9L);

        assertThat(result.documentCount()).isEqualTo(5L);
        assertThat(result.readyDocumentCount()).isEqualTo(3L);
    }

    @Test
    void missingKnowledgeBaseIsNotFoundForGetUpdateAndDelete() {
        KnowledgeBaseService service = new KnowledgeBaseService(new FakeRepository(), List.of());

        assertNotFound(() -> service.get(404L));
        assertNotFound(() -> service.update(404L, "不存在", null));
        assertNotFound(() -> service.delete(404L));
    }

    @Test
    void cleanupFailureKeepsDatabaseRecord() {
        FakeRepository repository = new FakeRepository();
        KnowledgeBase existing = new KnowledgeBase(null, "待删除", null, 1L);
        repository.create(existing);
        KnowledgeBaseCleanupPort failingCleanup = id -> {
            throw new IllegalStateException("vector store unavailable");
        };
        KnowledgeBaseService service =
                new KnowledgeBaseService(repository, List.of(failingCleanup));

        assertThatThrownBy(() -> service.delete(existing.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("vector store unavailable");
        assertThat(repository.findById(existing.id())).isPresent();
    }

    @Test
    void validatesNameAndDescriptionInsideService() {
        KnowledgeBaseService service = new KnowledgeBaseService(new FakeRepository(), List.of());

        assertValidation(() -> service.create("a", null, 1L));
        assertValidation(() -> service.create("x".repeat(61), null, 1L));
        assertValidation(() -> service.create("有效名称", "x".repeat(501), 1L));
    }

    private static void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode().code())
                .isEqualTo("NOT_FOUND");
    }

    private static void assertValidation(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).errorCode().code())
                .isEqualTo("VALIDATION_ERROR");
    }

    private static final class FakeRepository implements KnowledgeBaseRepository {
        private final List<KnowledgeBase> rows = new ArrayList<>();
        private long sequence;
        private KnowledgeBaseView view;

        @Override
        public List<KnowledgeBaseView> findAll() {
            return rows.stream().map(this::toView).toList();
        }

        @Override
        public Optional<KnowledgeBaseView> findById(long id) {
            if (view != null && view.id() == id) {
                return Optional.of(view);
            }
            return rows.stream().filter(row -> row.id() == id).findFirst().map(this::toView);
        }

        @Override
        public boolean existsByName(String name, Long excludedId) {
            return rows.stream().anyMatch(row -> row.name().equalsIgnoreCase(name)
                    && (excludedId == null || row.id() != excludedId));
        }

        @Override
        public void create(KnowledgeBase knowledgeBase) {
            knowledgeBase.assignId(++sequence);
            rows.add(knowledgeBase);
        }

        @Override
        public int update(long id, String name, String description) {
            for (int index = 0; index < rows.size(); index++) {
                KnowledgeBase row = rows.get(index);
                if (row.id() == id) {
                    rows.set(index, new KnowledgeBase(id, name, description, row.createdBy()));
                    return 1;
                }
            }
            return 0;
        }

        @Override
        public int delete(long id) {
            return rows.removeIf(row -> row.id() == id) ? 1 : 0;
        }

        private KnowledgeBaseView toView(KnowledgeBase row) {
            Instant now = Instant.parse("2026-07-14T08:00:00Z");
            return new KnowledgeBaseView(
                    row.id(), row.name(), row.description(), row.createdBy(), 0L, 0L, now, now);
        }
    }
}
