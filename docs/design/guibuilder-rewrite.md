# GUI Builder rewrite — design

A state-of-the-art, **bi-directional** visual GUI designer for Scilab 2027, replacing the
`guibuilder` ATOMS toolbox with a core module.

Status: **design approved 2026-08-29** (revision 2), not yet implemented.

Revision 2 replaces the project-file-plus-guarded-regions model of revision 1 with
source-as-model round-tripping, and brings callback editing into the builder. Revision 1
under-scoped the brief; the reasoning that replaced it is in section 3.

## 1. Why replace it

The existing `guibuilder` (ATOMS toolbox, v4.2.3, ~50 Scilab macros) is not merely dated,
it is actively destructive. A single session of use produced, in order:

    set: The handle is not or no more valid.
    This object has no data property.
    'Figure' handle does not or no longer exists.

Root cause, established by measurement: `draw_common()` accepted a click from any window
and then trusted `gce()` blindly. `gce()` is a *global* current entity, not per-figure and
not necessarily what `xrect` just drew. When a click landed on the palette window — built
with `default_axes "off"`, measured to have 32 children and **zero** axes — `xrect` created
nothing, so `gce()` still referred to a live object. The code wrote `r.data` to it and then
ran `delete(r)`, destroying it. One stray click deleted the component listbox, the next the
canvas axes, the next the canvas figure.

Two further defects came from the same era of design:

- `auto()` was a *second* function inside `guibuilder.sci`. `genlib` exposes exactly one
  macro per file, named after the file, so `auto` was never in `guibuilderlib` — measured:
  50 macros exposed, `auto` not among them — while every control is created with
  `"callback","auto"`. Clicking any control you placed always failed.
- All state lived in one base-workspace `handles` struct published by `resume()`. Any path
  that failed to reach the `resume` left the on-screen widgets and the variable describing
  them permanently out of step.

Those bugs are fixed (guibuilder `0ff6972`, `06aaf2c`), but the architecture that produced
them is intact. This document specifies the replacement.

## 2. Decisions taken

| Question | Decision |
|---|---|
| Where it lives | **New Scilab core module**, `modules/guibuilder`. The ATOMS toolbox is retired. |
| Source of truth | **The `.sce` itself.** Bi-directional: no project file, no generated/user distinction. |
| Positioning | **Layout-aware from day one** — Scilab's border/grid/gridbag layouts. |
| Callback bodies | **Edited inside the builder**, via the embedded Scilab editor component. |
| Partial understanding | **Open it anyway, with unmodelled parts locked** — never refuse, never drop. |
| Sequencing | **Phased**, each phase usable software. |
| Canvas | **Swing design surface mirroring Scilab's own widget mapping.** |

## 3. The bi-directional decision

Two industry designers bracket the choice:

- **NetBeans Matisse** keeps a `.form` file and writes guarded blocks into the source. The
  tool owns one region; the user owns the rest.
- **Eclipse WindowBuilder** treats the source as the model. Its documentation is explicit:
  *"The tool does not make any distinction between generated code and user-written code"*,
  and it *"can read and write almost any format and reverse-engineer most hand-written Java
  GUI code"*.

This design follows **WindowBuilder**. A guarded-region tool cannot open a GUI somebody
wrote by hand, which is most of the Scilab GUIs that exist.

WindowBuilder itself is not liftable: it is an Eclipse plug-in built on JDT for the AST and
GEF for the canvas. What transfers is the architecture. The reusable assets are in this
tree, and all three were verified before being relied on:

| Asset | Provides | Verification |
|---|---|---|
| SciNotes `scilab.jflex`, `ScilabLexer`, `FunctionScanner`, `MatchingBlockScanner` | A Scilab tokenizer in Java with source positions | 146 Java files, in production use for highlighting |
| `macr2tree` + `tree2code` | Full Scilab parse to tree and back to source | Run live: round-tripped a function **with its comments intact**, including a trailing comment |
| `ScilabEditorPane extends JEditorPane` | Embeddable Scilab editor with highlighting | Already embedded outside SciNotes by `helptools/ScilabSourceBrowser` and `preferences/PreviewCode` |

