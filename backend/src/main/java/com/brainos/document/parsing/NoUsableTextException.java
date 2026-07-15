package com.brainos.document.parsing;

public final class NoUsableTextException extends RuntimeException {

    public NoUsableTextException() {
        super("未提取到可用文本");
    }
}
