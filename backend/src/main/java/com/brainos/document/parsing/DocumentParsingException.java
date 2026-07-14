package com.brainos.document.parsing;

public final class DocumentParsingException extends RuntimeException {

    public DocumentParsingException(String message, Throwable cause) {
        super(message, cause);
    }

    public DocumentParsingException(String message) {
        super(message);
    }
}
