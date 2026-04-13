# CLAUDE.md — OET Practice Test Application

## Project Overview

Build a full-stack OET (Occupational English Test) Practice Test Application with:
- **Backend:** Spring Boot 3.x (Java 17+), Spring Security with JWT, Spring Data JPA
- **Frontend:** Angular 18+, Angular Material, TailwindCSS
- **Purpose:** Admin creates OET Reading & Listening practice tests; Applicants take timed tests and get auto-graded results

---

## Architecture

```
oet-practice-app/
├── backend/          # Spring Boot REST API
└── frontend/         # Angular SPA
```

### Tech Stack

| Layer | Technology                                                      |
|-------|-----------------------------------------------------------------|
| Frontend | Angular 18+, Angular Material, TailwindCSS, RxJS                |
| Backend | Spring Boot 3.x, Java 17+                                       |
| Security | Spring Security + JWT (access + refresh tokens)                 |
| Database | MySQL                                                       |
| ORM | Spring Data JPA / Hibernate                                     |
| Build | Maven (backend), npm (frontend)                                 |
| File Storage | Local filesystem (`/uploads/audio/`) — configurable to S3 later |
| API Docs | SpringDoc OpenAPI (Swagger UI)                                  |

---

## Database Schema

### IMPORTANT: Create all tables with proper foreign keys, indexes, and constraints. Use Flyway for migrations.

