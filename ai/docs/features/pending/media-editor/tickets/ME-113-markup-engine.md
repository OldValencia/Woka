# ME-113 — Markup engine

| | |
|---|---|
| **Order** | 40 — the first `Annotate` tool, after ME-112 closes the `Color` domain |
| **Depends on** | ME-105 (the fold's `Geometry`/`Paint` ordering), ME-109 (`EditorToolScope`, source coordinates) |
| **Feature** | [Media Editor](../media-editor.md) |

**Goal.** The four geometric shapes as data, one draw routine that every path shares, and the tool that
composes them — no gestures, which are ME-114.

**Why now.** `RasterStage.Paint` has existed since ME-007 and **nothing has ever produced one**. Both
draw paths already apply the paint list outside the matrix — `RasterSurface` and `renderAtNativeScale`,
each with a comment saying a crop must clip an arrow rather than move it — and neither has been
exercised by a single shape. This is the ticket that makes that code true rather than intended.

## The one thing that is not obvious

**One operation carries the whole shape list, and the tool is `Single`.**

Three comments in the tree say `Repeating` — the backlog block, `ToolCardinality` ("three arrows are
three arrows") and `EditorToolScopeImpl` ("markup will be `Repeating` and accumulate"). All three were
written before anything had to *move* a shape, and moving is what decides it.

Under `Repeating`, one operation is one shape and a drawn arrow is **committed**. Dragging it then has
to change a committed operation, and there is no way to do that: `EditorSession` offers `preview`,
`commit`, `undo`, `redo`, `reset` and nothing that edits history's contents. Worse, `preview` puts the
moved copy in `pending` while the original is still in `ops`, so the fold — which applies both — would
draw the arrow **twice** for the length of the drag.

Under `Single`, the document holds one markup operation whose payload is the ordered list. Adding,
moving, resizing and deleting are all "preview a different list, commit on release", which is exactly
the mechanism Crop already uses and needs no new session API at all. Undo granularity is unchanged:
one gesture, one entry.

Three arrows are still three arrows. They are three entries in one list rather than three operations,
and the fold emits **one** `RasterStage.Paint` that draws them in order.

**The pen is not the shapes.** `Color` and `Width` in the schema are the *next* shape's style; each
shape stores its own, copied at creation. They live in the same `ParamSet` as the list, which is safe
because `paramsOf` hands the whole set to `ParamWriter` — writing a colour carries the shapes through
untouched, the way Crop's aspect chip carries its rect.

## Steps

1. `tools/markup/MarkupShape.kt` (new). `@Serializable sealed interface MarkupShape` with `id: String`,
   `color: Long`, `width: Float`; `Arrow(from, to)`, `Rectangle(from, to)`, `Ellipse(from, to)`,
   `Freehand(points: List<MarkupPoint>)`. `MarkupPoint(x: Float, y: Float)` because `Offset` is not
   serialisable and a shape is stored in **source pixels**, floats, never canvas ones. Text is ME-122
   and is not in this hierarchy yet.
2. `tools/markup/MarkupGeometry.kt` (new), pure: `fun MarkupShape.hitTest(x, y, tolerance): Boolean`,
   `fun MarkupShape.handles(): List<MarkupPoint>` (two for an arrow, four for a box or an ellipse, none
   for freehand), `fun MarkupShape.movedBy(dx, dy): MarkupShape`, and
   `fun MarkupShape.withHandle(index, x, y): MarkupShape`. Moving and resizing are arithmetic on the
   model, so ME-114 is gestures and nothing else.
3. `tools/markup/MarkupDraw.kt` (new): `fun DrawScope.drawMarkup(shape: MarkupShape)`. **The single
   routine** — the overlay, the recipe's paint and the save all call this one function, which is what
   makes "rasterised at source resolution" fall out rather than being arranged. An arrow is its line
   plus a head sized from `width`; freehand is a `Path` through its points; the box and the ellipse are
   stroked with round joins.
4. `tools/markup/MarkupTool.kt` (new): `object MarkupTool : EditorTool` with
   `Shapes = ObjectParam("shapes", ListSerializer(MarkupShape.serializer()), emptyList())`,
   `Kind = ChoiceParam("kind", "Shape", MarkupKind.entries, Arrow)`, `Color = ColorParam(...)`,
   `Width = FloatParam("width", "Width", 1f..64f, default = 6f)`. Manifest: `id = "markup"`,
   `domain = ToolDomain.Annotate`, `order = 0`, `cardinality = ToolCardinality.Single`,
   `interaction = ToolInteraction.Canvas`, `icon = Icons.Static.PencilLine`.
   **Not a `CanvasTool` yet** — `ToolInteraction.Canvas` is a manifest claim the type does not have to
   back, which `FixtureTool` already documents; ME-114 adds the interface and the overlay.
5. Same file: `override fun stage(params: ParamSet): ToolStage?` returns `null` for an empty list and
   otherwise `RasterStage.Paint { params[Shapes].forEach { drawMarkup(it) } }` — one stage for every
   shape, in list order.
6. `di/EditorModule.kt` (edit): one `single<EditorTool>(named(MarkupTool.manifest.id)) { MarkupTool }`,
   matching the qualifier every other tool uses — two unqualified `single<EditorTool>` are one
   definition declared twice, which is how Transform disappeared from the rail in ME-109.

## Files

`shared/src/jvmMain/kotlin/com/woka/feature/editor/tools/markup/MarkupShape.kt` (new) ·
`.../tools/markup/MarkupGeometry.kt` (new) · `.../tools/markup/MarkupDraw.kt` (new) ·
`.../tools/markup/MarkupTool.kt` (new) · `.../di/EditorModule.kt` (edit)

Also **correct the three stale comments** that say markup will be `Repeating`:
`shared/src/commonMain/kotlin/com/woka/feature/editor/core/ToolCardinality.kt`,
`.../core/ObjectParam` in `ParamSpecChoice.kt`, `.../shell/EditorToolScopeImpl.kt` — a decision reversed
in a ticket and left contradicted in the code is the next reader's wrong turn.

## Done when

- `jvmTest/unit/MarkupShapeTest.kt`: every shape round-trips through `editorJson`; a payload naming a
  shape type that does not exist decodes to null and leaves the rest of the document readable — the
  degradation `ObjectParam` already promises, asserted for the first payload that uses it.
- `jvmTest/unit/MarkupGeometryTest.kt`: a point on a stroke hits and one a tolerance away does not; an
  arrow reports two handles and a rectangle four; `movedBy` translates every point and changes nothing
  else; `withHandle` moves one corner and leaves the opposite one fixed.
- `jvmTest/unit/MarkupToolTest.kt`: an empty list composes **no** stage; three shapes compose exactly
  **one** `RasterStage.Paint`, so `fold` reports `paints.size == 1`; a document holding a crop *and*
  markup keeps the paint out of `matrix` — the ordering both draw paths comment on and neither has
  ever run.
- By hand: the rail shows Markup and its panel draws a shape chooser, a swatch row and a width slider.
  **Nothing can be drawn yet** — that is ME-114, and this ticket is honest about shipping a tool that
  renders a document it cannot yet create.
- `./gradlew.bat :shared:build` green.
