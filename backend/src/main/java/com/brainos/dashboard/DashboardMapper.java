package com.brainos.dashboard;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DashboardMapper {

    @Select("""
            SELECT (SELECT COUNT(*) FROM knowledge_base) AS knowledgeBaseCount,
                   (SELECT COUNT(*) FROM kb_document) AS documentCount,
                   (SELECT COALESCE(SUM(chunk_count), 0)
                      FROM kb_document
                     WHERE status = 'READY') AS chunkCount,
                   (SELECT COUNT(*)
                      FROM chat_message
                     WHERE role = 'USER') AS questionCount
            """)
    DashboardSummary summary();

    @Select("""
            SELECT DATE(created_at) AS date, COUNT(*) AS count
            FROM chat_message
            WHERE role = 'USER'
              AND created_at >= #{startDate}
              AND created_at < DATE_ADD(#{endDate}, INTERVAL 1 DAY)
            GROUP BY DATE(created_at)
            ORDER BY date
            """)
    List<DailyCount> countQuestionsByDate(
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Select("""
            SELECT document.id,
                   document.knowledge_base_id AS knowledgeBaseId,
                   knowledge.name AS knowledgeBaseName,
                   document.original_name AS originalName,
                   document.status,
                   document.updated_at AS updatedAt
            FROM kb_document document
            INNER JOIN knowledge_base knowledge ON knowledge.id = document.knowledge_base_id
            ORDER BY document.updated_at DESC, document.id DESC
            LIMIT #{limit}
            """)
    List<RecentDocument> recentDocuments(int limit);
}
