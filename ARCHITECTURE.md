# ARCHITECTURE.md

This document describes the architecture of the KENKERN engine and editor **as implemented in
Block 1 (Phases 0–2, engine version 0.1.0)**. Everything here is **IMPLEMENTED** unless marked
otherwise. Systems listed in [ROADMAP.md](ROADMAP.md) as PLANNED have no architectural presence
in this document beyond the extension points at the end.

## 1. Package map

All application code lives in `app/src/main/java/com/kenkern/engine/`.

| Package | Responsibility |
|---|---|
| `core/` | Engine-wide singletons: `EngineConfig` (centralized, immutable-by-default configuration: version, camera defaults, grid extents, clear color, autosave tunables), `EngineLogger` (logging facade over `android.util.Log` plus an in-memory ring buffer exposed via `recentEntries()`), `EngineRuntime` (one-time init from `KenkernApplication`; installs an uncaught-exception hook that writes crash reports to `filesDir/logs` before delegating to the platform handler). |
| `math/` | `Vector3` and `Matrix4`. Column-major 4×4 matrices with multiply, invert, perspective, lookAt and TRS composition. Single Euler convention: `R = Rz(z)·Ry(y)·Rx(x)` (X applied first, then Y, then Z). Thread-confined scratch objects avoid per-call allocations. |
| `scene/` | The scene model: `NinkId` (UUID-backed `@JvmInline value class`), `Nink` (the fundamental scene entity), `NinkType` (14 types with stable serialized enum names), `NinkComponent` (sealed interface: `Camera`, `Light`), `Transform` (TRS + `decomposeFrom` for world-preserving reparenting), `NinkRegistry` (single source of truth for structure), `Scene` (name, main camera, change listeners), `SceneOps` (`copySubtree`, `uniqueName`), `SceneEditor` (facade for all editor mutations), `SceneFactory` (default starter scene). |
| `scene/commands/` | Reversible mutations: `SceneCommand` / `CoalescingCommand` interfaces, `CommandStack` (LIFO undo/redo, 128-entry history, burst coalescing for continuous transform edits), `NinkCommands` (`AddNink`, `AddSubtree`, `DeleteNink` with exact restore, `RenameNink`, `SetTransform`, `SetFlags`, `ReparentNink` with world preservation, `ReorderNink`, `SetMainCamera`, `Batch`). |
| `rendering/` | `GLRenderer` (GLES30 draw loop; rebuilds all resources on context loss; zero per-frame allocations), `KenkernGLSurfaceView` (EGL ES3 context, RGBA8 + 24-bit depth, `preserveEGLContextOnPause`), `GLCapabilities` (GLES version probe + diagnostics), `FrameStats` (FPS / frame time), `RenderFrameData` + `RenderFramePublisher` (immutable snapshot hand-off UI → GL thread), `NinkRenderColors` (type/flag-driven palette). |
| `rendering/camera/` | `CameraController`: editor orbit camera (yaw/pitch/distance/target with orbit, pan, zoom). Separate from the scene's main camera. |
| `rendering/mesh/` | `Mesh`: interleaved pos+color VBO + IBO + VAO. `MeshFactory`: triangle, plane, cube, sphere, cylinder, pyramid, grid, wire cube — all with baked unlit shading per vertex normal. |
| `rendering/shader/` | `ShaderProgram`: compile + link with full error reporting and a uniform location cache. `BuiltinShaders`: unlit solid (per-draw uniform color) and vertex-color (used by the grid with alpha). |
| `input/` | `ViewportInputHandler`: 1-finger drag = orbit, 2-finger drag = pan, pinch = zoom; falls back from pan to orbit when a finger lifts. |
| `project/` | `KenkernProject` (metadata), `ProjectRepository` (files under `filesDir/projects/<id>/`; list/create/load/save/delete; atomic writes; metadata validation), `ProjectException` hierarchy (typed errors instead of partial state). |
| `serialization/` | `ProjectSerializer` (`project.json`), `SceneSerializer` (scene + prefab JSON with defensive loading), `SerializationException`. Pure Kotlin over `org.json` so it runs on the JVM in unit tests. |
| `editor/` | Compose UI: `MainActivity` (single activity, hosts all Compose content), `KenkernTheme` (identity colors), `HomeScreen`/`HomeViewModel` (project list/create/delete), `EditorRoot` (home ↔ editor navigation), `EditorScreen` (top bar with undo/redo/save/panel toggles, GL viewport via `AndroidView`, FPS overlay, floating panels, toasts + error banners), `EditorViewModel` (scene ownership, revisions for Compose resync, autosave debounce 1.5 s, render payload publishing). |
| `editor/panels/` | `HierarchyPanel` (floating + draggable, search, expand/collapse, drag-handle reparenting, per-row three-dot menus), `InspectorPanel` (flags, transform, camera/light components), `AddNinkMenu` (catalog-driven + prefabs), `NinkContextMenu` (type-filtered actions), `NinkCatalog` (extensible registry driving the Add menu), `Dialogs`, `CommonUi`. |
| `localization/` | `SupportedLocales`: `en` (base), `pt-BR`, `pt-PT`, `fr`. |

