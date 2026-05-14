# Revision App — Full Design Specification
> Target: Java desktop application (JavaFX recommended)  
> Source: CS-375 Logic Revision HTML prototype

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Data Directory Layout](#2-data-directory-layout)
3. [Module Format](#3-module-format)
4. [JSS — JSON Style Sheet](#4-jss--json-style-sheet)
5. [Progress & Persistence Format](#5-progress--persistence-format)
6. [Settings Format](#6-settings-format)
7. [Study Modes](#7-study-modes)
8. [Spaced Repetition](#8-spaced-repetition)
9. [Module Management](#9-module-management)
10. [Theme Management](#10-theme-management)
11. [Keyboard Shortcuts](#11-keyboard-shortcuts)
12. [Accessibility](#12-accessibility)
13. [Window State](#13-window-state)
14. [Export & Backup](#14-export--backup)

---

## 1. Architecture Overview

```
RevisionApp/
├── core/
│   ├── ModuleManager       — load, validate, save, import, export modules
│   ├── ProgressStore       — read/write per-module progress JSON
│   ├── SettingsStore       — read/write global settings JSON
│   ├── ThemeEngine         — parse JSS, resolve CSS vars, apply to JavaFX scene
│   ├── SpacedRepetition    — SM-2 scheduling logic
│   └── WindowStateStore    — persist window position/size/maximised
├── model/
│   ├── Module              — POJO: metadata + categories + definitions + flashcards + quiz
│   ├── Definition          — term, definition, topic key
│   ├── Flashcard           — front, back, topic key
│   ├── QuizQuestion        — question, options[], correctIndex, explanation, topic key
│   ├── Category            — key, label, color (bg/border/text)
│   ├── Theme               — parsed JSS
│   └── SRCard              — card id, interval, easeFactor, due date (SM-2 state)
└── ui/
    ├── HomeView
    ├── FlashcardView
    ├── QuizView
    ├── LCWCView
    ├── DefinitionsView
    ├── ModuleEditorView
    ├── ThemeEditorView
    └── SettingsView
```

### Recommended stack
| Concern | Choice |
|---|---|
| UI framework | JavaFX 21+ |
| JSON | Jackson (or Gson) |
| Persistence | Plain JSON files in `~/.revisionapp/` |
| Styling | JSS → JavaFX CSS (generated at runtime) |
| Build | Maven or Gradle |

---

## 2. Data Directory Layout

All persistent data lives under a single root, configurable but defaulting to the user's home directory.

```
~/.revisionapp/
├── settings.json
├── window.json
├── progress/
│   └── <module-id>.json      # one file per module
├── modules/
│   ├── cs375-logic.json      # bundled modules (shipped with app)
│   └── my-custom-module.json # user-created
└── themes/
    ├── dark.jss.json         # bundled
    ├── grey.jss.json         # bundled
    ├── light.jss.json        # bundled
    └── my-theme.jss.json     # user-created
```

Bundled modules and themes are copied into this directory on first launch so the user can freely edit them. The originals inside the JAR serve as factory reset fallbacks.

---

## 3. Module Format

A module is a single `.json` file. The `id` field must be unique and URL-safe (lowercase, hyphens only).

```json
{
  "id": "cs375-logic",
  "name": "CS-375 Logic, Proof & Computation",
  "institution": "Swansea University",
  "version": "1.0.0",
  "description": "Propositional and predicate logic, SAT, resolution, and natural deduction.",

  "categories": [
    {
      "key": "found",
      "label": "Foundations",
      "color": {
        "bg":     "rgba(99,102,241,0.15)",
        "border": "#6366f1",
        "text":   "#a5b4fc"
      }
    },
    {
      "key":    "sem",
      "label":  "Semantics & Satisfiability",
      "color": {
        "bg":     "rgba(20,184,166,0.15)",
        "border": "#14b8a6",
        "text":   "#5eead4"
      }
    }
  ],

  "definitions": [
    {
      "topic": "found",
      "term":  "Atom",
      "def":   "A basic propositional symbol with no internal logical structure."
    }
  ],

  "flashcards": [
    {
      "topic": "found",
      "q":     "What is a tautology?",
      "a":     "A formula that is true under every assignment.",
      "tags":  ["validity", "semantics"]
    }
  ],

  "quiz": [
    {
      "topic":       "sat",
      "question":    "What is the time complexity class of 3-SAT?",
      "options":     ["P", "NP-complete", "PSPACE-complete", "Undecidable"],
      "answer":      1,
      "explanation": "3-SAT is the canonical NP-complete problem (Cook–Levin theorem)."
    }
  ],

  "quickReference": [
    { "key": "Validity / Unsatisfiability", "value": "F is valid iff ¬F is unsatisfiable" },
    { "key": "SAT",                         "value": "Decidable and NP-complete" }
  ],

  "examTraps": [
    "Valid means true in all models; satisfiable means true in at least one model."
  ],

  "lcwcExclude": [
    "What is a tautology?"
  ]
}
```

### Validation rules

| Field | Rule |
|---|---|
| `id` | Required, unique, matches `[a-z0-9-]+` |
| `version` | Semver string, used for migration |
| `categories[].key` | Must be unique within the module |
| `definitions[].topic` | Must match a `categories[].key` |
| `flashcards[].topic` | Must match a `categories[].key` |
| `quiz[].answer` | Integer index into `options[]`, 0-based |
| `quiz[].options` | 2–6 items |

On load, the app validates and surfaces errors in the Module Manager UI rather than silently failing.

---

## 4. JSS — JSON Style Sheet

JSS is the theming format. A `.jss.json` file fully describes every visual property of the app. The `ThemeEngine` reads it, generates a JavaFX CSS string, and injects it into the scene graph at runtime. No restart required.

### 4.1 Full JSS Schema

```json
{
  "meta": {
    "name":    "Dark",
    "author":  "RevisionApp",
    "version": "1.0"
  },

  "colors": {
    "background":       "#030712",
    "surface":          "#0a0f1e",
    "surface-raised":   "#111827",
    "border":           "#1e293b",
    "border-focus":     "#6366f1",

    "text-primary":     "#e0e7ff",
    "text-secondary":   "#9ca3af",
    "text-muted":       "#4b5563",
    "text-inverse":     "#030712",

    "accent":           "#6366f1",
    "accent-hover":     "#4f46e5",
    "accent-subtle":    "rgba(99,102,241,0.15)",

    "success":          "#22c55e",
    "success-subtle":   "rgba(34,197,94,0.12)",
    "danger":           "#ef4444",
    "danger-subtle":    "rgba(239,68,68,0.12)",
    "warning":          "#f59e0b",
    "warning-subtle":   "rgba(245,158,11,0.12)",

    "scrollbar-track":  "#030712",
    "scrollbar-thumb":  "#1f2937",

    "badge-bg":         "#052e16",
    "badge-border":     "#22c55e",
    "badge-text":       "#4ade80",

    "overlay":          "rgba(0,0,0,0.6)"
  },

  "typography": {
    "font-body":        "Crimson Pro",
    "font-body-fallback": ["Georgia", "serif"],
    "font-mono":        "Space Mono",
    "font-mono-fallback": ["Courier New", "monospace"],
    "font-dyslexic":    null,

    "size-xs":   10,
    "size-sm":   11,
    "size-base": 13,
    "size-md":   14,
    "size-lg":   16,
    "size-xl":   20,
    "size-2xl":  26,
    "size-3xl":  32,

    "weight-normal": 400,
    "weight-bold":   700,

    "line-height-tight":  1.3,
    "line-height-normal": 1.6,
    "line-height-loose":  1.9,

    "letter-spacing-tight":  "-0.01em",
    "letter-spacing-normal": "0em",
    "letter-spacing-wide":   "0.1em",
    "letter-spacing-wider":  "0.2em"
  },

  "shape": {
    "radius-sm":   6,
    "radius-md":   10,
    "radius-lg":   14,
    "radius-pill": 99,

    "border-width":       1,
    "border-width-thick": 1.5
  },

  "spacing": {
    "xs":  4,
    "sm":  8,
    "md":  12,
    "lg":  16,
    "xl":  20,
    "2xl": 28,
    "3xl": 40
  },

  "motion": {
    "duration-fast":   "0.1s",
    "duration-normal": "0.15s",
    "duration-slow":   "0.3s",
    "easing":          "ease-in-out"
  },

  "components": {
    "card": {
      "background":    "@surface",
      "border-color":  "@border",
      "border-radius": "@radius-lg",
      "padding":       "@lg"
    },
    "card-hover": {
      "background":   "@surface-raised",
      "border-color": "@accent"
    },
    "button-primary": {
      "background":    "@accent",
      "text":          "@text-primary",
      "border-radius": "@radius-md",
      "padding-x":     "@lg",
      "padding-y":     "@sm"
    },
    "button-ghost": {
      "background":    "transparent",
      "text":          "@text-secondary",
      "border-color":  "@border",
      "border-radius": "@radius-md"
    },
    "input": {
      "background":    "@surface",
      "border-color":  "@border",
      "text":          "@text-primary",
      "border-radius": "@radius-md",
      "font":          "@font-mono"
    },
    "input-focus": {
      "border-color": "@border-focus"
    },
    "tag-active": {
      "background":   "@accent-subtle",
      "border-color": "@accent",
      "text":         "@text-primary"
    },
    "tag-inactive": {
      "background":   "transparent",
      "border-color": "@border",
      "text":         "@text-muted"
    },
    "flashcard": {
      "background":    "@surface",
      "border-color":  "@border",
      "border-radius": "@radius-lg"
    },
    "flashcard-flipped": {
      "border-color": "@accent"
    },
    "progress-bar": {
      "track":  "@surface-raised",
      "fill":   "@accent",
      "radius": "@radius-pill"
    },
    "lcwc-input": {
      "background":    "@surface-raised",
      "border-color":  "@border",
      "border-radius": "@radius-md"
    },
    "lcwc-correct": {
      "border-color": "@success",
      "background":   "@success-subtle"
    },
    "lcwc-wrong": {
      "border-color": "@danger",
      "background":   "@danger-subtle"
    },
    "quiz-option": {
      "background":    "@surface",
      "border-color":  "@border",
      "border-radius": "@radius-md"
    },
    "quiz-correct": {
      "background":   "@success-subtle",
      "border-color": "@success"
    },
    "quiz-wrong": {
      "background":   "@danger-subtle",
      "border-color": "@danger"
    },
    "sidebar": {
      "background":   "@surface",
      "border-color": "@border"
    }
  }
}
```

### 4.2 Reference resolution

Values beginning with `@` are references to other tokens in the same file.

Resolution order: `components` → `spacing` → `shape` → `typography` → `colors`.

Circular references are detected and reported as a load error.

```
"@surface"    resolves to   colors.surface
"@radius-lg"  resolves to   shape.radius-lg
"@lg"         resolves to   spacing.lg
```

### 4.3 ThemeEngine contract

The `ThemeEngine` must:

1. Parse the JSS file and resolve all `@` references.
2. Generate a valid JavaFX CSS string from resolved values.
3. Inject into `scene.getStylesheets()` (clear previous theme first).
4. Expose a `getToken(String key): String` method for any component that needs to read values programmatically (e.g., canvas-drawn elements).
5. Fire a `ThemeChangedEvent` that all views subscribe to for programmatic repaints.

### 4.4 Bundled themes

| File | Description |
|---|---|
| `dark.jss.json` | Default. Deep navy/charcoal, indigo accents. Mirrors the prototype. |
| `grey.jss.json` | Mid-grey surfaces, reduced contrast. Good for bright rooms. |
| `light.jss.json` | White surfaces, dark text, same accent palette. |

---

## 5. Progress & Persistence Format

One file per module, stored at `~/.revisionapp/progress/<module-id>.json`.

```json
{
  "moduleId":  "cs375-logic",
  "moduleVersion": "1.0.0",
  "lastStudied": "2026-05-12T14:30:00Z",
  "streak": {
    "current": 3,
    "longest": 7,
    "lastDate": "2026-05-12"
  },
  "cards": {
    "def::Atom": {
      "type":        "definition",
      "got":         true,
      "confidence":  4,
      "interval":    6,
      "easeFactor":  2.6,
      "due":         "2026-05-18",
      "reviewCount": 5,
      "lastReview":  "2026-05-12T14:30:00Z"
    },
    "fc::What is a tautology?": {
      "type":        "flashcard",
      "got":         false,
      "confidence":  2,
      "interval":    1,
      "easeFactor":  2.1,
      "due":         "2026-05-13",
      "reviewCount": 3,
      "lastReview":  "2026-05-12T14:30:00Z"
    }
  },
  "quizHistory": [
    {
      "timestamp":  "2026-05-12T14:00:00Z",
      "score":      7,
      "total":      10,
      "topics":     ["found", "sem"],
      "wrongIds":   ["quiz::3-SAT complexity"]
    }
  ]
}
```

### Migration

When a module's `version` changes, the app compares old and new card IDs:

- Cards present in both: carry over SM-2 state.
- Cards added in new version: initialise fresh.
- Cards removed in new version: archive (keep in progress file under `"archived": {}` but exclude from study sessions).

---

## 6. Settings Format

`~/.revisionapp/settings.json`

```json
{
  "activeTheme":      "dark",
  "activeModule":     "cs375-logic",
  "fontSize":         "medium",
  "fontFamily":       "default",
  "studyDailyGoal":   20,

  "lcwc": {
    "lookTimeSec":    5,
    "coverTimeSec":   0,
    "writeTimeSec":   0,
    "revealDelaySec": 0,
    "autoAdvance":    false
  },

  "flashcards": {
    "autoAdvanceSec":    0,
    "showConfidenceBar": true
  },

  "quiz": {
    "showExplanationOnWrong": true,
    "showWrongReviewAtEnd":   true
  },

  "spaced": {
    "enabled":        true,
    "dailyNewCards":  10
  },

  "accessibility": {
    "reduceMotion":    false,
    "highContrast":    false,
    "dyslexicFont":    false
  }
}
```

### Font size scale

| Setting value | Base px |
|---|---|
| `"small"` | 11 |
| `"medium"` | 13 (default) |
| `"large"` | 15 |
| `"xlarge"` | 17 |

The font size setting multiplies all `typography.size-*` tokens in the active theme by a scale factor, so the JSS values remain relative.

---

## 7. Study Modes

### 7.1 Flashcards

- Cards shuffled on session start.
- Flip animation on Space or click (respects `reduceMotion` setting — crossfade instead of 3D flip if enabled).
- **Confidence rating** after flip: 1–5 stars displayed as buttons. Rating feeds directly into SM-2 (see §8).
- Progress bar at top showing `reviewed / total` for this session.
- Auto-advance: if `autoAdvanceSec > 0`, card flips and advances automatically after the configured delay.
- "Restart session" button resets shuffle without clearing SM-2 state.
- **Keyboard:** Space = flip, 1–5 = rate confidence, Left/Right = prev/next (without rating).

### 7.2 Quiz Mode

- Questions shuffled on session start.
- MCQ: 2–6 options. On selection, immediately show correct/wrong state and explanation.
- Option to skip a question (moves it to the end of the queue).
- Session summary screen showing: score, percentage, time taken, wrong answers list.
- "Review wrong answers" from summary re-queues only the missed questions.
- **Keyboard:** 1–6 = select option, Enter = confirm, Right = next after answer shown.

### 7.3 Look Cover Write Check (LCWC)

- Items are drawn from definitions and flashcards (excluding items in `lcwcExclude`).
- Session flow per item:
  1. **Look** — term and definition shown for `lookTimeSec` (progress bar countdown). Can be skipped.
  2. **Cover** — definition hidden. User writes from memory in a text area for `writeTimeSec` (0 = unlimited).
  3. **Check** — user clicks "Reveal" or timer expires. Definition shown alongside the user's answer.
  4. **Mark** — user marks "Got it" or "Missed". Feeds SM-2.
- Items marked "Got it" N times in a row (configurable, default 3) are considered mastered for the session.
- Mastered badge on home screen shows mastered count / total.
- **Keyboard:** Enter = advance through LCWC phases, G = got it, M = missed.

### 7.4 Definitions Browser

- Searchable. Search matches term and definition text.
- Grouped by category. Each group can be collapsed.
- Each definition is an expandable accordion row.
- Filter bar to show only selected categories (mirrors home topic filter).
- **Export to PDF** button: generates a clean two-column revision sheet from visible definitions (see §14).

### 7.5 Weak Spots Mode

A filtered session available from the home screen as a fifth mode.

- Aggregates all cards where `confidence ≤ 2` or `got = false` in the last N sessions (configurable).
- Can be run as Flashcards, LCWC, or Quiz against this filtered set.

---

## 8. Spaced Repetition

Implements **SM-2** (SuperMemo algorithm).

### 8.1 Card states

| State | Condition |
|---|---|
| New | Never reviewed |
| Learning | `reviewCount < 3` |
| Review | `reviewCount ≥ 3`, due on a future date |
| Overdue | Due date in the past |

### 8.2 Rating → SM-2 quality mapping

The user rates 1–5 stars. These map to SM-2 quality values (0–5):

| Stars | SM-2 q | Meaning |
|---|---|---|
| 1 | 1 | Complete blackout |
| 2 | 2 | Wrong but remembered on seeing answer |
| 3 | 3 | Wrong but easy to recall |
| 4 | 4 | Correct with hesitation |
| 5 | 5 | Correct, immediate |

LCWC "Got it" maps to q=4, "Missed" maps to q=1.

### 8.3 SM-2 update formula

```
if q < 3:
    interval    = 1
    easeFactor  = max(1.3, easeFactor - 0.2)
else:
    if reviewCount == 0:  interval = 1
    elif reviewCount == 1: interval = 6
    else:                  interval = round(interval * easeFactor)
    easeFactor = easeFactor + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))
    easeFactor = max(1.3, easeFactor)

due = today + interval days
reviewCount += 1
```

Initial `easeFactor = 2.5`.

### 8.4 Daily session

If `spaced.enabled = true`, the home screen shows a "Due today" badge with the count of cards whose `due ≤ today`. The Flashcard and LCWC modes offer an optional "Study due cards" toggle that restricts the session to overdue and due-today cards before showing new ones.

---

## 9. Module Management

### 9.1 Module list view

Accessed from the sidebar/menu. Displays all installed modules with:

- Name, institution, version, card counts.
- Active indicator.
- Actions: Switch to, Edit, Duplicate, Export, Delete.

### 9.2 In-app editor

The module editor provides a tabbed interface:

| Tab | Contents |
|---|---|
| Info | Name, institution, description, version (edit, auto-increment patch on save) |
| Categories | Add/rename/reorder/delete categories. Colour picker for bg/border/text. |
| Definitions | Table: topic, term, definition. Add, edit, delete rows. |
| Flashcards | Table: topic, front, back, tags. Add, edit, delete rows. |
| Quiz | List of questions. Each expands to: question text, options editor, correct answer selector, explanation. |
| Quick Ref | Key-value list, editable. |
| Exam Traps | Plain text list, editable. |
| LCWC Exclude | Multi-select from flashcard fronts. |

All edits are held in memory until "Save". Saving writes to the module JSON and triggers a progress migration check (§5).

### 9.3 Import

- **From file:** JSON file picker. Validates against module schema. If `id` already exists, prompt: overwrite, install as copy (new id), or cancel.
- **From URL:** Paste a URL pointing to a raw module JSON. App fetches and validates it.

### 9.4 Export

Exports the module JSON file. Does not include progress data.

### 9.5 Progress export/import (separate from module)

See §14.

---

## 10. Theme Management

### 10.1 Theme list

Accessed from Settings → Themes. Shows all `.jss.json` files in the themes directory.

- Live preview thumbnail (small colour swatches from the JSS tokens).
- Apply button (no restart).
- Edit, Duplicate, Delete.

### 10.2 In-app JSS editor

A structured form — not a raw text editor — that maps directly to the JSS schema:

| Section | Controls |
|---|---|
| Meta | Name, author text inputs |
| Colors | Colour picker for each named token |
| Typography | Font pickers, numeric size inputs, dropdowns for weight/line-height |
| Shape | Numeric inputs for radii and border widths |
| Spacing | Numeric inputs for each step |
| Motion | Duration inputs, easing dropdown |
| Components | Per-component subsections; each field shows the resolved value next to the `@ref` input |

A **live preview panel** to the right of the editor shows representative UI elements (a card, a button, a flashcard, a tag, an input) updating in real time as tokens change.

An **"Edit raw JSON"** toggle exposes the raw JSS text in a monospace editor with syntax highlighting for users who prefer it. Switches back to the form view on valid JSON.

### 10.3 Import / Export

- Import: file picker for `.jss.json`. Validates against schema.
- Export: saves current theme as a `.jss.json` file to a user-chosen location.

---

## 11. Keyboard Shortcuts

### Global

| Shortcut | Action |
|---|---|
| `Ctrl+,` | Open Settings |
| `Ctrl+M` | Open Module Manager |
| `Ctrl+T` | Cycle themes (dark → grey → light → dark) |
| `Ctrl+W` | Return to Home |
| `?` | Show shortcut cheatsheet overlay |
| `Escape` | Close overlay / return to home |

### Flashcards

| Shortcut | Action |
|---|---|
| `Space` | Flip card |
| `1–5` | Rate confidence (after flip) |
| `Left` | Previous card |
| `Right` | Next card (no rating) |
| `R` | Restart session |

### Quiz

| Shortcut | Action |
|---|---|
| `1–6` | Select option |
| `Enter` | Confirm selection / advance |
| `S` | Skip question |
| `Right` | Next question (after answer shown) |

### LCWC

| Shortcut | Action |
|---|---|
| `Enter` | Advance phase (Look → Write → Check) |
| `G` | Mark "Got it" |
| `M` | Mark "Missed" |
| `S` | Skip current item |

### Definitions

| Shortcut | Action |
|---|---|
| `Ctrl+F` | Focus search bar |
| `Up/Down` | Navigate accordion items |
| `Enter` | Expand/collapse focused item |

---

## 12. Accessibility

| Feature | Details |
|---|---|
| Font size | Four steps: small / medium / large / xlarge. Applied as a multiplier on JSS typography tokens. |
| Dyslexic font | Optional OpenDyslexic substitution for body font. Requires bundling the font in the JAR. Set `font-dyslexic` in JSS to the font name; `settings.accessibility.dyslexicFont = true` activates it. |
| Reduce motion | Disables flip animations (crossfade instead), disables countdown progress bar animation (shows static percentage text instead). |
| High contrast | Overrides the active theme with a high-contrast variant: forces `border-width-thick`, increases text brightness, removes translucent `rgba` backgrounds. Implemented as a post-processing pass in `ThemeEngine` rather than a separate theme file. |
| Keyboard navigation | All interactive elements must be reachable by Tab. Focus ring always visible (never suppressed). |
| Screen reader labels | All icon-only buttons must have accessible labels (`aria`-equivalent in JavaFX: `Node.setAccessibleText`). |

---

## 13. Window State

Stored at `~/.revisionapp/window.json`:

```json
{
  "x":          100,
  "y":          80,
  "width":      900,
  "height":     700,
  "maximised":  false
}
```

- Written on every window move, resize, and maximise/restore event (debounced 500ms).
- Read on launch. If the saved position is off-screen (monitors changed), re-centre on primary screen.
- Minimum window size: 700 × 520.

---

## 14. Export & Backup

### 14.1 Progress backup

Export: `File → Export Progress` saves a ZIP containing all files from `~/.revisionapp/progress/`.

Import: `File → Import Progress` extracts the ZIP, validates each file, and merges into the progress directory. Conflict resolution: prompt per-module (keep existing / overwrite / keep newer).

### 14.2 Full backup

`File → Export Full Backup` saves a ZIP of the entire `~/.revisionapp/` directory. Useful when moving between machines.

### 14.3 Definitions PDF export

Available from the Definitions view. Generates a two-column A4 PDF:

- Header: module name + date.
- Entries grouped by category with colour-coded category headers.
- Term bold, definition body text beneath.
- Page numbers in footer.
- Uses whatever definitions are currently visible (respects active search/filter).

Implementation note: use Apache PDFBox or iText (check licence) for PDF generation.

### 14.4 Revision sheet PDF export

Available from the home screen quick reference panel. Generates a single-page A4 sheet:

- Quick reference table.
- Exam traps list.
- Module name and date.

---

## Appendix A — Bundled JSS Examples

### Grey theme (colours only, other sections identical to dark)

```json
{
  "meta": { "name": "Grey", "author": "RevisionApp", "version": "1.0" },
  "colors": {
    "background":       "#1a1a1a",
    "surface":          "#242424",
    "surface-raised":   "#2e2e2e",
    "border":           "#3a3a3a",
    "border-focus":     "#6366f1",
    "text-primary":     "#e0e0e0",
    "text-secondary":   "#a0a0a0",
    "text-muted":       "#606060",
    "accent":           "#6366f1",
    "accent-hover":     "#4f46e5",
    "accent-subtle":    "rgba(99,102,241,0.15)",
    "success":          "#22c55e",
    "success-subtle":   "rgba(34,197,94,0.12)",
    "danger":           "#ef4444",
    "danger-subtle":    "rgba(239,68,68,0.12)",
    "warning":          "#f59e0b",
    "warning-subtle":   "rgba(245,158,11,0.12)",
    "scrollbar-track":  "#1a1a1a",
    "scrollbar-thumb":  "#3a3a3a",
    "badge-bg":         "#052e16",
    "badge-border":     "#22c55e",
    "badge-text":       "#4ade80",
    "overlay":          "rgba(0,0,0,0.5)"
  }
}
```

### Light theme (colours only)

```json
{
  "meta": { "name": "Light", "author": "RevisionApp", "version": "1.0" },
  "colors": {
    "background":       "#f8fafc",
    "surface":          "#ffffff",
    "surface-raised":   "#f1f5f9",
    "border":           "#e2e8f0",
    "border-focus":     "#6366f1",
    "text-primary":     "#0f172a",
    "text-secondary":   "#475569",
    "text-muted":       "#94a3b8",
    "accent":           "#6366f1",
    "accent-hover":     "#4f46e5",
    "accent-subtle":    "rgba(99,102,241,0.1)",
    "success":          "#16a34a",
    "success-subtle":   "rgba(22,163,74,0.1)",
    "danger":           "#dc2626",
    "danger-subtle":    "rgba(220,38,38,0.1)",
    "warning":          "#d97706",
    "warning-subtle":   "rgba(217,119,6,0.1)",
    "scrollbar-track":  "#f1f5f9",
    "scrollbar-thumb":  "#cbd5e1",
    "badge-bg":         "#dcfce7",
    "badge-border":     "#16a34a",
    "badge-text":       "#15803d",
    "overlay":          "rgba(0,0,0,0.3)"
  }
}
```

---

## Appendix B — SM-2 worked example

```
Card: "What is a tautology?"
Initial state: interval=1, easeFactor=2.5, reviewCount=0

Session 1 — user rates 4 (correct with hesitation)
  q=4, reviewCount=0 → interval=1
  easeFactor = 2.5 + (0.1 - 1*0.08 + 1*0.02) = 2.5 + 0.04 = 2.54
  due = today + 1 day

Session 2 (next day) — user rates 5
  q=5, reviewCount=1 → interval=6
  easeFactor = 2.54 + (0.1 - 0) = 2.64
  due = today + 6 days

Session 3 (6 days later) — user rates 3
  q=3, reviewCount=2 → interval = round(6 * 2.64) = 16
  easeFactor = 2.64 + (0.1 - 2*0.08 + 4*0.02) = 2.64 + 0.02 = 2.66
  due = today + 16 days

Session 4 (16 days later) — user rates 2 (missed)
  q=2 < 3 → interval=1, easeFactor = max(1.3, 2.66-0.2) = 2.46
  due = today + 1 day
```

---

*End of specification.*
