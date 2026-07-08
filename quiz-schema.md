# Quiz Schema — PostgreSQL & MongoDB

## PostgreSQL

```sql
-- ============================================================
-- ENUMS
-- ============================================================

CREATE TYPE question_type AS ENUM (
    'SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE', 'FITB'
);

CREATE TYPE difficulty_level AS ENUM (
    'EASY', 'MEDIUM', 'HARD'
);

CREATE TYPE question_source AS ENUM (
    'BUILTIN', 'LIBRARY', 'USER_CREATED'
);

-- ============================================================
-- SUBJECT CATALOG
-- ============================================================

CREATE TABLE subject_catalog (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    board       VARCHAR(50)  NOT NULL,
    class_name  VARCHAR(20)  NOT NULL,
    subject     VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    UNIQUE (board, class_name, subject)
);

CREATE INDEX idx_subject_catalog_board ON subject_catalog(board);
CREATE INDEX idx_subject_catalog_class ON subject_catalog(class_name);

-- ============================================================
-- QUESTIONS
-- ============================================================

CREATE TABLE questions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resource_id         VARCHAR(20)     NOT NULL,   -- stable resource key: "nur_q01", "c03_q15"
    question_text       TEXT            NOT NULL,
    question_image_url  TEXT,
    question_type       question_type   NOT NULL,
    options             JSONB           NOT NULL,
        -- [{"id": "uuid", "text": "...", "imageUrl": null}, ...]
        -- At least one of text/imageUrl per option.
    correct_answers     JSONB           NOT NULL,
        -- ["option_id_1"] for single choice
        -- ["option_id_1", "option_id_3"] for multiple choice
        -- ["water", "H2O"] for FITB
    explanation         TEXT,
    points              INTEGER         NOT NULL DEFAULT 1,
    difficulty          difficulty_level NOT NULL,
    board               VARCHAR(50)     NOT NULL,
    classes             JSONB           NOT NULL,
        -- ["4", "5"]
    subjects            JSONB           NOT NULL,
        -- ["Mathematics", "Science"]
    languages           JSONB           NOT NULL,
        -- ["English", "Hindi"]
    tags                JSONB,
        -- ["fractions", "addition"]
    source              question_source NOT NULL,
    creator_account_id  VARCHAR(36),
    creator_parent_id   VARCHAR(36),
    creator_kid_id      VARCHAR(36),
    is_active           BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          BIGINT          NOT NULL,
    updated_at          BIGINT          NOT NULL
);

CREATE INDEX idx_questions_type       ON questions(question_type);
CREATE INDEX idx_questions_difficulty ON questions(difficulty);
CREATE INDEX idx_questions_board      ON questions(board);
CREATE INDEX idx_questions_source     ON questions(source);
CREATE INDEX idx_questions_creator    ON questions(creator_account_id);

-- GIN indexes for JSONB array containment queries
CREATE INDEX idx_questions_classes    ON questions USING GIN (classes);
CREATE INDEX idx_questions_subjects   ON questions USING GIN (subjects);
CREATE INDEX idx_questions_languages  ON questions USING GIN (languages);
CREATE INDEX idx_questions_tags       ON questions USING GIN (tags);

-- ============================================================
-- QUIZ SESSIONS
-- ============================================================

CREATE TABLE quiz_sessions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kid_id              VARCHAR(36),
    study_session_id    VARCHAR(36),
    board               VARCHAR(50),
    classes             JSONB,
    subjects            JSONB,
    languages           JSONB,
    question_types      JSONB,
    source              VARCHAR(20),
    difficulty          VARCHAR(10),
    question_count      INTEGER         NOT NULL,
    started_at          BIGINT          NOT NULL,
    completed_at        BIGINT,
    correct_count       INTEGER         NOT NULL DEFAULT 0,
    score_percent       DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_quiz_sessions_kid   ON quiz_sessions(kid_id);
CREATE INDEX idx_quiz_sessions_study ON quiz_sessions(study_session_id);

-- ============================================================
-- QUIZ SESSION QUESTIONS (individual answers)
-- ============================================================

CREATE TABLE quiz_session_questions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      UUID            NOT NULL REFERENCES quiz_sessions(id) ON DELETE CASCADE,
    question_id     VARCHAR(36)     NOT NULL,
    order_index     INTEGER         NOT NULL,
    selected_answer TEXT,
    was_correct     BOOLEAN,
    time_spent_ms   BIGINT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_qsq_session    ON quiz_session_questions(session_id);
CREATE INDEX idx_qsq_question   ON quiz_session_questions(question_id);
```

---

## MongoDB

### Questions Collection

