package com.brainos.rag.persistence;

import com.brainos.rag.domain.ChatSessionEntity;
import com.brainos.rag.domain.ChatSessionView;
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
public interface ChatSessionMapper {

    @Insert("""
            INSERT INTO chat_session (title, knowledge_base_id, chat_model, user_id)
            VALUES (#{title}, #{knowledgeBaseId}, #{chatModel}, #{userId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void create(ChatSessionEntity session);

    @Select("""
            SELECT id, title, knowledge_base_id AS knowledgeBaseId,
                   chat_model AS chatModel, user_id AS userId,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM chat_session
            WHERE user_id = #{userId}
            ORDER BY updated_at DESC, id DESC
            """)
    List<ChatSessionView> findAllByUserId(long userId);

    @Select("""
            SELECT id, title, knowledge_base_id AS knowledgeBaseId,
                   chat_model AS chatModel, user_id AS userId,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM chat_session
            WHERE id = #{id} AND user_id = #{userId}
            """)
    Optional<ChatSessionView> findOwned(@Param("id") long id, @Param("userId") long userId);

    @Update("""
            UPDATE chat_session SET title = #{title}
            WHERE id = #{id} AND user_id = #{userId}
            """)
    int rename(@Param("id") long id, @Param("userId") long userId, @Param("title") String title);

    @Update("UPDATE chat_session SET updated_at = CURRENT_TIMESTAMP(3) WHERE id = #{id}")
    int touch(long id);

    @Delete("DELETE FROM chat_session WHERE id = #{id} AND user_id = #{userId}")
    int deleteOwned(@Param("id") long id, @Param("userId") long userId);
}
