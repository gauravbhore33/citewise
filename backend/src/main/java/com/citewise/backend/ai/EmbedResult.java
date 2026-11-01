package com.citewise.backend.ai;

import java.util.List;

public record EmbedResult(String model, int dimension, List<List<Double>> embeddings) {}