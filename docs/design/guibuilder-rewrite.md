# GUI Builder rewrite — design

A state-of-the-art visual GUI designer for Scilab 2027, replacing the `guibuilder`
ATOMS toolbox with a core module.

Status: **design approved 2026-08-29**, not yet implemented.

## 1. Why

The existing `guibuilder` (ATOMS toolbox, v4.2.3, ~50 Scilab macros) is not merely
dated, it is actively destructive. A single session of use produced, in order:

    set: The handle is not or no more valid.
    This object has no data property.
    'Figure' handle does not or no longer exists.

Root cause, established by measurement rather than inspection: `draw_common()`
accepted a click from any window and then trusted `gce()` blindly. `gce()` is a
*global* current entity, not per-figure and not necessarily what `xrect` just drew.
When a click landed on the palette window — which is built with `default_axes "off"`
and was measured to have 32 children and **zero** axes — `xrect` created nothing, so
`gce()` still referred to a live object. The code wrote `r.data` to it and then ran
`delete(r)`, destroying it. One stray click deleted the component listbox; the next
the canvas axes; the next the canvas figure itself.

Two further defects came from the same era of design:

- `auto()` was a *second* function inside `guibuilder.sci`. `genlib` exposes exactly
  one macro per file, named after the file, so `auto` was never in `guibuilderlib` —
  measured: 50 macros exposed, `auto` not among them — while every control is created
  with `"callback","auto"`. Clicking any control you placed always failed.
- All state lived in a single base-workspace `handles` struct published by `resume()`.
  Any path that failed to reach the `resume` left the on-screen widgets and the
  variable describing them permanently out of step.

Those specific bugs are now fixed (guibuilder `0ff6972`, `06aaf2c`) so the toolbox is
usable, but the architecture that produced them is intact. This document specifies the
replacement.

## 2. Decisions taken

| Question | Decision |
|---|---|
| Where it lives | **New Scilab core module**, `modules/guibuilder`. The ATOMS toolbox is retired. |
| Persistence | **Project file + guarded codegen.** `.sgui` is the source of truth; generated `.sce` preserves user code outside marked regions. |
| Positioning | **Layout-aware from day one** — Scilab's border/grid/gridbag layouts, not absolute-only. |
| Sequencing | **Phased**, each phase usable software. |
| Canvas | **Swing design surface mirroring Scilab's own widget mapping** (approach A below). |

## 3. Why a Swing design surface (approach A)

Every Scilab uicontrol *is* a standard Swing component:

    SwingScilabPushButton extends JButton
    SwingScilabCheckBox   extends JCheckBox
    SwingScilabSlider     extends JSlider
    SwingScilabListBox    extends JScrollPane

They add `SwingViewObject` only to bind to the graphic-object model. So a Swing canvas
built from the same classes is not an approximation of a Scilab GUI — it is structurally
the same widgets. And because Scilab's `__GO_LAYOUT__` (border position/preferredsize/
padding; gridbag grid/weight/fill/anchor/padding) maps onto Swing's `BorderLayout`,
`GridLayout` and `GridBagLayout`, **layout fidelity is free rather than simulated**.

Rejected alternatives:

- **Instantiate Scilab's `SwingScilab*` classes directly.** Zero mapping drift, but they
  require a real graphic-object UID with `SwingView` registration, routing updates through
  the model and JNI. That coupling drags the JNI-abort problem (referencing model classes
  aborts a hermetic test JVM with exit 134) into every unit test.
- **Custom-painted abstract canvas** (jgraphx or hand-drawn). Simplest input handling, but
  not WYSIWYG, and previewing layouts would mean reimplementing `GridBagLayout` semantics
  by hand — exactly the hard part.

The cost of approach A is a 12-entry style-to-Swing mapping kept in step with Scilab's
bridge classes, against a widget set that has been stable for years.

## 4. Architecture

### 4.1 Module

`modules/guibuilder`, with the anatomy of `modules/scinotes`: `CMakeLists.txt`, `pom.xml`,
`macros/`, `sci_gateway/`, `src/java/`, `etc/`, `help/`, `locales/`, `tests/`.

### 4.2 Launch path

Follows the proven SciNotes route exactly:

    guibuilder()  [macro]
      -> sci_gateway/cpp/sci_guibuilder.cpp
      -> giws bridge generated from @ScilabExported
      -> Java GuiBuilder.open(path)

