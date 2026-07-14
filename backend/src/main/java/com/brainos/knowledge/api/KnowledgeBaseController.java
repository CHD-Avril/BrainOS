package com.brainos.knowledge.api;

import com.brainos.auth.security.UserPrincipal;
import com.brainos.common.api.ApiResponse;
import com.brainos.knowledge.application.KnowledgeBaseService;
import com.brainos.knowledge.domain.KnowledgeBaseView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge-bases")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class KnowledgeBaseController {

    private final KnowledgeBaseService service;

    public KnowledgeBaseController(KnowledgeBaseService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<KnowledgeBaseView> create(
            @Valid @RequestBody KnowledgeBaseCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(
                service.create(request.name(), request.description(), principal.userId()));
    }

    @GetMapping
    public ApiResponse<List<KnowledgeBaseView>> list() {
        return ApiResponse.success(service.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<KnowledgeBaseView> get(@PathVariable long id) {
        return ApiResponse.success(service.get(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<KnowledgeBaseView> update(
            @PathVariable long id, @Valid @RequestBody KnowledgeBaseUpdateRequest request) {
        return ApiResponse.success(service.update(id, request.name(), request.description()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }
}
