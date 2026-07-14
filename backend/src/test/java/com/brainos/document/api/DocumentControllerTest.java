package com.brainos.document.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brainos.auth.domain.UserRole;
import com.brainos.auth.security.UserPrincipal;
import com.brainos.document.application.DocumentIndexingService;
import com.brainos.document.application.DocumentUploadService;
import com.brainos.document.domain.DocumentStatus;
import com.brainos.document.domain.DocumentView;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DocumentControllerTest {

    private final DocumentUploadService uploads = mock(DocumentUploadService.class);
    private final DocumentIndexingService indexing = mock(DocumentIndexingService.class);
    private final DocumentController controller = new DocumentController(uploads, indexing);

    @Test
    void exposesUploadListStatusRetryAndDeleteContracts() {
        DocumentView view = document(DocumentStatus.PARSING);
        MockMultipartFile file = new MockMultipartFile(
                "file", "policy.txt", "text/plain", "制度".getBytes());
        UserPrincipal principal = new UserPrincipal(9L, "baron", UserRole.USER);
        when(uploads.upload(7L, file, 9L)).thenReturn(view);
        when(indexing.list(7L)).thenReturn(List.of(view));
        when(indexing.get(7L, 44L)).thenReturn(view);
        when(indexing.retry(7L, 44L)).thenReturn(view);

        assertThat(controller.upload(7L, file, principal).data()).isEqualTo(view);
        assertThat(controller.list(7L).data()).containsExactly(view);
        assertThat(controller.get(7L, 44L).data()).isEqualTo(view);
        assertThat(controller.retry(7L, 44L).data()).isEqualTo(view);
        assertThat(controller.delete(7L, 44L).code()).isEqualTo("OK");

        verify(indexing).delete(7L, 44L);
    }

    private static DocumentView document(DocumentStatus status) {
        return new DocumentView(
                44L,
                7L,
                "policy.txt",
                "/tmp/policy.txt",
                "text/plain",
                12L,
                "a".repeat(64),
                status,
                0,
                null,
                9L,
                Instant.EPOCH,
                Instant.EPOCH);
    }
}
