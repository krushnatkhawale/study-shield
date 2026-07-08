# Quiz Design — Model, Schema & Data Plan

## Model Overview

A **flat question library** — each question is a standalone entity, not grouped into bundles. Quiz sessions are dynamically composed by filtering on class, subject, language, type, difficulty, and source.

### Question Type Categories

- **Single Choice** — pick exactly one correct answer from N options
- **Multiple Choice** — pick all correct answers from N options (2+ correct)
- **True/False** — dedicated 2-button layout on TV
- **FITB** — free text input, multiple valid answers accepted

### Image Support

- Question can have a text description + an optional image displayed together
- Options can be text OR image per-question (consistent within a question)
- Never mixed text+image in the same option

---

## Core Data Models

### Question

| Field | Type | Notes |
|-------|------|-------|
| id | String (PK) | UUID |
| resourceId | String | Stable resource identifier (e.g. `"nur_q01"`, `"c03_q15"`) |
| questionText | String | The question stem |
| questionImageUrl | String? | Optional image displayed with question |
| questionType | enum | `SINGLE_CHOICE`, `MULTIPLE_CHOICE`, `TRUE_FALSE`, `FITB` |
| options | List\<QuestionOption\> | Each has `id`, `text?`, `imageUrl?` |
| correctAnswers | List\<String\> | List of option IDs that are correct |
| explanation | String? | Shown after answering on TV |
| points | Int | Default 1 |
| difficulty | enum | `EASY`, `MEDIUM`, `HARD` |
| board | String | `"CBSE"`, `"ICSE"`, `"State_Maharashtra"`, or `"all"` |
| classes | List\<String\> | Grade levels (e.g., `["4", "5"]`) |
| subjects | List\<String\> | Validated against SubjectCatalog |
| languages | List\<String\> | e.g., `["English", "Hindi"]` |
| tags | List\<String\>? | Free-form keywords |
| source | enum | `BUILTIN`, `LIBRARY`, `USER_CREATED` |
| creatorAccountId | String? | `"builtin"` for system questions |
| creatorParentId | String? | |
| creatorKidId | String? | |
| isActive | Boolean | Soft-delete / disable |
| createdAt | Long | Timestamp |
| updatedAt | Long | Timestamp |

### QuestionOption (embedded)

| Field | Type | Notes |
|-------|------|-------|
| id | String | UUID, referenced by correctAnswers |
| text | String? | Null if image-only option |
| imageUrl | String? | Null if text-only option |

### SubjectCatalog

| Field | Type | Notes |
|-------|------|-------|
| id | String (PK) | |
| board | String | |
| className | String | |
| subject | String | |
| Unique | (board, className, subject) | |

Defines which subjects are available per board + grade level. Seeded for CBSE, ICSE, common state boards, Nursery–Class 10.

### QuizSession

| Field | Type | Notes |
|-------|------|-------|
| id | String (PK) | |
| kidId | String? | |
| studySessionId | String? | FK to study_sessions |
| board | String? | Filter criteria used |
| classes | List\<String\>? | |
| subjects | List\<String\>? | |
| languages | List\<String\>? | |
| questionTypes | List\<String\>? | |
| source | String? | |
| difficulty | String? | |
| questionCount | Int | |
| startedAt | Long | |
| completedAt | Long? | |
| correctCount | Int | |
| scorePercent | Double | |

### QuizSessionQuestion

| Field | Type | Notes |
|-------|------|-------|
| id | String (PK) | |
| sessionId | String (FK) | References QuizSession |
| questionId | String | |
| orderIndex | Int | |
| selectedAnswer | String? | |
| wasCorrect | Boolean? | |
| timeSpentMs | Long? | |

---

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Answer matching | **By option ID** | Survives shuffle. Works for text and image options uniformly |
| correctAnswers plural | **List\<String\>** | Single type covers SINGLE_CHOICE (1 item), MULTIPLE_CHOICE (2+), FITB (multiple valid texts) |
| Option format | **id + text? + imageUrl?** | Single structure for text-only, image-only, or mixed per-question |
| Question storage | **Flat table** | No bundles — dynamic filtering replaces pre-grouping |
| Subject validation | **Catalog table** | Drives UI dropdowns, ensures data consistency per board+grade |
| Creator tracking | **Hierarchical 3-level** | `accountId > parentId > kidId` with `"builtin"` sentinel for system questions |
| TRUE_FALSE | **Dedicated type** | Cleaner TV UI with two big buttons, better for young kids |
| FITB multiple answers | **List of valid texts** | Accepts synonyms/aliases (e.g. "H2O" and "water") |

---

## Data Flow

```
JSON assets  ──seed──▶  Room DB  ──query──▶  Filter by class/subject/type
                                                       │
                                                       ▼
                                              Shuffle + pick N
                                                       │
                                                       ▼
                                          QuizSessionDTO ──TCP──▶ TV renders
                                                       │
                                                       ▼
                                          Results recorded in QuizSession + QuizSessionQuestion
```

---

## Implementation Roadmap

1. **Room entities** — Question, SubjectCatalog, QuizSession, QuizSessionQuestion tables + DAOs
2. **Seed built-in data** — Load JSON files from assets into Room on first launch
3. **Quiz browser** — Filter questions by class/subject/type/difficulty, preview, select
4. **Quiz creator** — Add/edit questions with all metadata
5. **Session flow** — Compose dynamic quiz, send to TV, record results
6. **History & stats** — Per-kid performance dashboard
