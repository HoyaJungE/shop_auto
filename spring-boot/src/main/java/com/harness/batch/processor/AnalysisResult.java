package com.harness.batch.processor;

/**
 * Spring Batch AnalysisProcessor 출력 모델
 */
public record AnalysisResult(Long sourceId, String result, String status) {}
