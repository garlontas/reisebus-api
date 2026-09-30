--liquibase formatted sql

--changeset garlontas:000-initial-schema
CREATE TABLE event_publication
(
    id                     UUID                     NOT NULL,
    listener_id            TEXT                     NOT NULL,
    event_type             TEXT                     NOT NULL,
    serialized_event       TEXT                     NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_event_publication PRIMARY KEY (id)
);

CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);

--rollback DROP TABLE event_publication;


CREATE SCHEMA IF NOT EXISTS platform;

SET
search_path = platform, public;

CREATE TABLE tenant
(
    id           UUID PRIMARY KEY,
    company_name VARCHAR(200)             NOT NULL,
    slug         VARCHAR(63)              NOT NULL,
    schema_name  VARCHAR(63)              NOT NULL,
    status       VARCHAR(30)              NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_tenants_slug UNIQUE (slug),
    CONSTRAINT uk_tenants_schema_name UNIQUE (schema_name)
);

CREATE INDEX idx_tenants_status ON tenant (status);

CREATE TABLE tenant_domain
(
    id         UUID PRIMARY KEY,
    tenant_id  UUID                     NOT NULL,
    domain     VARCHAR(255)             NOT NULL,
    is_primary BOOLEAN                  NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_tenant_domain_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT uk_tenant_domain_domain UNIQUE (domain)
);

CREATE INDEX idx_tenant_domain_tenant_id ON tenant_domain (tenant_id);

CREATE TABLE tenant_settings
(
    tenant_id  UUID PRIMARY KEY REFERENCES tenant (id),
    branding   JSON                     NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_tenant_settings_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id)
);

CREATE INDEX idx_tenant_settings_tenant_id ON tenant_settings (tenant_id);

CREATE TABLE platform_user
(
    id            UUID PRIMARY KEY,
    tenant_id     UUID                     NOT NULL,
    email         VARCHAR(255)             NOT NULL,
    password_hash VARCHAR(255)             NOT NULL,
    first_name    VARCHAR(100)             NOT NULL,
    last_name     VARCHAR(100)             NOT NULL,
    role          VARCHAR(30)              NOT NULL,
    status        VARCHAR(30)              NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_user_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT uk_user_tenant_email UNIQUE (tenant_id, email)
);

CREATE INDEX idx_user_tenant_id ON platform_user (tenant_id);
CREATE INDEX idx_user_status ON platform_user (status);
