package com.brainos.document.application;

@FunctionalInterface
public interface DocumentIndexingDispatcher {

    void submit(long documentId);
}
