package com.brainos.rag.persistence;

import com.brainos.rag.domain.ChatMessageEntity;
import com.brainos.rag.domain.ChatMessageRow;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChatMessageMapper {

    @Insert("""
            INSERT INTO chat_message (session_id, role, content, citations_json)
            VALUES (#{sessionId}, #{role}, #{content}, #{citationsJson})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void create(ChatMessageEntity message);

    @Select("""
            SELECT id, session_id AS sessionId, role, content,
                   citations_json AS citationsJson, created_at AS createdAt
            FROM chat_message
            WHERE session_id = #{sessionId}
            ORDER BY created_at, id
            """)
    List<ChatMessageRow> findAllBySessionId(long sessionId);
}
