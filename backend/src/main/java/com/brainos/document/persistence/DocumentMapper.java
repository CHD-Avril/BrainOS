package com.brainos.document.persistence;

import com.brainos.document.domain.DocumentRepository;
import com.brainos.document.domain.DocumentView;
import com.brainos.document.domain.KnowledgeDocument;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
            WHERE knowledge_base_id = #{knowledgeBaseId}
            ORDER BY created_at DESC, id DESC
            """)
    List<DocumentView> findAllByKnowledgeBaseId(long knowledgeBaseId);

    @Override
    @Update("""
            UPDATE kb_document
            SET status = 'PARSING', chunk_count = 0, failure_reason = NULL
            WHERE id = #{id} AND status = 'FAILED'
            """)
    int markParsing(long id);

    @Override
    @Update("""
            UPDATE kb_document
            SET status = 'INDEXING', chunk_count = 0, failure_reason = NULL
            WHERE id = #{id} AND status = 'PARSING'
            """)
    int markIndexing(long id);

    @Override
    @Update("""
            UPDATE kb_document
            SET status = 'READY', chunk_count = #{chunkCount}, failure_reason = NULL
            WHERE id = #{id} AND status = 'INDEXING'
            """)
    int markReady(@Param("id") long id, @Param("chunkCount") int chunkCount);

    @Override
    @Update("""
            UPDATE kb_document
            SET status = 'FAILED', chunk_count = 0, failure_reason = #{failureReason}
            WHERE id = #{id}
            """)
    int markFailed(@Param("id") long id, @Param("failureReason") String failureReason);

    @Override
    @Delete("DELETE FROM kb_document WHERE id = #{id}")
    int delete(long id);
}