`macr2tree`/`tree2code` round-trips semantics and comments but **normalises whitespace**:
`y   =   x + 1;` came back as `y = x+1;`, `function y =` as `function [y] =`, indentation
lost. Regenerating a user's file through it would reformat all of it. It is also absent
from `modules/core/sci_gateway/*.xml` despite working at runtime, so its registration is
not where a maintainer would look. For both reasons it is used **only as a validation
oracle**, never as the write path.

## 4. Why a Swing design surface

Every Scilab uicontrol *is* a standard Swing component:

    SwingScilabPushButton extends JButton
    SwingScilabCheckBox   extends JCheckBox
    SwingScilabSlider     extends JSlider
    SwingScilabListBox    extends JScrollPane

They add `SwingViewObject` only to bind to the graphic-object model. A Swing canvas built
from the same classes is therefore not an approximation of a Scilab GUI but structurally
the same widgets. And Scilab's `__GO_LAYOUT__` (border position/preferredsize/padding;
gridbag grid/weight/fill/anchor/padding) maps onto Swing's `BorderLayout`, `GridLayout` and
`GridBagLayout`, so **layout fidelity is free rather than simulated**.

Rejected: instantiating Scilab's `SwingScilab*` classes directly requires a real
graphic-object UID with `SwingView` registration, dragging the JNI abort (exit 134 in a
hermetic test JVM) into every unit test. Also rejected: a custom-painted abstract canvas,
which would mean reimplementing `GridBagLayout` semantics by hand.

The cost is a 12-entry style-to-Swing mapping kept in step with Scilab's bridge classes,
against a widget set stable for years. A test asserts the mapping against the real
`SwingScilab*` classes so drift fails the build, not the user.

## 5. Architecture

### 5.1 Module

`modules/guibuilder`, with the anatomy of `modules/scinotes`: `CMakeLists.txt`, `pom.xml`,
`macros/`, `sci_gateway/`, `src/java/`, `etc/`, `help/`, `locales/`, `tests/`. It takes a
Maven dependency on `scinotes` for the lexer and the editor pane — a dependency, not a copy.
(`helptools` carries its own forked `ScilabLexer`; that fork is not to be repeated.)

### 5.2 Launch path

Follows the SciNotes route:

    guibuilder()  or  guibuilder("myapp.sce")   [macro]
      -> sci_gateway/cpp/sci_guibuilder.cpp
      -> giws bridge generated from @ScilabExported
      -> Java GuiBuilder.open(path)

The editor is a `GuiBuilderTabFactory extends AbstractScilabTabFactory`, so it docks,
follows the FlatLaf theme and is restored by `WindowsConfigurationManager` like SciNotes and
Xcos.

### 5.3 Units and dependency direction

| Unit | Contains | Depends on |
|---|---|---|
| `model` | `Design`, `Node`, `Frame`, `LayoutSpec`, properties, source ranges, `UnmodelledRegion` | nothing |
| `parse` | Scilab source to model; reverse-engineering of figure/uicontrol construction | `model`, scinotes lexer |
| `write` | model edits to new source text, position-preserving; validation via the oracle | `model` |
| `render` | style-to-Swing mapping, layout mapping, model to live components | `model`, Swing |
| `editor` | canvas, input overlay, palette, inspector, tree, undo, callback pane | all of the above |

Strictly one-way; nothing depends on `editor`. `model`, `parse` and `write` contain no Swing,
so the three units carrying the correctness-critical logic are unit-testable headlessly.

## 6. Data model

    Design
      file          : the .sce being edited
      figure        : figure properties (name, tag, size, resizable, background, ...)
      units         : PIXELS | NORMALIZED   (taken from the file; PIXELS for new designs)
      root          : Frame
      unmodelled    : ordered list of UnmodelledRegion

    Node (abstract)
      id            : stable internal identifier, never shown
      tag           : user-facing name; a valid, unique Scilab identifier
      style         : PUSHBUTTON | EDIT | TEXT | CHECKBOX | RADIOBUTTON | LISTBOX
                    | POPUPMENU | SLIDER | SPINNER | TABLE | IMAGE | FRAME | AXES
      properties    : property key to value, each carrying its own source range
      constraint    : how the parent's layout places this node
      sourceRange   : the exact span of the call that created it
      locked        : true when some part of it was not fully understood

    Frame extends Node
      layout        : LayoutSpec
      children      : ordered list of Node

    UnmodelledRegion
      sourceRange   : a span the parser could not model
      reason        : why, in words fit to show the user

