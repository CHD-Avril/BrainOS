package com.brainos.knowledge.persistence;

import com.brainos.knowledge.domain.KnowledgeBase;
import com.brainos.knowledge.domain.KnowledgeBaseRepository;
import com.brainos.knowledge.domain.KnowledgeBaseView;
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
public interface KnowledgeBaseMapper extends KnowledgeBaseRepository {

    String VIEW_SELECT = """
            SELECT kb.id,
                   kb.name,
                   kb.description,
                   kb.created_by AS createdBy,
                   COUNT(doc.id) AS documentCount,
                   COALESCE(SUM(CASE WHEN doc.status = 'READY' THEN 1 ELSE 0 END), 0)
                       AS readyDocumentCount,
                   kb.created_at AS createdAt,
                   kb.updated_at AS updatedAt
            FROM knowledge_base kb
            LEFT JOIN kb_document doc ON doc.knowledge_base_id = kb.id
            """;

    @Override
    @Select(VIEW_SELECT + """
            GROUP BY kb.id, kb.name, kb.description, kb.created_by, kb.created_at, kb.updated_at
            ORDER BY kb.updated_at DESC, kb.id DESC
            """)
    List<KnowledgeBaseView> findAll();

    @Override
    @Select(VIEW_SELECT + """
            WHERE kb.id = #{id}
            GROUP BY kb.id, kb.name, kb.description, kb.created_by, kb.created_at, kb.updated_at
            """)
    Optional<KnowledgeBaseView> findById(long id);

    @Override
    @Select("""
            <script>
            SELECT EXISTS(
                SELECT 1 FROM knowledge_base
                WHERE name = #{name}
                <if test="excludedId != null">AND id != #{excludedId}</if>
            )
            </script>
            """)
    boolean existsByName(@Param("name") String name, @Param("excludedId") Long excludedId);

    @Override
    @Insert("""
            INSERT INTO knowledge_base (name, description, created_by)
            VALUES (#{name}, #{description}, #{createdBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void create(KnowledgeBase knowledgeBase);

    @Override
    @Update("""
            UPDATE knowledge_base
            SET name = #{name}, description = #{description}
            WHERE id = #{id}
            """)
    int update(
            @Param("id") long id,
            @Param("name") String name,
            @Param("description") String description);

    @Override
    @Delete("DELETE FROM knowledge_base WHERE id = #{id}")
    int delete(long id);
}
