package com.lumora.cloud.billing.domain.vo.reconciliation;
import java.util.List;
public record ReconciliationPage(List<ReconciliationCase> items, long total, int page, int pageSize) {}