The editor is a `GuiBuilderTabFactory extends AbstractScilabTabFactory`, so it docks,
follows the FlatLaf theme, and is restored by `WindowsConfigurationManager` like SciNotes
and Xcos. No bespoke window handling.

### 4.3 Units and dependency direction

| Unit | Contains | Depends on |
|---|---|---|
| `model` | `Design`, `Node`, `Frame`, `LayoutSpec`, property values, `.sgui` load/save | nothing |
| `codegen` | model to `.sce`, and the merge preserving user code | `model` |
| `render` | style-to-Swing mapping, layout mapping, model to live components | `model`, Swing |
| `editor` | canvas, input overlay, palette, inspector, tree, undo, commands | `model`, `render` |

Dependencies are strictly one-way and nothing depends on `editor`. `model` and `codegen`
contain no Swing and no Scilab references, so the two units carrying the correctness-critical
logic — layout semantics and code generation — are fully unit-testable headlessly.

## 5. Data model

    Design
      formatVersion : int
      figure        : figure properties (name, tag, size, resizable, background,
                      menubar/toolbar/infobar visibility)
      units         : PIXELS | NORMALIZED     (default PIXELS)
      root          : Frame

    Node (abstract)
      id            : stable internal identifier, never shown to the user
      tag           : user-facing name; a valid Scilab identifier, unique within the design
      style         : PUSHBUTTON | EDIT | TEXT | CHECKBOX | RADIOBUTTON | LISTBOX
                    | POPUPMENU | SLIDER | SPINNER | TABLE | IMAGE | FRAME | AXES
      properties    : map of property key to value (string, font, colours, enable,
                      visible, tooltip, min/max/value, ...)
      constraint    : how the parent's layout places this node

    Frame extends Node
      layout        : LayoutSpec
      children      : ordered list of Node

`LayoutSpec` is one of:

- `None` — children carry `position = [x y w h]` in the design's units
- `Border` — children carry a position in {top, bottom, left, right, center},
  plus preferredsize and padding
- `Grid` — rows, columns, padding; children fill in order
- `GridBag` — children carry grid(x, y, w, h), weight, fill, anchor, padding

This mirrors `__GO_LAYOUT__` exactly, and one `LayoutSpec` drives both the canvas and the
generated code, so the two cannot disagree.

`tag` is validated on entry: non-empty, a legal Scilab identifier, unique in the design,
and not a Scilab keyword or an existing library macro name. Rejection is immediate and
explained; a design can never reach codegen with a tag that would produce invalid Scilab.

## 6. Project file

`.sgui`, JSON, serialised with `gson` (already a dependency in this part of the tree).
Carries `formatVersion` so later format changes migrate rather than break. The `.sgui` is
the source of truth for layout; the `.sce` is generated from it.

## 7. Code generation

Generated code is plain uicontrol calls that run on **stock Scilab**, not only this fork.

    // === BEGIN GUIBUILDER GENERATED CODE — DO NOT EDIT THIS BLOCK ===
    // Generated from myapp.sgui
    function handles = myapp_build()
      handles = struct();
      handles.fig = figure("figure_name","My App", "tag","myapp_figure", ...);
      handles.okButton = uicontrol(handles.fig, "style","pushbutton", "tag","okButton", ...);
      handles.fig.userdata = handles;
    endfunction

    function handles = myapp_handles()
      handles = findobj("tag","myapp_figure").userdata;
    endfunction
    // === END GUIBUILDER GENERATED CODE ===

    function okButton_callback()
      handles = myapp_handles();
      // your code
    endfunction

    handles = myapp_build();

### 7.1 State model

There is **no base-workspace `handles` and no `resume()`**. State lives in the figure's
`userdata`, and callbacks fetch it through the generated accessor. This removes the entire
failure class the old builder suffered from: stale globals, a `resume` that did not publish,
and callbacks operating on a previous session's destroyed objects.

### 7.2 Regeneration rules

- Replace **only** the span between the markers. Everything outside is preserved
  byte-for-byte.
- Markers absent: write the full template.
- Markers malformed or unbalanced: **refuse to write, and say why.** Never guess at a
  boundary when user code is on the other side of it.
- Callback stubs are generated **once**, outside the guarded region. They are never
  regenerated and never deleted. Renaming a tag produces a warning naming the now-orphaned
  callback; it does not remove or rewrite it.