`LayoutSpec` is `None` (children carry `position = [x y w h]`), `Border`, `Grid` or
`GridBag`, mirroring `__GO_LAYOUT__` exactly. One `LayoutSpec` drives both the canvas and
the emitted code, so the two cannot disagree.

`tag` is validated on entry: non-empty, a legal Scilab identifier, unique in the design,
not a keyword or an existing library macro. A design can never reach the writer with a tag
that would produce invalid Scilab.

## 7. Reading a `.sce`

The parser tokenises with the SciNotes lexer and runs a small recursive-descent pass over
the token stream. It does **not** attempt to be a general Scilab parser. It recognises:

- `figure(...)` and `uicontrol(...)` calls and their property-list arguments
- assignments capturing those calls (`handles.ok = uicontrol(...)`, `h = figure(...)`)
- literal property values, and named constants it can resolve
- `function` definitions and their bodies, located with `FunctionScanner`

Everything it recognises becomes a `Node` with a `sourceRange`. Everything else becomes an
`UnmodelledRegion`.

### 7.1 The degradation contract

A file we only partly understand **opens**, with the parts we do not model **locked**:

- Locked regions appear in the component tree, named and with the reason shown.
- They are rendered on the canvas where their geometry is known, and marked as locked.
- They are **never rewritten and never dropped**. Their bytes are carried through untouched.
- An edit that would require modifying a locked region is **refused with an explanation**,
  not silently applied or silently ignored.
- A property with a computed value (`"position", [x y w h]` where `x` is a variable) locks
  that property alone, not the whole widget: everything else stays editable.

A round-trip tool that loses code it did not understand is worse than no round-trip at all.
This section is a hard requirement.

If a file builds more than one figure, the builder lists them and edits one at a time; the
others are carried through as unmodelled.

## 8. Writing a `.sce`

Writing is **position-preserving rewriting**, the equivalent of JDT's `ASTRewrite`, not
regeneration. Only the spans that changed are replaced; untouched code keeps its bytes, its
indentation and its blank lines.

New widgets are inserted adjacent to their siblings' spans, matching the surrounding
indentation. Deletions remove exactly the widget's span and its own line if that leaves the
line blank.

The controlling invariant, and the first test written:

> **Open a file, change nothing, save: the bytes are identical.**

After every write the result is parsed with the `macr2tree` oracle. If it does not parse,
**the write is refused and the file on disk is left untouched.** The tool must never leave
the user with a broken file.

There is no base-workspace `handles` and no `resume()` in anything the builder emits. New
designs get a figure whose `userdata` holds the handle struct and a generated accessor;
callbacks fetch state through it. That removes the failure class behind the original bug
report. When editing a file that uses a different convention, the builder preserves that
file's convention rather than imposing its own.

## 9. Editing experience

One dockable tab: palette left, canvas centre, inspector and component tree right, callback
editor in a bottom pane, toolbar above for align/distribute, snap, preview and save.

Canvas:

- Drag from palette; the drop lands in the frame under the cursor.
- Click to select, shift-click to extend, marquee-drag on empty space.
- Eight resize handles; drag to move; arrows nudge 1px, shift-arrow 10px; Escape cancels a
  drag in flight.
- Snapping to sibling edges and centres and to parent margins, with live alignment guides.
- Context menu: cut, copy, paste, duplicate, delete, raise, lower, wrap in frame.
- The overlay owns every mouse event. Widgets never receive input in design mode. There are
  no marker rectangles, no `gce()`, and nothing the user can see is ever passed to `delete`.

With a layout other than `None`, dragging shows drop zones — border regions or grid cells —
and the constraint is edited in the inspector.

