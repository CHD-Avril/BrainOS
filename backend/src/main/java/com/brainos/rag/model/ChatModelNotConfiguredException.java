package com.brainos.rag.model;

public final class ChatModelNotConfiguredException extends RuntimeException {

    public ChatModelNotConfiguredException(ChatModelType model) {
        super(model == ChatModelType.QWEN ? "千问聊天模型未配置" : "DeepSeek 聊天模型未配置");
    }
}