## 2. Threading model

KENKERN uses four execution contexts with a strict ownership discipline:

| Thread / dispatcher | Owns | Never touches |
|---|---|---|
| **Main thread** | Scene objects, `NinkRegistry`, `CommandStack`, all Compose state, all UI. Scene mutations happen here via commands (or validated direct writes for live Inspector component edits). | Direct GL state. |
| **GL thread** (managed by `GLSurfaceView`) | All GL calls, shader programs, meshes, scratch matrices, `CameraController`, `FrameStats`. | Live scene objects. |
| **`Dispatchers.IO`** | All file operations: project listing/creation, metadata, scene saves, prefab writes. | Scene graph mutations. |
| **Coroutines on `viewModelScope`** | Autosave debounce, save jobs, project load flows. | — |

The UI → GL hand-off is the core of the design: whenever the scene changes, the editor builds a
new immutable `RenderFrameData` (mesh kind, world matrix, color per renderable item; selection
highlight data) and publishes it with a **single volatile swap** on `RenderFramePublisher`.
The GL thread reads the latest snapshot at the start of each frame. The GL thread never sees
live `Nink` objects, and the main thread never blocks on rendering.

IO discipline: saves run on `Dispatchers.IO`. Autosave is debounced 1.5 s after the last scene
change (`EngineConfig.autosaveDelayMs`), and the pending save is flushed when the editor exits.

## 3. Rendering pipeline

```
Scene (main thread)                      GL thread
────────────────────                     ─────────────────────────────────────
scene change notification
   │
EditorViewModel.rebuildRenderPayload()
   │  builds immutable RenderFrameData
   ▼
RenderFramePublisher.current  ──volatile swap──▶  GLRenderer.onDrawFrame()
                                                      │  reads latest snapshot
                                                      │  view/proj from CameraController
                                                      │  MVP per item, draws mesh + grid
                                                      ▼
                                              KenkernGLSurfaceView (EGL ES3)
                                                      ▼
                                        Compose window (GL behind, panels above)
```

Key implementation points (all **IMPLEMENTED**):

- **Surface**: `KenkernGLSurfaceView` requests an OpenGL ES 3.0 client version and resolves the
  EGL config through a **fallback chain** (RGBA8 + D24 + S8 → RGBA8 + D16 → RGB565 → any
  window config) instead of a single rigid spec, so constrained/quirky drivers degrade
  gracefully. `preserveEGLContextOnPause = true` and `setZOrderOnTop(false)` — the surface
  renders *behind* the (translucent) Compose window surface, and the editor leaves only the
  viewport area unpainted so the GL content shows through while Compose panels float on top
  (see decision D7).
- **Crash containment (HOTFIX 0.1.1)**: EGL config availability is pre-validated on the UI
  thread by `EGLPreflight` before the GL surface is created — a broken driver becomes a
  localized UI error, not an uncaught GL-thread exception (which would kill the process).
  Surface creation, `setRenderer` and every `GLRenderer` callback are exception-guarded:
  failures are logged with stack traces and surfaced through the existing error banner.
