package com.lumora.cloud.catalog.domain;

public final class CatalogTypes {

    private CatalogTypes() {
    }

    public enum ProviderStatus {
        ACTIVE,
        DISABLED
    }

    public enum ModelStatus {
        ACTIVE,
        DISABLED
    }

    public enum VersionStatus {
        DRAFT,
        PUBLISHED,
        ARCHIVED
    }
}
