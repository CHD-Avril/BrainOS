package com.brainos.document.persistence;

import com.brainos.document.domain.DocumentRepository;
import com.brainos.document.domain.DocumentView;
import com.brainos.document.domain.KnowledgeDocument;
import java.util.Optional;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DocumentMapper extends DocumentRepository {

    @Override
    @Select("""
            SELECT EXISTS(
                SELECT 1 FROM kb_document
                WHERE knowledge_base_id = #{knowledgeBaseId} AND sha256 = #{sha256}
            )
            """)
    boolean existsByKnowledgeBaseAndSha256(
            @Param("knowledgeBaseId") long knowledgeBaseId, @Param("sha256") String sha256);

    @Override
    @Insert("""
            INSERT INTO kb_document (
                knowledge_base_id, original_name, storage_path, mime_type, size_bytes,
                sha256, status, chunk_count, failure_reason, uploaded_by
            ) VALUES (
                #{knowledgeBaseId}, #{originalName}, #{storagePath}, #{mimeType}, #{sizeBytes},
                #{sha256}, #{status}, 0, NULL, #{uploadedBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void create(KnowledgeDocument document);

    @Override
    @Select("""
            SELECT id,
                   knowledge_base_id AS knowledgeBaseId,
                   original_name AS originalName,
                   storage_path AS storagePath,
                   mime_type AS mimeType,
                   size_bytes AS sizeBytes,
                   sha256,
                   status,
                   chunk_count AS chunkCount,
                   failure_reason AS failureReason,
                   uploaded_by AS uploadedBy,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM kb_document
            WHERE id = #{id}
            """)
    Optional<DocumentView> findById(long id);
}
