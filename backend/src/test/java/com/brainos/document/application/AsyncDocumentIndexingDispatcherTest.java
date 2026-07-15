package com.brainos.document.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AsyncDocumentIndexingDispatcherTest {

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void rejectedAfterCommitSubmissionMarksDocumentFailed() {
        TaskExecutor executor = mock(TaskExecutor.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<DocumentIndexingService> services = mock(ObjectProvider.class);
        DocumentIndexingService service = mock(DocumentIndexingService.class);
        when(services.getObject()).thenReturn(service);
        doThrow(new TaskRejectedException("queue full")).when(executor).execute(any(Runnable.class));
        AsyncDocumentIndexingDispatcher dispatcher =
                new AsyncDocumentIndexingDispatcher(executor, services);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        dispatcher.submit(44L);
        TransactionSynchronizationManager.getSynchronizations().getFirst().afterCommit();

        verify(service).markDispatchFailed(44L);
    }

    @Test
    void unexpectedBackgroundFailureMarksDocumentFailed() {
        TaskExecutor executor = mock(TaskExecutor.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<DocumentIndexingService> services = mock(ObjectProvider.class);
        DocumentIndexingService service = mock(DocumentIndexingService.class);
        when(services.getObject()).thenReturn(service);
        doThrow(new RuntimeException("unexpected")).when(service).index(44L);
        doAnswer(invocation -> {
                    ((Runnable) invocation.getArgument(0)).run();
                    return null;
                })
                .when(executor)
                .execute(any(Runnable.class));
        AsyncDocumentIndexingDispatcher dispatcher =
                new AsyncDocumentIndexingDispatcher(executor, services);

        dispatcher.submit(44L);

        verify(service).markDispatchFailed(44L);
    }
}
