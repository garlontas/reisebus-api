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

SET search_path = platform, public;

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
    id                 UUID PRIMARY KEY,
    tenant_id          UUID                     NOT NULL,
    domain             VARCHAR(255)             NOT NULL,
    is_primary         BOOLEAN                  NOT NULL,
    verification_token VARCHAR(64)              NOT NULL,
    status             VARCHAR(20)              NOT NULL, -- PENDING, VERIFIED, ACTIVE, FAILED
    verified_at        TIMESTAMP,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_tenant_domain_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT uk_tenant_domain_domain UNIQUE (domain)
);

CREATE INDEX idx_tenant_domain_tenant_id ON tenant_domain (tenant_id);

CREATE TABLE tenant_settings
(
    tenant_id     UUID PRIMARY KEY REFERENCES tenant (id),
    contact_email VARCHAR(255)             NOT NULL,
    branding      JSONB                    NOT NULL DEFAULT '{}',
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_tenant_settings_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id)
);

CREATE TABLE users
(
    id               UUID PRIMARY KEY,
    email            VARCHAR(255)             NOT NULL,
    identity_issuer  VARCHAR(255)             NOT NULL,
    identity_subject VARCHAR(255)             NOT NULL,
    first_name       VARCHAR(100)             NOT NULL,
    last_name        VARCHAR(100)             NOT NULL,
    status           VARCHAR(30)              NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_identity UNIQUE (identity_issuer, identity_subject)
);

CREATE INDEX idx_users_status ON users (status);

CREATE TABLE platform_users
(
    user_id    UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_platform_users_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE user_tenants
(
    user_id   UUID        NOT NULL,
    tenant_id UUID        NOT NULL,
    role      VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, tenant_id),
    CONSTRAINT fk_user_tenants_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_tenants_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id)
);

CREATE TABLE invitation
(
    id          UUID PRIMARY KEY,
    tenant_id   UUID                     NOT NULL,
    email       VARCHAR(255)             NOT NULL,
    role        VARCHAR(30)              NOT NULL,
    token_hash  VARCHAR(64)              NOT NULL, -- nur den SHA-256-Hash speichern, nie den Token selbst
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_invitation_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT uk_invitation_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_invitation_tenant_id ON invitation (tenant_id);

--rollback DROP TABLE invitation;
--rollback DROP TABLE user_tenants;
--rollback DROP TABLE platform_users;
--rollback DROP TABLE users;
--rollback DROP TABLE tenant_settings;
--rollback DROP TABLE tenant_domain;
--rollback DROP TABLE tenant;
--rollback DROP SCHEMA platform;