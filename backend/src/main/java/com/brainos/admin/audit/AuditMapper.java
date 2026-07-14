package com.brainos.admin.audit;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

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
}
