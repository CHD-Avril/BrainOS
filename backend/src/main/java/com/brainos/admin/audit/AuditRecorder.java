package com.brainos.admin.audit;

@FunctionalInterface
public interface AuditRecorder {

    void record(AuditEvent event);
}
