package com.lumora.cloud.catalog.domain.dto.model;

import jakarta.validation.constraints.Min;

public record PublishDraftRequest(@Min(0) long expectedRevision) {
}
