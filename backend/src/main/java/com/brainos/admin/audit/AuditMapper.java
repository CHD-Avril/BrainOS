package com.brainos.admin.audit;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuditMapper extends AuditRecorder {

    @Override
    @Insert("""
            INSERT INTO audit_log (
                user_id, action, target_type, target_id, result, summary
            ) VALUES (
                #{userId}, #{action}, #{targetType}, #{targetId}, #{result}, #{summary}
            )
            """)
    void record(AuditEvent event);

    @Select("""
            <script>
            SELECT l.id, l.user_id AS userId, u.username,
                   l.action, l.target_type AS targetType, l.target_id AS targetId,
                   l.result, l.summary, l.created_at AS createdAt
            FROM audit_log l
            LEFT JOIN sys_user u ON u.id = l.user_id
            <where>
              <if test="userId != null">AND l.user_id = #{userId}</if>
              <if test="username != null">AND u.username = #{username}</if>
              <if test="action != null">AND l.action = #{action}</if>
              <if test="from != null">AND l.created_at &gt;= #{from}</if>
              <if test="to != null">AND l.created_at &lt;= #{to}</if>
            </where>
            ORDER BY l.created_at DESC, l.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AuditLogView> findPage(
            @Param("userId") Long userId,
            @Param("username") String username,
            @Param("action") String action,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("limit") int limit,
            @Param("offset") long offset);

    @Select("""
            <script>
            SELECT COUNT(*) FROM audit_log l
            LEFT JOIN sys_user u ON u.id = l.user_id
            <where>
              <if test="userId != null">AND l.user_id = #{userId}</if>
              <if test="username != null">AND u.username = #{username}</if>
              <if test="action != null">AND l.action = #{action}</if>
              <if test="from != null">AND l.created_at &gt;= #{from}</if>
              <if test="to != null">AND l.created_at &lt;= #{to}</if>
            </where>
            </script>
            """)
    long count(
            @Param("userId") Long userId,
            @Param("username") String username,
            @Param("action") String action,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