- **Renderer**: `GLRenderer` implements `GLSurfaceView.Renderer`. `onSurfaceCreated` probes
  capabilities and rebuilds shader programs and meshes (context-loss strategy: never assume a
  previous context's objects exist). `onDrawFrame` clears to the identity dark color, draws the
  reference grid, then every renderable item from the current snapshot with the unlit solid or
  vertex-color program. Zero allocations per frame: all matrices are thread-confined scratch
  fields.
- **Meshes**: one `Mesh` per `MeshKind` (triangle, plane, cube, sphere, cylinder, pyramid,
  wire cube) is cached in the renderer; each is an interleaved pos+color VBO with an IBO and a
  VAO. Non-visual Nink types (groups, empties) produce no items; characters map to a cube,
  cameras and lights to a pyramid.
- **Shaders**: two builtin programs (unlit solid; vertex-color). `ShaderProgram` reports
  compile/link errors through the log + an initialization error surfaced by the UI, and caches
  uniform locations.
- **Capabilities**: `GLCapabilities` probes the actual GL version and produces diagnostics used
  by the editor (e.g. the "OpenGL ES 3.0 not supported" screen on incompatible devices).
- **Stats**: `FrameStats` computes FPS and frame time; the editor overlay reads it for the
  `"%1$d FPS · %2$.1f ms"` display.
- **Selection highlight** is part of the snapshot (selection key + world matrix), rendered as
  a wire cube outline — not as viewport picking (picking is PLANNED).

## 4. Scene system

### 4.1 The Nink model

A **Nink** is anything that exists in a scene. It carries: stable `NinkId` (UUID string in a
`@JvmInline value class`), display `name` (never identity), `NinkType` (14 types:
`MESH_CUBE/SPHERE/PLANE/CYLINDER/TRIANGLE`, `CHARACTER_PLAYER/NPC`, `GROUP/GROUP_ENVIRONMENT`,
`CAMERA`, `LIGHT_DIRECTIONAL/POINT/SPOT`, `EMPTY/CUSTOM`), a `Transform` (position, Euler
rotation in radians, scale — relative to parent), a component list (sealed
`NinkComponent`: `Camera` or `Light`), parent/children links, and flags:

| Flag | Semantics |
|---|---|
| `active` | Disables the nink **and its entire subtree**. |
| `visible` | Hides **only the nink itself** (children remain visible). |
| `locked` | Cannot be deleted or reparented-into; duplicates start unlocked. |

Groups are organizational only — they never force behavior on children. The editor viewport
camera (`CameraController`) is separate from the scene main camera; `Scene.mainCameraId` stores
the active camera for future play mode.

### 4.2 Registry invariants

`NinkRegistry` is the single source of truth for scene structure. It maintains:

1. every nink id is unique,
2. parent/child references are consistent in both directions,
3. the hierarchy is acyclic (reparenting into the nink's own subtree is rejected),
4. child ordering is stable and explicit.

Mutation primitives (`add`, `removeSubtree`/`restoreSubtree`, reparent, reorder) enforce these
invariants and are invoked **only** by commands. `removeSubtree` returns a `RemovedSubtree`
record (pre-order list, original parent, original index) that enables exact undo of deletions.

### 4.3 Command system

All editor mutations flow: **panels → `SceneEditor` → `CommandStack` → command → registry →
`Scene.notifyChanged`**.

- `SceneCommand` encapsulates `execute` + `undo`; commands are deterministic, do no IO and no
  UI work, and store everything needed to reverse themselves (e.g. `DeleteNink` keeps the
  removed subtree record and whether it contained the main camera; `ReparentNink` preserves
  world transforms via `Transform.decomposeFrom`).
- `CommandStack` keeps a 128-entry undo history, clears the redo stack on new commands, and
  supports **burst coalescing**: `CoalescingCommand`s executed back-to-back with the same
  coalesce key merge into one undo entry (used for continuous Inspector dragging/typing of
  transforms).
- `NinkCommands` (Phase 2 set): `AddNink`, `AddSubtree` (duplicate/prefab instantiation),
  `DeleteNink` (exact restore), `RenameNink`, `SetTransform`, `SetFlags`, `ReparentNink`
  (world preservation), `ReorderNink`, `SetMainCamera`, `Batch`.
- `SceneEditor` is the only facade panels use; it validates (unique names via `SceneOps`,
  locked-nink rules), attaches default components (camera/light per type), and delegates to the
  command stack.
- Undo/redo caveats (**IMPLEMENTED behavior**): flags and transforms are undoable; **component
  edits (camera/light values) are live writes and not undoable**.

### 4.4 Event flow

`Scene.notifyChanged(reason, ninkId)` with `SceneChangeReason` ∈ {`STRUCTURE`, `TRANSFORM`,
`FLAGS`, `SCENE_REPLACED`} is the single notification path. Subscribers:

| Consumer | Reaction |
|---|---|
| `EditorViewModel` | Bumps `structureRevision` / `transformRevision` (Compose resync keys), rebuilds hierarchy rows, marks dirty, schedules autosave, republishes `RenderFrameData`. |
| GL viewport | Consumes the newly published snapshot next frame (no direct listener). |
| Persistence | Dirty flag drives the debounced autosave + save-on-exit flush. |

## 5. Persistence

All data lives in app-private storage:

```
filesDir/
  logs/                          crash reports (EngineRuntime)
  projects/
    <project-uuid>/
      project.json               KenkernProject metadata (ProjectSerializer)
      scenes/
        main.kscene              entry scene (SceneSerializer, format "kenkern-scene" v1)
      prefabs/
        <name>.kprefab           prefab templates (SceneSerializer, format "kenkern-prefab" v1)
```

- `ProjectRepository` performs all IO on `Dispatchers.IO`, writes atomically (write-to-temp +
  rename), validates metadata on load, and raises the typed `ProjectException` hierarchy rather
  than returning partial state. Invalid project directories are skipped with a warning during
  listing.
- Scene files carry `format`, `version`, scene `name`, `mainCamera`, and a pre-order `ninks`
  array (id, parent, type, flags, transform, components, props). File order guarantees parents
  appear before children during load.

### Serialization robustness rules (IMPLEMENTED, deterministic)

`SceneSerializer` never produces an invalid hierarchy:

| Damage on load | Repair |
|---|---|
| Malformed JSON / wrong format / wrong version | Hard failure with `SerializationException`. |
| Unknown `type` name | Nink becomes `EMPTY`. |
| Missing parent reference | Nink is re-attached to root. |
| Duplicate ids | Later duplicates are skipped (logged). |
| Cycles (self/cross references) | Broken deterministically during hierarchy reconstruction. |
| Zero/invalid scale | Repaired to a valid scale. |

`NinkType.fromSerializedName` provides the same defensive mapping (unknown → `EMPTY`).

## 6. Editor UI architecture

- **Single activity** (`MainActivity`) hosts all Compose content; `EditorRoot` switches between
  `HomeScreen` and `EditorScreen`. Configuration changes are declared in the manifest so the
  activity is not recreated (the GL surface and editor state survive rotation).
- **Orientation policy (HOTFIX 0.1.1)**: the manifest pins portrait as the base state; the
  editor requests `SENSOR_LANDSCAPE` on entry and restores portrait on every exit path
  (back button, back gesture, exit button, fatal-load error screen) via a `DisposableEffect`.
  Because config changes do not recreate the activity, scene state, the GL surface host and
  the floating panels survive the rotation intact; the GL surface simply re-creates and the
  renderer rebuilds its context resources.
- **Error containment (HOTFIX 0.1.1)**: opening a project can never crash the process. The
  scene-opening decision lives in the pure `SceneOpenResolver` (missing file → default scene;
  corrupt file → default scene + recorded error + localized banner; unexpected failure →
  `Failed`). `EditorViewModel` logs the real exception, exposes `loadFailed`/`loadErrorMessage`,
  and `EditorScreen` renders a full-screen localized error with a way back to the launcher;
  project files are never touched by the failing load path. Viewport/GL failures surface as
  an in-editor error banner (see §3).
- **State ownership**: `EditorViewModel` (an `AndroidViewModel`) owns the `Scene`, the
  `SceneEditor`/`CommandStack` pair, selection, panel visibility, search, expanded ids, dirty
  and saving flags, prefab names, and error state. `HomeViewModel` owns the project list.
- **Compose ↔ scene resync**: the scene model is not Compose state, so the view model exposes
  `structureRevision` and `transformRevision` counters. Panels key their recomposition on these
  revisions (Inspector reads live scene values keyed by revisions; the hierarchy is a flat
  `HierarchyRow` projection rebuilt on structural changes — never a parallel source of truth).
- **GL in Compose**: the viewport is a `KenkernGLSurfaceView` embedded via `AndroidView`.
  The GL surface sits behind the **opaque** Compose window surface (`setZOrderOnTop(false)`)
  and the editor paints opaque chrome (status-bar strip, top bar, navigation-bar strip),
  leaving the viewport region unpainted — the framework punches that region transparent
  in the window surface (standard SurfaceView contract, see D7), so floating panels render
  above the viewport while the GL content stays visible.
- **Panels** (`editor/panels/`): `HierarchyPanel` (floating, draggable, search, expand/collapse,
  drag-handle reparenting, per-row three-dot menus), `InspectorPanel` (flags, transform,
  camera/light components), `AddNinkMenu` (entries from `NinkCatalog`, an extensible registry,
  plus project prefabs), `NinkContextMenu` (actions filtered by Nink type), `Dialogs`,
  `CommonUi`.
- **Banners/toasts**: transient feedback (e.g. prefab created, scene saved) and persistent
  error banners come from view-model state; all strings are resources.

## 7. Documented architectural decisions

| # | Decision | Rationale |
|---|---|---|
| D1 | **`GLSurfaceView` instead of a custom EGL loop** (this block) | Android's `GLSurfaceView` already provides the EGL config chooser, context management, a dedicated GL thread and correct pause/resume semantics. A custom loop would add surface-lifecycle risk without a concrete requirement yet. `preserveEGLContextOnPause` + full resource rebuild in `onSurfaceCreated` covers context loss. Revisit with Vulkan (PLANNED). |
| D2 | **`org.json` instead of adding a JSON library** | The Android platform already ships `org.json`; adding Gson/Moshi/kotlinx-serialization would add a dependency and reflection/codegen setup for two small serializers. A real `org.json` artifact is added for JVM tests only (`testImplementation("org.json:json:20240303")`) because the `android.jar` stub would otherwise throw `Stub!` in unit tests. Serializers stay pure Kotlin and JVM-testable. |
| D3 | **`NinkId` as a `@JvmInline value class`** over a UUID string | Zero-allocation identity wrapper with type safety (prevents passing arbitrary strings as ids), cheap equality, and direct JSON serialization of the UUID string. |
| D4 | **Commands instead of direct scene mutation** | Undo/redo, single notification path, and main-thread confinement fall out of the same mechanism. Commands make every mutation reversible and testable (`CommandStackTest`, 12 tests) without UI. |
| D5 | **Single Euler convention** `R = Rz(z)·Ry(y)·Rx(x)` (X then Y then Z) | One convention across math, serialization, Inspector (degrees) and tests eliminates convention drift between systems; `Transform.decomposeFrom` is implemented to invert exactly this composition. |
| D6 | **Immutable `RenderFrameData` snapshots for UI → GL hand-off** | The GL thread must never observe live scene objects mid-mutation; a volatile pointer swap is lock-free, allocation-light (built only on change, not per frame) and trivially safe. |
| D7 | **Opaque window + SurfaceView punch-hole + `setZOrderOnTop(false)`** (revised in 0.1.2) | Compose panels float above the viewport because the GL surface renders *behind* the window surface and the editor leaves the viewport region unpainted. The window is **opaque** (`windowIsTranslucent=false`, opaque `#0E0E10` background): the framework's standard punch-hole makes the unpainted viewport region transparent — the same contract every video/GPU app uses. History: pre-0.1.1 used a transparent background without a translucent window (viewport hidden, fragile); 0.1.1 switched to a translucent window (viewport visible, but a non-standard compositing path and a known driver hazard on low-end Unisoc/Mali Android 13 — process death *after* frames render). 0.1.2 reverted to the standard opaque punch-hole: on a broken driver the worst case is a dark viewport, not a crash. |
| D8 | **Registry as single source of truth + flat `HierarchyRow` projection** | UI trees drift; a projection rebuilt from the registry on `STRUCTURE` changes keeps one authoritative structure with no parallel hierarchy to invalidate. |
| D9 | **Version-checked file formats** | Scene/prefab files carry `format` + `version` (currently 1) and are rejected loudly on mismatch, so future format changes can migrate instead of silently corrupting scenes. |
| D10 | **No per-frame allocations in the renderer** | Mobile GC pauses are visible in frame time; scratch matrices, cached meshes and cached shader programs keep the GL loop allocation-free. |
| D11 | **Orientation via `requestedOrientation`, not two activities** (HOTFIX 0.1.1) | The launcher is portrait, the editor landscape. Requesting orientation on the single activity + declared `configChanges` avoids any activity recreation, so scene state, undo history and the GL surface survive the transition; a second activity would add state-transfer risk for zero benefit in a hotfix. |
| D12 | **Defense-in-depth around the GL thread** (HOTFIX 0.1.1) | EGL failures inside `GLSurfaceView`'s GL thread are process-fatal by platform design. KENKERN therefore layers: UI-thread `EGLPreflight` (config availability before creation) → fallback config chooser → guarded surface/renderer creation → guarded renderer callbacks → error banner + crash-report file. Any layer that fails degrades to the next instead of crashing. |
| D13 | **Forensic layer for uncatchable process deaths** (0.1.2) | Native GL-driver/compositor crashes, ANR and low-memory kills cannot be caught by any Java handler, and 0.1.1 proved that stack-free diagnosis invites wrong fixes. KENKERN therefore records the full execution trail (`CrashDiag`, tag `KENKERN_CRASH_DIAG`, embedded in crash reports) and reads **why the previous process actually died** via `ApplicationExitInfo` (`ExitReasonProbe`, API 30+), persisting the reason + platform trace to `files/logs/` and surfacing it in a localized home-screen banner — real device evidence without adb. Instrumentation only records; it never alters control flow or masks failures. |

## 8. Extension points for future blocks

These seams exist in the current code specifically so PLANNED blocks can attach without
rewriting the core (the systems themselves are NOT implemented):

| Extension point | Intended future use |
|---|---|
| `NinkComponent` sealed interface + `components` list on `Nink` | New components (rigidbody, audio source, script, mesh reference) with the same serialization pattern (`kind`-tagged JSON). |
| `NinkCatalog.register(...)` | New Nink types / custom entries appear in the Add menu without touching panel code. |
| `NinkType` (append-only enum + `fromSerializedName` fallback) | Additional types serialize compatibly; unknown types degrade to `EMPTY`. |
| `MeshKind` + `MeshFactory` + `BuiltinShaders` | New mesh families and shader programs plug into the renderer's caches; custom meshes/asset imports extend `MeshFactory`. |
| `RenderFrameData` / `RenderFramePublisher` | Richer frame payloads (gizmos, lights, cameras, picking metadata) ride the same snapshot channel. |
| `SceneCommand` / `CoalescingCommand` | New operations (component undo/redo, gizmo drags) become new command classes; undo history and notifications work automatically. |
| `EngineRuntime` | Subsystem registration hook for future engine-wide services (physics, audio, scripting). |
| `EngineConfig` runtime tunables | Editor settings surface (later blocks) reads/writes these instead of scattering constants. |
| Scene format `version` field | Migration path: bump `FORMAT_VERSION`, write a migration, keep old files loadable. |
| `Scene.mainCameraId` | Play-mode camera activation when a play/runtime mode is built. |
| `GLCapabilities` diagnostics | Device feature gating for future renderer features. |
