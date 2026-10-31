CREATE TABLE tenants (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    api_key    VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE rules (
    id             UUID         PRIMARY KEY,
    tenant_id      UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    endpoint       VARCHAR(255) NOT NULL,
    method         VARCHAR(16)  NOT NULL,
    max_requests   INTEGER      NOT NULL CHECK (max_requests > 0),
    window_seconds INTEGER      NOT NULL CHECK (window_seconds > 0),
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_rules_tenant_endpoint_method UNIQUE (tenant_id, endpoint, method)
);