The tool must never destroy the user's work. This is a hard requirement, and it is the
direct lesson of the bug that motivated this rewrite.

## 8. Editing experience

One dockable tab: palette left, canvas centre, property inspector and component tree right,
toolbar above for align/distribute, snap toggle, preview and generate.

Canvas:

- Drag from palette onto the canvas; the drop lands in the frame under the cursor.
- Click to select, shift-click to extend, marquee-drag on empty space.
- Eight resize handles; drag to move; arrow keys nudge 1px, shift-arrow 10px; Escape
  cancels a drag in flight.
- Snapping to sibling edges and centres and to parent margins, with live alignment guides.
- Context menu: cut, copy, paste, duplicate, delete, raise, lower, wrap in frame.
- The overlay owns every mouse event. Widgets never receive input in design mode. There are
  no marker rectangles, no `gce()`, and nothing the user can see is ever passed to `delete`.

When a frame's layout is not `None`, dragging shows drop zones — border regions or grid
cells — instead of free positioning, and the constraint is edited in the inspector.

Every mutation goes through a command object, so undo/redo is present from the first commit
rather than retrofitted.

Preview generates to a temporary `.sce` and executes it in Scilab. If the preview and the
canvas ever disagree, that is a bug with a reproducible test.

## 9. Testing

| Unit | Approach |
|---|---|
| `model` | JUnit: tag validation and uniqueness, reparenting, constraint invariants, `.sgui` round-trip |
| `codegen` | JUnit golden files; merge tests for user code preserved, stub never regenerated, malformed markers refused |
| `render` | Headless Swing tests of the style-to-Swing mapping |
| `editor` | Command objects tested against the model; property-based check that every command's undo restores the exact prior state |
| end-to-end | Generate a reference design, run it in Scilab, assert the widgets exist with the expected tags |

Headless Swing construction of `JPanel`/`JButton` and friends is already proven in this tree
by `ContentLayoutThemeTest`.

## 10. Phases

Each phase lands as working software.

**Phase 1 — a builder that can be used.** Module skeleton, gateway, dockable tab; `model`;
absolute placement; canvas with selection, move, resize, multi-select, snapping,
align/distribute; palette; inspector; tree; undo/redo; `.sgui` save and load; codegen with
guarded regions; preview. Layouts are modelled but only `None` is selectable. The ATOMS
toolbox is retired in this phase, because the command collision appears as soon as the core
`guibuilder()` exists.

**Phase 2 — layouts.** Border, Grid and GridBag: drop zones, constraint editing, a canvas
driven by the real Swing layout managers, and codegen emitting layout and constraints.
GUIs that resize correctly.

**Phase 3 — polish.** Nested-frame interaction, cross-design copy and paste, z-order, the
specifics of image/table/axes, keyboard-first flows, templates and help pages.

## 11. Retiring the ATOMS toolbox

The toolbox defines a `guibuilder` macro and is in the autoload set, so two `guibuilder`
commands would shadow each other unpredictably. In phase 1 the toolbox is removed from the
tbxManager catalog and the autoload manifest. The repository at
`SciLabProjects/guibuilder` is left in place as history, with its recent fixes intact, and
`cfg.verified` is updated so the catalog count stays honest.

## 12. Non-goals

- Parsing arbitrary Scilab. The `.sgui` is the source of truth; the generator never reads
  back hand-written layout code.
- Editing callback bodies inside the builder. That is SciNotes's job; the builder generates
  stubs and gets out of the way.
- Graphics objects beyond a placeholder `axes`. Plot content belongs to the generated
  script, not the designer.
- Windows and Linux verification. The code is plain Swing and portable, but only macOS is
  verified here, consistent with the rest of this fork.

## 13. Risks

- **Mapping drift**: Scilab could change a bridge class's Swing superclass. Mitigated by a
  test that asserts the mapping against the actual `SwingScilab*` classes, so drift fails
  the build rather than the user.
- **GridBag fidelity**: Scilab's gridbag translation to `GridBagConstraints` must match
  what the runtime does. Mitigated by the end-to-end phase-2 test that runs the generated
  GUI and compares resulting geometry against the canvas.
- **Module plumbing**: adding a core module touches CMake, Maven and the classpath, where
  `ScilabClasspath.cmake` fatals if a token's glob does not match exactly one jar. This is
  known ground after the build migration, but it is phase-1 work that must be budgeted.
