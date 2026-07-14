package com.brainos.knowledge.application;

import com.brainos.admin.audit.AuditEvent;
import com.brainos.admin.audit.AuditRecorder;
import com.brainos.common.api.ApiException;
import com.brainos.common.api.ErrorCode;
import com.brainos.knowledge.domain.KnowledgeBase;
import com.brainos.knowledge.domain.KnowledgeBaseRepository;
import com.brainos.knowledge.domain.KnowledgeBaseView;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBases;
    private final List<KnowledgeBaseCleanupPort> cleanupPorts;
    private final AuditRecorder audit;

    @Autowired
    public KnowledgeBaseService(
            KnowledgeBaseRepository knowledgeBases,
            List<KnowledgeBaseCleanupPort> cleanupPorts,
            AuditRecorder audit) {
        this.knowledgeBases = knowledgeBases;
        this.cleanupPorts = List.copyOf(cleanupPorts);
        this.audit = audit;
    }

    public KnowledgeBaseService(
            KnowledgeBaseRepository knowledgeBases, List<KnowledgeBaseCleanupPort> cleanupPorts) {
        this(knowledgeBases, cleanupPorts, event -> {});
    }

    @Transactional
    public KnowledgeBaseView create(String rawName, String rawDescription, long createdBy) {
        String name = normalizeName(rawName);
        String description = normalizeDescription(rawDescription);
        if (knowledgeBases.existsByName(name, null)) {
            throw new ApiException(ErrorCode.CONFLICT);
        }
        KnowledgeBase knowledgeBase = new KnowledgeBase(null, name, description, createdBy);
        try {
            knowledgeBases.create(knowledgeBase);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(ErrorCode.CONFLICT);
        }
        audit.record(AuditEvent.knowledgeCreated(createdBy, knowledgeBase.id()));
        return get(knowledgeBase.id());
    }

    @Transactional(readOnly = true)
    public List<KnowledgeBaseView> list() {
        return knowledgeBases.findAll();
    }

    @Transactional(readOnly = true)
    public KnowledgeBaseView get(long id) {
        return knowledgeBases.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    @Transactional
    public KnowledgeBaseView update(long id, String rawName, String rawDescription) {
        return update(id, rawName, rawDescription, null);
    }

    @Transactional
    public KnowledgeBaseView update(
            long id, String rawName, String rawDescription, Long actorId) {
        get(id);
        String name = normalizeName(rawName);
        String description = normalizeDescription(rawDescription);
        if (knowledgeBases.existsByName(name, id)) {
            throw new ApiException(ErrorCode.CONFLICT);
        }
        try {
            if (knowledgeBases.update(id, name, description) == 0) {
                throw new ApiException(ErrorCode.NOT_FOUND);
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(ErrorCode.CONFLICT);
        }
        if (actorId != null) audit.record(AuditEvent.knowledgeUpdated(actorId, id));
        return get(id);
    }

    @Transactional
    public void delete(long id) {
        delete(id, null);
    }

    @Transactional
    public void delete(long id, Long actorId) {
        get(id);
        cleanupPorts.forEach(port -> port.cleanup(id));
        if (knowledgeBases.delete(id) == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        if (actorId != null) audit.record(AuditEvent.knowledgeDeleted(actorId, id));
    }

    private static String normalizeName(String rawName) {
        if (rawName == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
        String name = rawName.trim();
        if (name.length() < 2 || name.length() > 60) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
        return name;
    }

    private static String normalizeDescription(String rawDescription) {
        if (rawDescription == null) {
            return null;
        }
        String description = rawDescription.trim();
        if (description.length() > 500) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR);
        }
        return description.isEmpty() ? null : description;
    }
}
