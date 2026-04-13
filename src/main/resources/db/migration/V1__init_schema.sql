-- ─── users ───────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    first_name     VARCHAR(100) NOT NULL,
    last_name      VARCHAR(100) NOT NULL,
    role           VARCHAR(20)  NOT NULL,
    profession     VARCHAR(100),
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     DATETIME(6),
    updated_at     DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
);
CREATE INDEX idx_users_email ON users (email);

-- ─── refresh_tokens ───────────────────────────────────────────────────────────
CREATE TABLE refresh_tokens (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    token       VARCHAR(512) NOT NULL,
    expires_at  DATETIME(6)  NOT NULL,
    created_at  DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token UNIQUE (token),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- ─── tests ───────────────────────────────────────────────────────────────────
CREATE TABLE tests (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    title                    VARCHAR(255) NOT NULL,
    description              TEXT,
    sub_test_type            VARCHAR(20)  NOT NULL,
    total_time_limit_minutes INT          NOT NULL,
    is_published             BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by               BIGINT       NOT NULL,
    created_at               DATETIME(6),
    updated_at               DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_tests_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX idx_tests_published   ON tests (is_published);
CREATE INDEX idx_tests_created_by  ON tests (created_by);

-- ─── test_parts ──────────────────────────────────────────────────────────────
CREATE TABLE test_parts (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    test_id             BIGINT      NOT NULL,
    part_label          VARCHAR(10) NOT NULL,
    time_limit_minutes  INT,
    instructions        TEXT,
    sort_order          INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_test_parts_test_label UNIQUE (test_id, part_label),
    CONSTRAINT fk_test_parts_test FOREIGN KEY (test_id) REFERENCES tests (id) ON DELETE CASCADE
);

-- ─── text_passages ────────────────────────────────────────────────────────────
CREATE TABLE text_passages (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    test_part_id           BIGINT       NOT NULL,
    label                  VARCHAR(50)  NOT NULL,
    content                TEXT,
    audio_file_url         VARCHAR(500),
    audio_duration_seconds INT,
    sort_order             INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_text_passages_part FOREIGN KEY (test_part_id) REFERENCES test_parts (id) ON DELETE CASCADE
);

-- ─── question_groups ─────────────────────────────────────────────────────────
CREATE TABLE question_groups (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    test_part_id  BIGINT      NOT NULL,
    passage_id    BIGINT,
    title         VARCHAR(255),
    instructions  TEXT,
    question_type VARCHAR(30) NOT NULL,
    sort_order    INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_question_groups_part    FOREIGN KEY (test_part_id) REFERENCES test_parts    (id) ON DELETE CASCADE,
    CONSTRAINT fk_question_groups_passage FOREIGN KEY (passage_id)   REFERENCES text_passages (id) ON DELETE SET NULL
);

-- ─── questions ───────────────────────────────────────────────────────────────
CREATE TABLE questions (
    id                BIGINT NOT NULL AUTO_INCREMENT,
    question_group_id BIGINT NOT NULL,
    question_number   INT    NOT NULL,
    question_text     TEXT   NOT NULL,
    prefix_text       TEXT,
    suffix_text       TEXT,
    sort_order        INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_questions_group FOREIGN KEY (question_group_id) REFERENCES question_groups (id) ON DELETE CASCADE
);

-- ─── question_options ────────────────────────────────────────────────────────
CREATE TABLE question_options (
    id           BIGINT     NOT NULL AUTO_INCREMENT,
    question_id  BIGINT     NOT NULL,
    option_label CHAR(1)    NOT NULL,
    option_text  TEXT       NOT NULL,
    sort_order   INT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_question_options_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE
);

-- ─── correct_answers ─────────────────────────────────────────────────────────
CREATE TABLE correct_answers (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    question_id         BIGINT       NOT NULL,
    correct_option_id   BIGINT,
    correct_text        VARCHAR(500),
    alternative_answers JSON,
    PRIMARY KEY (id),
    CONSTRAINT uk_correct_answers_question UNIQUE (question_id),
    CONSTRAINT fk_correct_answers_question FOREIGN KEY (question_id)       REFERENCES questions       (id) ON DELETE CASCADE,
    CONSTRAINT fk_correct_answers_option   FOREIGN KEY (correct_option_id) REFERENCES question_options (id) ON DELETE SET NULL
);

-- ─── test_attempts ───────────────────────────────────────────────────────────
CREATE TABLE test_attempts (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    user_id             BIGINT      NOT NULL,
    test_id             BIGINT      NOT NULL,
    started_at          DATETIME(6) NOT NULL,
    completed_at        DATETIME(6),
    status              VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    total_score         INT,
    max_score           INT,
    time_spent_seconds  INT,
    version             INT,
    PRIMARY KEY (id),
    CONSTRAINT fk_attempts_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_attempts_test FOREIGN KEY (test_id) REFERENCES tests (id)
);
CREATE INDEX idx_attempts_user   ON test_attempts (user_id);
CREATE INDEX idx_attempts_test   ON test_attempts (test_id);
CREATE INDEX idx_attempts_status ON test_attempts (status);

-- ─── attempt_answers ─────────────────────────────────────────────────────────
CREATE TABLE attempt_answers (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    attempt_id         BIGINT       NOT NULL,
    question_id        BIGINT       NOT NULL,
    selected_option_id BIGINT,
    answer_text        VARCHAR(500),
    is_correct         BOOLEAN,
    answered_at        DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_attempt_answers UNIQUE (attempt_id, question_id),
    CONSTRAINT fk_attempt_answers_attempt  FOREIGN KEY (attempt_id)         REFERENCES test_attempts    (id) ON DELETE CASCADE,
    CONSTRAINT fk_attempt_answers_question FOREIGN KEY (question_id)        REFERENCES questions        (id),
    CONSTRAINT fk_attempt_answers_option   FOREIGN KEY (selected_option_id) REFERENCES question_options (id)
);
