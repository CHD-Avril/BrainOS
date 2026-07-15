package com.brainos.document.parsing;

public final class UnsupportedDocumentTypeException extends RuntimeException {

    public UnsupportedDocumentTypeException(String mimeType) {
        super("不支持的文档类型: " + mimeType);
    }
}
