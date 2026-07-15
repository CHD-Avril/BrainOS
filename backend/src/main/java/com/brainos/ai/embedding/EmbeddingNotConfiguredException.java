package com.brainos.ai.embedding;

public final class EmbeddingNotConfiguredException extends IllegalStateException {

    public EmbeddingNotConfiguredException() {
        super("向量模型未配置");
    }
}
