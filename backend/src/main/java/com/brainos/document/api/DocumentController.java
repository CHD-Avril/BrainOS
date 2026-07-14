package com.brainos.document.api;

import com.brainos.auth.security.UserPrincipal;
import com.brainos.common.api.ApiResponse;
import com.brainos.document.application.DocumentIndexingService;
import com.brainos.document.application.DocumentUploadService;
import com.brainos.document.domain.DocumentView;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/knowledge-bases/{knowledgeBaseId}/documents")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class DocumentController {

    private final DocumentUploadService uploads;
    private final DocumentIndexingService indexing;

    public DocumentController(DocumentUploadService uploads, DocumentIndexingService indexing) {
        this.uploads = uploads;
        this.indexing = indexing;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentView> upload(
            @PathVariable long knowledgeBaseId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(uploads.upload(knowledgeBaseId, file, principal.userId()));
    }

    @GetMapping
    public ApiResponse<List<DocumentView>> list(@PathVariable long knowledgeBaseId) {
        return ApiResponse.success(indexing.list(knowledgeBaseId));
    }

    @GetMapping("/{documentId}")
    public ApiResponse<DocumentView> get(
            @PathVariable long knowledgeBaseId, @PathVariable long documentId) {
        return ApiResponse.success(indexing.get(knowledgeBaseId, documentId));
    }

    @PostMapping("/{documentId}/retry")
    public ApiResponse<DocumentView> retry(
            @PathVariable long knowledgeBaseId, @PathVariable long documentId) {
        return ApiResponse.success(indexing.retry(knowledgeBaseId, documentId));
    }

    @DeleteMapping("/{documentId}")
    public ApiResponse<Void> delete(
            @PathVariable long knowledgeBaseId, @PathVariable long documentId) {
        indexing.delete(knowledgeBaseId, documentId);
        return ApiResponse.success(null);
    }
}
