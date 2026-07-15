package com.brainos.rag.model;

public final class ChatModelNotConfiguredException extends RuntimeException {

    public ChatModelNotConfiguredException(ChatModelType model) {
        super(switch (model) {
            case QWEN -> "千问聊天模型未配置";
            case DEEPSEEK -> "DeepSeek 聊天模型未配置";
            case CHATGPT -> "ChatGPT 聊天模型未配置";
        });
    }
}
