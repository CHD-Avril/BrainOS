package com.brainos.document.application;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
final class AsyncDocumentIndexingDispatcher implements DocumentIndexingDispatcher {

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
        Runnable task = () -> services.getObject().index(documentId);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executor.execute(task);
                }
            });
            return;
        }
        executor.execute(task);
    }
}