```javascript
// Schema validation rules (MongoDB 5.0+)
db.createCollection("questions", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["resourceId", "questionText", "questionType", "options", "correctAnswers",
                 "difficulty", "board", "classes", "subjects", "languages",
                 "source", "createdAt", "updatedAt"],
      properties:
        _id:               { bsonType: "objectId" },
        resourceId:        { bsonType: "string" },
        questionText:      { bsonType: "string" },
        questionImageUrl:  { bsonType: ["string", "null"] },
        questionType:      { enum: ["SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "FITB"] },
        options: {
          bsonType: "array",
          minItems: 1,
          items: {
            bsonType: "object",
            required: ["id"],
            properties: {
              id:       { bsonType: "string" },
              text:     { bsonType: ["string", "null"] },
              imageUrl: { bsonType: ["string", "null"] }
            }
          }
        },
        correctAnswers: {
          bsonType: "array",
          minItems: 1,
          items: { bsonType: "string" }
        },
        explanation:       { bsonType: ["string", "null"] },
        points:            { bsonType: "int", minimum: 1 },
        difficulty:        { enum: ["EASY", "MEDIUM", "HARD"] },
        board:             { bsonType: "string" },
        classes:           { bsonType: "array", items: { bsonType: "string" } },
        subjects:          { bsonType: "array", items: { bsonType: "string" } },
        languages:         { bsonType: "array", items: { bsonType: "string" } },
        tags:              { bsonType: ["array", "null"], items: { bsonType: "string" } },
        source:            { enum: ["BUILTIN", "LIBRARY", "USER_CREATED"] },
        creatorAccountId:  { bsonType: ["string", "null"] },
        creatorParentId:   { bsonType: ["string", "null"] },
        creatorKidId:      { bsonType: ["string", "null"] },
        isActive:          { bsonType: "bool" },
        createdAt:         { bsonType: "long" },
        updatedAt:         { bsonType: "long" }
      }
    }
  }
});

// Indexes
db.questions.createIndex({ resourceId: 1 }, { unique: true });
db.questions.createIndex({ questionType: 1 });
db.questions.createIndex({ difficulty: 1 });
db.questions.createIndex({ board: 1 });
db.questions.createIndex({ source: 1 });
db.questions.createIndex({ creatorAccountId: 1 });
db.questions.createIndex({ classes: 1 });        // Multikey index
db.questions.createIndex({ subjects: 1 });       // Multikey index
db.questions.createIndex({ languages: 1 });      // Multikey index
db.questions.createIndex({ tags: 1 });           // Multikey index
db.questions.createIndex({
  classes: 1, subjects: 1, questionType: 1, difficulty: 1
}); // Compound index for common query pattern
```

### Subject Catalog Collection

```javascript
db.createCollection("subject_catalog", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["board", "className", "subject"],
      properties: {
        board:      { bsonType: "string" },
        className:  { bsonType: "string" },
        subject:    { bsonType: "string" },
        createdAt:  { bsonType: "date" }
      }
    }
  }
});

db.subject_catalog.createIndex({ board: 1, className: 1, subject: 1 }, { unique: true });
db.subject_catalog.createIndex({ board: 1 });
db.subject_catalog.createIndex({ className: 1 });
```

### Quiz Sessions Collection

```javascript
db.createCollection("quiz_sessions", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["questionCount", "startedAt"],
      properties: {
        kidId:            { bsonType: ["string", "null"] },
        studySessionId:   { bsonType: ["string", "null"] },
        board:            { bsonType: ["string", "null"] },
        classes:          { bsonType: ["array", "null"], items: { bsonType: "string" } },
        subjects:         { bsonType: ["array", "null"], items: { bsonType: "string" } },
        languages:        { bsonType: ["array", "null"], items: { bsonType: "string" } },
        questionTypes:    { bsonType: ["array", "null"], items: { bsonType: "string" } },
        source:           { bsonType: ["string", "null"] },
        difficulty:       { bsonType: ["string", "null"] },
        questionCount:    { bsonType: "int" },
        startedAt:        { bsonType: "long" },
        completedAt:      { bsonType: ["long", "null"] },
        correctCount:     { bsonType: "int" },
        scorePercent:     { bsonType: "double" },
        // Embedded answers for atomicity
        answers: {
          bsonType: "array",
          items: {
            bsonType: "object",
            required: ["questionId", "orderIndex"],
            properties: {
              questionId:    { bsonType: "string" },
              orderIndex:    { bsonType: "int" },
              selectedAnswer:{ bsonType: ["string", "null"] },
              wasCorrect:    { bsonType: ["bool", "null"] },
              timeSpentMs:   { bsonType: ["long", "null"] }
            }
          }
        }
      }
    }
  }
});

db.quiz_sessions.createIndex({ kidId: 1 });
db.quiz_sessions.createIndex({ studySessionId: 1 });
db.quiz_sessions.createIndex({ startedAt: -1 });
```

### Query Examples

```javascript
// Filter questions for a quiz session
db.questions.find({
  classes: { $in: ["4", "5"] },
  subjects: "Mathematics",
  questionType: { $in: ["SINGLE_CHOICE", "MULTIPLE_CHOICE"] },
  difficulty: "EASY",
  board: { $in: ["CBSE", "all"] },
  isActive: true
})
  .sort({ createdAt: -1 })
  .limit(50);

// Random selection (pick 10 from the filtered set)
db.questions.aggregate([
  { $match: { classes: "4", subjects: "Science", isActive: true } },
  { $sample: { size: 10 } }
]);

// Leaderboard: average score per kid
db.quiz_sessions.aggregate([
  { $group: {
    _id: "$kidId",
    avgScore: { $avg: "$scorePercent" },
    totalSessions: { $sum: 1 }
  }},
  { $sort: { avgScore: -1 } }
]);

// Subject-wise performance
db.quiz_sessions.aggregate([
  { $unwind: "$subjects" },
  { $group: {
    _id: { kidId: "$kidId", subject: "$subjects" },
    avgScore: { $avg: "$scorePercent" },
    count: { $sum: 1 }
  }}
]);
```
