--liquibase formatted sql

--changeset system:202609300001-initial-tenant-schema
CREATE TABLE booking (
id         UUID                     NOT NULL,
status     VARCHAR(30)              NOT NULL,
created_at TIMESTAMP WITH TIME ZONE NOT NULL,
updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
CONSTRAINT pk_booking PRIMARY KEY (id)
);

CREATE INDEX idx_booking_status ON booking (status);

--rollback DROP INDEX idx_booking_status;
--rollback DROP TABLE booking;