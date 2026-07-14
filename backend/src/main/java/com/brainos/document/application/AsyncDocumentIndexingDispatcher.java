package com.brainos.document.application;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.task.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
final class AsyncDocumentIndexingDispatcher implements DocumentIndexingDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncDocumentIndexingDispatcher.class);

    private final TaskExecutor executor;
    private final ObjectProvider<DocumentIndexingService> services;

    AsyncDocumentIndexingDispatcher(
            @Qualifier("documentIndexingTaskExecutor") TaskExecutor executor,
            ObjectProvider<DocumentIndexingService> services) {
        this.executor = executor;
        this.services = services;
    }

    @Override
    public void submit(long documentId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    schedule(documentId);
                }
            });
            return;
        }
        schedule(documentId);
    }

    private void schedule(long documentId) {
        try {
            executor.execute(() -> run(documentId));
        } catch (RuntimeException exception) {
            markFailed(documentId);
        }
    }

    private void run(long documentId) {
        try {
            services.getObject().index(documentId);
        } catch (RuntimeException exception) {
            markFailed(documentId);
        }
    }

    private void markFailed(long documentId) {
        try {
            services.getObject().markDispatchFailed(documentId);
        } catch (RuntimeException exception) {
            LOGGER.error("索引任务失败状态写入失败，documentId={}", documentId);
        }
    }
}
