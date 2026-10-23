-- ─── payments ────────────────────────────────────────────────────────────────
CREATE TABLE payments (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    user_id              BIGINT        NOT NULL,
    transaction_id       VARCHAR(30)   NOT NULL,
    session_key          VARCHAR(50),
    amount               DECIMAL(10,2) NOT NULL,
    currency             VARCHAR(3)    NOT NULL,
    status               ENUM('PENDING','VALID','ON_HOLD','FAILED','CANCELLED','EXPIRED','UNATTEMPTED') NOT NULL DEFAULT 'PENDING',
    validation_id        VARCHAR(50),
    bank_transaction_id  VARCHAR(80),
    card_type            VARCHAR(50),
    risk_level           INT,
    risk_title           VARCHAR(50),
    validated_at         DATETIME(6),
    created_at           DATETIME(6),
    updated_at           DATETIME(6),
    version              INT,
    PRIMARY KEY (id),
    CONSTRAINT uk_payments_transaction_id UNIQUE (transaction_id),
    CONSTRAINT fk_payments_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_payments_user   ON payments (user_id);
CREATE INDEX idx_payments_status ON payments (status);
