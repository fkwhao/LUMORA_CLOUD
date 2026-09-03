package com.lumora.cloud.billing.domain.projection.history;

import java.math.BigDecimal;
import java.time.LocalDate;

public class DailyUsageAggregate {

    private LocalDate summaryDate;
    private Long requestCount;
    private Long inputTokens;
    private Long outputTokens;
    private Long reasoningTokens;
    private Long cacheReadTokens;
    private Long cacheWriteTokens;
    private BigDecimal billedQuota;

    public DailyUsageAggregate() {
    }

    public LocalDate getSummaryDate() { return summaryDate; }
    public void setSummaryDate(LocalDate summaryDate) { this.summaryDate = summaryDate; }
    public Long getRequestCount() { return requestCount; }
    public void setRequestCount(Long requestCount) { this.requestCount = requestCount; }
    public Long getInputTokens() { return inputTokens; }
    public void setInputTokens(Long inputTokens) { this.inputTokens = inputTokens; }
    public Long getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Long outputTokens) { this.outputTokens = outputTokens; }
    public Long getReasoningTokens() { return reasoningTokens; }
    public void setReasoningTokens(Long reasoningTokens) { this.reasoningTokens = reasoningTokens; }
    public Long getCacheReadTokens() { return cacheReadTokens; }
    public void setCacheReadTokens(Long cacheReadTokens) { this.cacheReadTokens = cacheReadTokens; }
    public Long getCacheWriteTokens() { return cacheWriteTokens; }
    public void setCacheWriteTokens(Long cacheWriteTokens) { this.cacheWriteTokens = cacheWriteTokens; }
    public BigDecimal getBilledQuota() { return billedQuota; }
    public void setBilledQuota(BigDecimal billedQuota) { this.billedQuota = billedQuota; }
}