### Table: `users`
```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'APPLICANT')),
    profession VARCHAR(100),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Table: `tests`
```sql
CREATE TABLE tests (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    sub_test_type VARCHAR(20) NOT NULL CHECK (sub_test_type IN ('READING', 'LISTENING')),
    total_time_limit_minutes INT NOT NULL,
    is_published BOOLEAN DEFAULT FALSE,
    created_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_tests_published ON tests(is_published);
CREATE INDEX idx_tests_created_by ON tests(created_by);
```

### Table: `test_parts`
```sql
CREATE TABLE test_parts (
    id BIGSERIAL PRIMARY KEY,
    test_id BIGINT NOT NULL REFERENCES tests(id) ON DELETE CASCADE,
    part_label VARCHAR(10) NOT NULL CHECK (part_label IN ('PART_A', 'PART_B', 'PART_C')),
    time_limit_minutes INT,
    instructions TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    UNIQUE(test_id, part_label)
);
```

### Table: `text_passages`
Holds reading passages (Text A, B, C, D for Reading Part A; individual passages for Part B; Text 1, Text 2 for Part C) and listening extract metadata.
```sql
CREATE TABLE text_passages (
    id BIGSERIAL PRIMARY KEY,
    test_part_id BIGINT NOT NULL REFERENCES test_parts(id) ON DELETE CASCADE,
    label VARCHAR(50) NOT NULL,
    content TEXT,
    audio_file_url VARCHAR(500),
    audio_duration_seconds INT,
    sort_order INT NOT NULL DEFAULT 0
);
```

### Table: `question_groups`
Groups questions that share instructions (e.g., "Questions 1-7: match to text A-D").
```sql
CREATE TABLE question_groups (
    id BIGSERIAL PRIMARY KEY,
    test_part_id BIGINT NOT NULL REFERENCES test_parts(id) ON DELETE CASCADE,
    passage_id BIGINT REFERENCES text_passages(id) ON DELETE SET NULL,
    title VARCHAR(255),
    instructions TEXT,
    question_type VARCHAR(30) NOT NULL CHECK (question_type IN (
        'TEXT_MATCHING',
        'SHORT_ANSWER',
        'GAP_FILL',
        'MCQ_3',
        'MCQ_4',
        'NOTE_COMPLETION'
    )),
    sort_order INT NOT NULL DEFAULT 0
);
```

#### Question Type Mapping to OET Parts:
- `TEXT_MATCHING` → Reading Part A Q1-7 (which text A/B/C/D contains the info)
- `SHORT_ANSWER` → Reading Part A Q8-14 (answer with word/short phrase)
- `GAP_FILL` → Reading Part A Q15-20 (complete the sentence with a word/phrase)
- `MCQ_3` → Reading Part B Q1-6 (3 options: A, B, C) AND Listening Part B & C
- `MCQ_4` → Reading Part C Q7-22 (4 options: A, B, C, D)
- `NOTE_COMPLETION` → Listening Part A Q1-24 (fill in blanks in clinical notes)

### Table: `questions`
```sql
CREATE TABLE questions (
    id BIGSERIAL PRIMARY KEY,
    question_group_id BIGINT NOT NULL REFERENCES question_groups(id) ON DELETE CASCADE,
    question_number INT NOT NULL,
    question_text TEXT NOT NULL,
    prefix_text TEXT,
    suffix_text TEXT,
    sort_order INT NOT NULL DEFAULT 0
);
```

- `question_text`: The main question (e.g., "procedures for delivering pain relief?")
- `prefix_text`: For GAP_FILL — text before blank (e.g., "Falling on an outstretched hand is a typical cause of a")
- `suffix_text`: For GAP_FILL — text after blank (e.g., "of the elbow.")
- For NOTE_COMPLETION: `question_text` contains the note template with a placeholder like `___` for the blank; `prefix_text` and `suffix_text` hold surrounding context

### Table: `question_options`
For MCQ questions only.
```sql
CREATE TABLE question_options (
    id BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    option_label CHAR(1) NOT NULL,
    option_text TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0
);
```

### Table: `correct_answers`
```sql
CREATE TABLE correct_answers (
    id BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    correct_option_id BIGINT REFERENCES question_options(id) ON DELETE SET NULL,
    correct_text VARCHAR(500),
    alternative_answers JSONB DEFAULT '[]'::jsonb
);
```

- `correct_option_id`: For MCQ — FK to the correct option
- `correct_text`: For SHORT_ANSWER, GAP_FILL, NOTE_COMPLETION, TEXT_MATCHING — the text answer (e.g., "pillows" or "B")
- `alternative_answers`: JSON array of acceptable alternatives, e.g., `["(a) (heavy) suitcase", "case", "suitcase"]`

### Table: `test_attempts`
```sql
CREATE TABLE test_attempts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    test_id BIGINT NOT NULL REFERENCES tests(id),
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS' CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'TIMED_OUT', 'ABANDONED')),
    total_score INT,
    max_score INT,
    time_spent_seconds INT
);
CREATE INDEX idx_attempts_user ON test_attempts(user_id);
CREATE INDEX idx_attempts_test ON test_attempts(test_id);
CREATE INDEX idx_attempts_status ON test_attempts(status);
```

### Table: `attempt_answers`
```sql
CREATE TABLE attempt_answers (
    id BIGSERIAL PRIMARY KEY,
    attempt_id BIGINT NOT NULL REFERENCES test_attempts(id) ON DELETE CASCADE,
    question_id BIGINT NOT NULL REFERENCES questions(id),
    selected_option_id BIGINT REFERENCES question_options(id),
    answer_text VARCHAR(500),
    is_correct BOOLEAN,
    answered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(attempt_id, question_id)
);
```

---

## Spring Boot Backend Structure

```
backend/
├── pom.xml
├── src/main/
│   ├── java/com/oetpractice/
│   │   ├── OetPracticeApplication.java
│   │   ├── config/
│   │   │   ├── SecurityConfig.java          # Spring Security with JWT
│   │   │   ├── JwtAuthenticationFilter.java
│   │   │   ├── JwtTokenProvider.java
│   │   │   ├── CorsConfig.java
│   │   │   └── OpenApiConfig.java           # Swagger config
│   │   ├── auth/
│   │   │   ├── dto/
│   │   │   │   ├── LoginRequest.java
│   │   │   │   ├── RegisterRequest.java
│   │   │   │   └── AuthResponse.java        # { accessToken, refreshToken, role }
│   │   │   ├── controller/AuthController.java
│   │   │   └── service/AuthService.java
│   │   ├── user/
│   │   │   ├── entity/User.java
│   │   │   ├── repository/UserRepository.java
│   │   │   ├── service/UserService.java
│   │   │   ├── dto/UserProfileDto.java
│   │   │   └── controller/UserController.java
│   │   ├── test/
│   │   │   ├── entity/
│   │   │   │   ├── Test.java
│   │   │   │   ├── TestPart.java
│   │   │   │   ├── TextPassage.java
│   │   │   │   ├── QuestionGroup.java
│   │   │   │   ├── Question.java
│   │   │   │   ├── QuestionOption.java
│   │   │   │   └── CorrectAnswer.java
│   │   │   ├── enums/
│   │   │   │   ├── SubTestType.java         # READING, LISTENING
│   │   │   │   ├── PartLabel.java           # PART_A, PART_B, PART_C
│   │   │   │   └── QuestionType.java        # TEXT_MATCHING, SHORT_ANSWER, GAP_FILL, MCQ_3, MCQ_4, NOTE_COMPLETION
│   │   │   ├── repository/
│   │   │   │   ├── TestRepository.java
│   │   │   │   ├── TestPartRepository.java
│   │   │   │   ├── TextPassageRepository.java
│   │   │   │   ├── QuestionGroupRepository.java
│   │   │   │   ├── QuestionRepository.java
│   │   │   │   ├── QuestionOptionRepository.java
│   │   │   │   └── CorrectAnswerRepository.java
│   │   │   ├── service/
│   │   │   │   ├── TestService.java
│   │   │   │   └── QuestionService.java
│   │   │   ├── dto/
│   │   │   │   ├── TestCreateRequest.java
│   │   │   │   ├── TestPartDto.java
│   │   │   │   ├── TextPassageDto.java
│   │   │   │   ├── QuestionGroupCreateRequest.java
│   │   │   │   ├── QuestionCreateRequest.java
│   │   │   │   ├── TestSummaryResponse.java
│   │   │   │   └── TestDetailResponse.java  # Full test with parts, passages, questions (no answers for applicant)
│   │   │   └── controller/
│   │   │       ├── AdminTestController.java  # @RequestMapping("/api/admin/tests")
│   │   │       └── TestController.java       # @RequestMapping("/api/tests") — applicant-facing
│   │   ├── attempt/
│   │   │   ├── entity/
│   │   │   │   ├── TestAttempt.java
│   │   │   │   └── AttemptAnswer.java
│   │   │   ├── repository/
│   │   │   │   ├── TestAttemptRepository.java
│   │   │   │   └── AttemptAnswerRepository.java
│   │   │   ├── service/
│   │   │   │   ├── AttemptService.java       # Start, save answer, submit, auto-grade
│   │   │   │   └── GradingService.java       # Auto-grading logic
│   │   │   ├── dto/
│   │   │   │   ├── StartAttemptResponse.java
│   │   │   │   ├── SaveAnswerRequest.java
│   │   │   │   ├── SubmitAttemptResponse.java
│   │   │   │   └── AttemptResultResponse.java
│   │   │   └── controller/AttemptController.java  # @RequestMapping("/api/attempts")
│   │   ├── media/
│   │   │   ├── service/MediaStorageService.java
│   │   │   └── controller/MediaController.java    # Upload & stream audio
│   │   └── report/
│   │       ├── service/ReportService.java
│   │       ├── dto/
│   │       │   ├── UserScoreReport.java
│   │       │   └── TestAnalyticsResponse.java
│   │       └── controller/ReportController.java
│   └── resources/
│       ├── application.yml
│       ├── application-dev.yml
│       └── db/migration/
│           └── V1__init_schema.sql           # Flyway migration with all tables above
```

---

## API Endpoints

### Auth
```
POST   /api/auth/register          # Register new user (role defaults to APPLICANT)
POST   /api/auth/login             # Login → returns JWT tokens
POST   /api/auth/refresh           # Refresh access token
```

### Admin — Test Management (requires ADMIN role)
```
POST   /api/admin/tests                              # Create test shell
GET    /api/admin/tests                              # List all tests (published + draft)
GET    /api/admin/tests/{testId}                     # Get full test with all nested data including answers
PUT    /api/admin/tests/{testId}                     # Update test metadata
DELETE /api/admin/tests/{testId}                     # Delete test (cascades)
PUT    /api/admin/tests/{testId}/publish             # Publish test
PUT    /api/admin/tests/{testId}/unpublish           # Unpublish

