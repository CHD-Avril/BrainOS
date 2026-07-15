package com.brainos.dashboard;

public record DashboardSummary(
        long knowledgeBaseCount,
        long documentCount,
        long chunkCount,
        long questionCount) {}