**Callback editing** uses an embedded `ScilabEditorPane`: selecting a widget shows its
callback body with real Scilab highlighting. Creating a widget offers to create the
callback function; the body is the user's from that moment and is only ever edited through
the pane, never regenerated. Renaming a tag offers to rename the callback and updates the
reference; declining leaves both untouched and warns.

Every mutation goes through a command object, so undo/redo exists from the first commit.

Preview writes to a temporary file and runs it in Scilab. If preview and canvas disagree,
that is a bug with a reproducible test.

## 10. Testing

| Unit | Approach |
|---|---|
| `model` | Tag validation and uniqueness, reparenting, constraint invariants |
| `parse` | Corpus of `.sce` files including hand-written ones: expected nodes, expected locked regions, expected reasons |
| `write` | **No-op round-trip is byte-identical** (property-based over the corpus); an edit changes only the intended span; a write that would not parse is refused; locked regions are never touched |
| `render` | Headless Swing tests of the style-to-Swing mapping, asserted against the real `SwingScilab*` classes |
| `editor` | Command objects against the model; every command's undo restores the exact prior state |
| end-to-end | Open, edit, save, **run the result in Scilab**, assert widgets exist with expected tags and geometry |

The corpus deliberately includes the `.sce` files the old ATOMS builder generated, and
hand-written GUIs from the toolbox tree, so "opens real files" is measured rather than
asserted. Headless Swing construction is already proven here by `ContentLayoutThemeTest`.

## 11. Phases

**Phase 1 — round-trip core.** Module plumbing, gateway, dockable tab; `model`, `parse`,
`write` with the byte-identical invariant and the locking contract; a real canvas for
absolute positioning (select, move, resize, multi-select, snap, align/distribute); palette;
inspector; tree; undo/redo; embedded callback editor; preview. Layouts are modelled but only
`None` is selectable. The ATOMS toolbox is retired here, since the command collision appears
as soon as the core `guibuilder()` exists.

**Phase 2 — layouts.** Border, Grid and GridBag: parsing them out of existing files, drop
zones, constraint editing, a canvas driven by the real Swing layout managers, and writing
layout and constraints back. GUIs that resize correctly.

**Phase 3 — polish.** Nested-frame interaction, cross-design copy and paste, z-order, the
specifics of image/table/axes, keyboard-first flows, templates and help pages.

## 12. Retiring the ATOMS toolbox

The toolbox defines a `guibuilder` macro and is in the autoload set, so two `guibuilder`
commands would shadow each other unpredictably. In phase 1 it is removed from the tbxManager
catalog and the autoload manifest, `cfg.verified` is updated so the catalog count stays
honest, and the repository is left in place as history with its recent fixes intact. Its
generated `.sce` files remain openable — that is a phase-1 test, not a courtesy.

## 13. Non-goals

- Being a general Scilab refactoring tool. The parser understands GUI construction; anything
  else is carried through untouched.
- Designing plot content. `axes` is placed and sized; what is drawn into it belongs to the
  script.
- Windows and Linux verification. The code is plain Swing and portable, but only macOS is
  verified here, consistent with the rest of this fork.

## 14. Risks

- **The parser is the project's risk.** Reverse-engineering arbitrary Scilab is unbounded;
  what makes it tractable is that failure is *allowed* — anything not understood locks
  rather than breaks. The corpus test is what keeps that honest.
- **Write fidelity.** A position-preserving rewriter that corrupts a file is the worst
  possible outcome. Mitigated by the byte-identical no-op invariant, the parse-back oracle,
  and refusing to write rather than writing something unparseable.
- **Mapping drift** if Scilab changes a bridge class's superclass. Mitigated by a test
  asserting the mapping against the real classes.
- **GridBag fidelity** between the canvas and the Scilab runtime. Mitigated by the phase-2
  end-to-end test comparing run geometry against the canvas.
- **Module plumbing** touches CMake, Maven and the classpath, where `ScilabClasspath.cmake`
  fatals if a token's glob does not match exactly one jar. Known ground after the build
  migration, but phase-1 work that must be budgeted.