POST   /api/admin/tests/{testId}/parts               # Add part
PUT    /api/admin/parts/{partId}                      # Update part
DELETE /api/admin/parts/{partId}                      # Delete part

POST   /api/admin/parts/{partId}/passages             # Add passage/audio
PUT    /api/admin/passages/{passageId}                 # Update passage
DELETE /api/admin/passages/{passageId}                 # Delete passage

POST   /api/admin/parts/{partId}/question-groups       # Add question group
PUT    /api/admin/question-groups/{groupId}             # Update group
DELETE /api/admin/question-groups/{groupId}             # Delete group

POST   /api/admin/question-groups/{groupId}/questions   # Add question with options + correct answer
PUT    /api/admin/questions/{questionId}                 # Update question
DELETE /api/admin/questions/{questionId}                 # Delete question

POST   /api/admin/media/upload                          # Upload audio file
GET    /api/admin/media/{filename}                      # Stream audio

GET    /api/admin/reports/test/{testId}                  # Analytics for a test
GET    /api/admin/reports/user/{userId}                  # All attempts by a user
```

### Applicant — Test Taking (requires APPLICANT role)
```
GET    /api/tests                                   # List published tests
GET    /api/tests/{testId}/preview                  # Get test structure (no questions yet, just metadata)

POST   /api/attempts/start                          # Body: { testId } → creates attempt, returns full test content WITHOUT correct answers
GET    /api/attempts/{attemptId}                    # Resume an in-progress attempt
PUT    /api/attempts/{attemptId}/answer              # Body: { questionId, selectedOptionId?, answerText? } — auto-saves
POST   /api/attempts/{attemptId}/submit             # Submit attempt → triggers grading → returns score
GET    /api/attempts/{attemptId}/results            # Get detailed results with correct answers + user answers

