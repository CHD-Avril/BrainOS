package com.brainos.ai.embedding;

import java.util.List;

public interface EmbeddingPort {

    float[] embed(String text);

    List<float[]> embedAll(List<String> texts);
}
