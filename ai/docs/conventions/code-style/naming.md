# Naming

A name is the cheapest documentation in the file. Woka's names say **what a thing is in the domain**,
not what layer it sits in.

## Types

- Domain first: `PlaybackGeneration`, `PanBounds`, `SeekPreview`, `MediaKind`, `ToolDomain`,
  `RasterRecipe`. Not `VideoHelper`, not `EditorUtils`, not `ImageManager`.
- `Manager`, `Helper`, `Util`, `Handler`, `Processor` in a *new* name is a smell: it means the concept
  has not been found yet. Argue about it before writing the file. (`DesktopUtils` and `PipManager`
  predate this rule; do not use them as precedent.)
- Suffixes that carry a promise:

| Suffix | Means |
|---|---|
| `*State` | an immutable snapshot |
| `*ViewModel` | a screen's state holder |
| `*Repository` | a source of domain data |
| `*Store` | the disk layer under a repository |
| `*Cache` | bounded, evictable, re-fillable |
| `*Job` | one unit of background work with a result |
| `*Scope` | what a callee is handed to work inside |
| `*Spec` | a declaration something else renders or executes |
| `*Probe` | measures the machine, asserts nothing |
| `*Harness` | test setup a test uses, not inherits |

## Functions

- Verbs, in the imperative: `toggleSelectMode()`, `backupOnce()`, `fold()`, `restore()`.
- A function returning a decision is named for the decision: `editPlan()`, `resumeAppliesTo()`.
- Boolean-returning helpers read as a claim: `hasBackup()`, `needsColorManagement`.
- No `doX`, no `handleX`, no `processX`. If the verb is empty, the function does two things.

## Properties

- Booleans are assertions: `isDirty`, `canUndo`, `pipAlwaysOnTop`, `showHiddenFiles`. Not `dirtyFlag`,
  not `undoEnabled`.
- Units in the name, always: `resumeMinDurationMinutes`, `retainedBytes`, `defaultZoomPercent`,
  `subtitleColorArgb`.
- Backing fields are `_x`, exposed as `x`. Nothing else uses a leading underscore.

## Files and packages

- File name = the main declaration. A file of siblings is named for the concept they share
  (`GlobalControlButtons.kt`, `ParamRows.kt`, `StillDecoders.kt`).
- Package = directory, lowercase, singular where it names a concept (`editor/core`), plural where it
  names a collection (`tools`, `fixtures`).
- Splitting a file keeps the root name and adds a role: `SubtitleCustomizePanel` →
  `SubtitleCustomizeControls` → `SubtitleCustomizeParts`. Never `Foo2`, never `FooExtra`.

## Tests

Class says what is under test (`RasterFoldTest`), test name says the behaviour as a sentence
(`an op added last but ranked first applies first`).

## Constants

`SCREAMING_SNAKE` for compile-time constants, with the reason in a comment when the value is a
judgement call (`DEFAULT_BUDGET_BYTES`, `KEY_SCHEMA_MARKER`, `STORAGE_ROOT_PROPERTY`). A system
property name is a `const val` next to what reads it, never a literal in two places.