GET    /api/attempts/my-history                     # List all past attempts for current user
```

---

## Auto-Grading Logic (GradingService.java)

```java
// CRITICAL: Implement this carefully

// For MCQ (MCQ_3, MCQ_4, TEXT_MATCHING when stored as option):
//   Compare attempt_answer.selected_option_id == correct_answer.correct_option_id

// For TEXT_MATCHING (when stored as text like "B"):
//   Normalize and compare: answer.trim().equalsIgnoreCase(correctAnswer.correctText.trim())

// For SHORT_ANSWER, GAP_FILL, NOTE_COMPLETION:
//   1. Normalize: trim, lowercase, remove leading articles (a, an, the) if present in alternatives
//   2. Check against correct_text AND each entry in alternative_answers JSON array
//   3. Use fuzzy matching threshold for minor typos (optional, configurable)
//   4. Example alternatives for Listening Q1: ["(a) (heavy) suitcase", "suitcase", "case", "a heavy suitcase", "heavy suitcase"]
//   5. Strip parenthetical optional words: "(a) (heavy) suitcase" means "suitcase", "a suitcase", "heavy suitcase", "a heavy suitcase" are all correct
//
// Scoring: 1 mark per correct answer. No negative marking.
```

---

## Angular Frontend Structure

```
frontend/
├── angular.json
├── package.json
├── tailwind.config.js
├── src/
│   ├── app/
│   │   ├── app.component.ts
│   │   ├── app.routes.ts
│   │   ├── core/
│   │   │   ├── guards/
│   │   │   │   ├── auth.guard.ts
│   │   │   │   └── role.guard.ts          # Separate admin vs applicant routes
│   │   │   ├── interceptors/
│   │   │   │   └── jwt.interceptor.ts     # Attach token to requests
│   │   │   ├── services/
│   │   │   │   ├── auth.service.ts
│   │   │   │   ├── test.service.ts
│   │   │   │   ├── attempt.service.ts
│   │   │   │   ├── admin-test.service.ts
│   │   │   │   └── media.service.ts
│   │   │   └── models/
│   │   │       ├── user.model.ts
│   │   │       ├── test.model.ts
│   │   │       ├── test-part.model.ts
│   │   │       ├── passage.model.ts
│   │   │       ├── question-group.model.ts
│   │   │       ├── question.model.ts
│   │   │       ├── question-option.model.ts
│   │   │       ├── attempt.model.ts
│   │   │       └── attempt-answer.model.ts
│   │   ├── shared/
│   │   │   ├── components/
│   │   │   │   ├── timer/timer.component.ts           # Countdown timer
│   │   │   │   ├── audio-player/audio-player.component.ts  # Single-play audio
│   │   │   │   ├── question-navigator/question-navigator.component.ts  # Shows answered/unanswered
│   │   │   │   └── confirm-dialog/confirm-dialog.component.ts
│   │   │   └── pipes/
│   │   │       └── time-format.pipe.ts
│   │   ├── auth/
│   │   │   ├── login/login.component.ts
│   │   │   └── register/register.component.ts
│   │   ├── admin/
│   │   │   ├── admin-layout/admin-layout.component.ts
│   │   │   ├── dashboard/admin-dashboard.component.ts
│   │   │   ├── test-list/test-list.component.ts
│   │   │   ├── test-builder/
│   │   │   │   ├── test-builder.component.ts           # Stepper/wizard: metadata → parts → passages → questions
│   │   │   │   ├── part-editor/part-editor.component.ts
│   │   │   │   ├── passage-editor/passage-editor.component.ts
│   │   │   │   ├── question-group-editor/question-group-editor.component.ts
│   │   │   │   └── question-editor/
│   │   │   │       ├── question-editor.component.ts     # Dynamic form based on question_type
│   │   │   │       ├── mcq-editor/mcq-editor.component.ts
│   │   │   │       ├── short-answer-editor/short-answer-editor.component.ts
│   │   │   │       ├── gap-fill-editor/gap-fill-editor.component.ts
│   │   │   │       └── note-completion-editor/note-completion-editor.component.ts
│   │   │   ├── audio-upload/audio-upload.component.ts
│   │   │   └── reports/
│   │   │       ├── test-analytics/test-analytics.component.ts
│   │   │       └── user-results/user-results.component.ts
│   │   └── applicant/
│   │       ├── applicant-layout/applicant-layout.component.ts
│   │       ├── dashboard/applicant-dashboard.component.ts
│   │       ├── test-catalog/test-catalog.component.ts    # Browse published tests
│   │       ├── test-player/
│   │       │   ├── test-player.component.ts              # Main container: loads test, manages timer, handles submit
│   │       │   ├── reading/
│   │       │   │   ├── reading-part-a/
│   │       │   │   │   └── reading-part-a.component.ts   # Split pane: tabbed texts (A/B/C/D) left, questions right
│   │       │   │   ├── reading-part-b/
│   │       │   │   │   └── reading-part-b.component.ts   # Short passage + 3-option MCQ per question
│   │       │   │   └── reading-part-c/
│   │       │   │       └── reading-part-c.component.ts   # Long passage + 4-option MCQ
│   │       │   └── listening/
│   │       │       ├── listening-part-a/
│   │       │       │   └── listening-part-a.component.ts # Audio player + note completion form
│   │       │       ├── listening-part-b/
│   │       │       │   └── listening-part-b.component.ts # Audio + 3-option MCQ
│   │       │       └── listening-part-c/
│   │       │           └── listening-part-c.component.ts # Audio + 3-option MCQ
│   │       ├── results/
│   │       │   ├── result-summary/result-summary.component.ts  # Score overview
│   │       │   └── result-detail/result-detail.component.ts    # Question-by-question review
│   │       └── history/attempt-history.component.ts
│   ├── environments/
│   │   ├── environment.ts
│   │   └── environment.prod.ts
│   └── styles.scss
```

---

## Angular Routing

```typescript
// app.routes.ts
export const routes: Routes = [
  { path: '', redirectTo: '/login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  {
    path: 'admin',
    component: AdminLayoutComponent,
    canActivate: [AuthGuard, RoleGuard],
    data: { role: 'ADMIN' },
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', component: AdminDashboardComponent },
      { path: 'tests', component: TestListComponent },
      { path: 'tests/new', component: TestBuilderComponent },
      { path: 'tests/:id/edit', component: TestBuilderComponent },
      { path: 'reports/test/:testId', component: TestAnalyticsComponent },
      { path: 'reports/user/:userId', component: UserResultsComponent },
    ]
  },
  {
    path: 'app',
    component: ApplicantLayoutComponent,
    canActivate: [AuthGuard, RoleGuard],
    data: { role: 'APPLICANT' },
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', component: ApplicantDashboardComponent },
      { path: 'tests', component: TestCatalogComponent },
      { path: 'test/:testId/take', component: TestPlayerComponent },
      { path: 'attempts/:attemptId/results', component: ResultDetailComponent },
      { path: 'history', component: AttemptHistoryComponent },
    ]
  }
];
```

---

## Key UI Behavior Rules

### Test Player (Applicant Taking a Test)

1. **Timer:** Always visible at top. Countdown from `time_limit_minutes`. When time runs out, auto-submit with whatever answers are saved.

2. **Auto-save:** Every answer change triggers `PUT /api/attempts/{id}/answer` so nothing is lost on disconnect/timeout.

3. **Question Navigator:** Sidebar showing all question numbers. Green = answered, gray = unanswered. Clickable to jump.

4. **Reading Part A Layout:**
    - Left panel (60% width): Tabbed view with tabs "Text A", "Text B", "Text C", "Text D" — each showing the passage content
    - Right panel (40% width): Scrollable question list
    - Questions 1-7: Dropdown or radio with options A, B, C, D
    - Questions 8-14: Text input field
    - Questions 15-20: Sentence with inline text input (prefix_text + `[input]` + suffix_text)

5. **Reading Part B Layout:**
    - Each question has its own passage shown above it
    - 3 radio options (A, B, C) below

6. **Reading Part C Layout:**
    - Long passage on left (scrollable)
    - Questions on right with 4 radio options (A, B, C, D)
    - Two text passages: Text 1 and Text 2, each with their question set

7. **Listening Part A Layout:**
    - Audio player at top — play button, progress bar. PLAYS ONCE ONLY (disable replay after completion to simulate real test). Optionally: allow admin to configure replay policy.
    - Below: Note completion form — clinical notes template with blank input fields inline

8. **Listening Parts B & C Layout:**
    - Each question has its own short audio extract
    - Audio player above the question
    - 3 radio options (A, B, C) below

9. **Submit Confirmation:** Modal dialog: "Are you sure? You have X unanswered questions." Show count.

10. **Results Page:** After submit, show:
    - Total score / max score and percentage
    - Per-part breakdown
    - Each question: your answer vs correct answer, with green/red highlighting
    - For reading: show the relevant passage excerpt
    - For listening: do NOT replay audio (keep it realistic)

### Admin Test Builder

1. **Stepper Wizard:**
    - Step 1: Test metadata (title, description, sub_test_type, time_limit)
    - Step 2: Add parts (auto-suggest PART_A, PART_B, PART_C based on sub_test_type)
    - Step 3: For each part, add passages/audio
    - Step 4: For each part, add question groups with questions
    - Step 5: Review & Publish

2. **Question Editor:** Dynamic form that changes based on `question_type`:
    - MCQ: question text + N options (with correct answer radio)
    - SHORT_ANSWER: question text + correct answer text + alternative answers (add/remove chips)
    - GAP_FILL: prefix text + correct answer + suffix text + alternatives
    - NOTE_COMPLETION: note template + correct answer + alternatives
    - TEXT_MATCHING: question text + correct text (A/B/C/D)

3. **Audio Upload:** Drag-and-drop zone. Accept MP3/WAV. Show duration after upload. Associate with a passage/extract.

4. **Preview Mode:** Admin can preview the test as an applicant would see it (without starting a real attempt).

---

## Security Rules

1. **All `/api/admin/**` endpoints:** Require JWT with role=ADMIN
2. **All `/api/tests/**` and `/api/attempts/**` endpoints:** Require JWT with role=APPLICANT
3. **`/api/auth/**` endpoints:** Public (no auth required)
4. **CORS:** Allow frontend origin (http://localhost:4200 in dev)
5. **Password:** BCrypt with strength 12
6. **JWT:** Access token expires in 1 hour; refresh token expires in 7 days
7. **Attempt Security:** User can only access their own attempts. Verified server-side.
8. **Answer Key Protection:** Correct answers are NEVER sent to the applicant until they submit the test.

---

## application.yml Configuration

```yaml
spring:
  datasource:
    url: jdbc:cj://localhost:3306/oet_practice
    username: root
    password: root
  jpa:
    hibernate:
      ddl-auto: validate  # Use Flyway for schema management
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
        format_sql: true
  flyway:
    enabled: true
    locations: classpath:db/migration
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB

app:
  jwt:
    secret: ${JWT_SECRET:your-256-bit-secret-key-change-in-production}
    access-token-expiration-ms: 3600000
    refresh-token-expiration-ms: 604800000
  upload:
    audio-dir: ./uploads/audio/

server:
  port: 8080
```

---

## Entity Relationship Rules

- `Test` 1:N `TestPart` (cascade ALL, orphanRemoval=true)
- `TestPart` 1:N `TextPassage` (cascade ALL, orphanRemoval=true)
- `TestPart` 1:N `QuestionGroup` (cascade ALL, orphanRemoval=true)
- `QuestionGroup` N:1 `TextPassage` (optional, nullable)
- `QuestionGroup` 1:N `Question` (cascade ALL, orphanRemoval=true)
- `Question` 1:N `QuestionOption` (cascade ALL, orphanRemoval=true)
- `Question` 1:1 `CorrectAnswer` (cascade ALL, orphanRemoval=true)
- `TestAttempt` N:1 `User`
- `TestAttempt` N:1 `Test`
- `TestAttempt` 1:N `AttemptAnswer` (cascade ALL, orphanRemoval=true)
- `AttemptAnswer` N:1 `Question`
- `AttemptAnswer` N:1 `QuestionOption` (optional)

---

## Seed Data

Create a Flyway migration `V2__seed_admin_user.sql`:
```sql
-- Default admin user (password: admin123 — BCrypt hash)
INSERT INTO users (email, password_hash, first_name, last_name, role)
VALUES ('admin@oetpractice.com', '$2a$12$LJ3m4ys3PzN1HsK1TBOiaeGFkmOdDJsMRiJq5D0JjSfGkxGNzJwvi', 'Admin', 'User', 'ADMIN');
```

---

## Commands to Run

### Backend
```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Frontend
```bash
cd frontend
npm install
ng serve --open
```

### Database
```bash
# Create the database
createdb oet_practice
# Flyway runs automatically on Spring Boot startup
```

---

## Development Workflow

1. Start with backend: entities → repositories → services → controllers
2. Write Flyway migration first, then create matching JPA entities
3. Use DTOs for all API requests/responses — never expose entities directly
4. Frontend: start with auth module, then admin test builder, then applicant test player
5. Test the grading logic thoroughly with unit tests — use the OET Sample Test 1 answer key as test data

---

## Important Implementation Notes

- Use `@JsonIgnore` on `CorrectAnswer` relationship when serializing questions for applicants
- Use separate DTOs for admin (includes answers) vs applicant (excludes answers)
- For the listening test audio player: use HTML5 `<audio>` element with `controlsList="nodownload"` and custom controls
- Implement WebSocket or polling for timer sync (optional enhancement)
- Add `@Transactional` on all service methods that modify data
- Use `@EntityGraph` or fetch joins to avoid N+1 queries when loading test with all nested data
- The alternative_answers JSONB field handling: use a JPA converter or Hibernate JSONB type
- For TEXT_MATCHING questions (Reading Part A Q1-7): the correct_text stores "A", "B", "C", or "D" and the applicant selects from a dropdown with those options
