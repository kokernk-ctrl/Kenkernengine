# ROADMAP.md

Legend:

- **IMPLEMENTED** — the code exists in this repository, is covered by tests or verified builds,
  and matches the current engine version (0.1.0).
- **PLANNED ONLY** — **not started. No code exists for these items.** They are recorded here so
  contributors do not implement them prematurely and do not mistake the list for existing
  functionality.

---

## Block 1 — IMPLEMENTED

Block 1 is the initial implementation of the repository (the repository was previously empty).
It covers Phases 0, 1 and 2.

### Phase 0 — Foundation (IMPLEMENTED)

| Deliverable | Summary |
|---|---|
| Engine core | `EngineConfig` centralized configuration; `EngineLogger` facade with ring buffer; `EngineRuntime` init + crash reports to `filesDir/logs`. |
| Math library | `Vector3`, `Matrix4` (column-major; multiply/invert/perspective/lookAt/TRS; single Euler convention). |
| Project layer | `KenkernProject` metadata; `ProjectRepository` with atomic writes and validation; typed `ProjectException` hierarchy. |
| Serialization | `ProjectSerializer` and `SceneSerializer` (scene + prefab JSON) with defensive loading. |
| Home screen | Project list, create and delete flows (Compose). |

### Phase 1 — Graphics (IMPLEMENTED)

| Deliverable | Summary |
|---|---|
| GL surface | `KenkernGLSurfaceView`: EGL ES3 context, RGBA8 + 24-bit depth, context preservation on pause. |
| Renderer | `GLRenderer` (GLES30): context-loss resource rebuild, zero per-frame allocations, FPS/frame-time stats. |
| Meshes | `Mesh` (VBO+IBO+VAO, interleaved pos+color); `MeshFactory` primitives with baked unlit shading (triangle, plane, cube, sphere, cylinder, pyramid, grid, wire cube). |
| Shaders | `ShaderProgram` (compile/link with error reporting, uniform cache); builtin unlit solid + vertex-color programs. |
| Camera & input | Editor orbit camera; 1-finger orbit, 2-finger pan, pinch zoom. |
| Diagnostics | `GLCapabilities` version probe + diagnostics; unsupported-device screen. |

### Phase 2 — Nink system (IMPLEMENTED)

| Deliverable | Summary |
|---|---|
| Scene model | `Nink`, `NinkId` (UUID value class), 14 `NinkType`s, `NinkComponent` (Camera/Light), `Transform` with world-preserving reparent. |
| Registry | `NinkRegistry`: single source of truth; unique ids, acyclic hierarchy, consistent links, stable ordering. |
| Commands | `CommandStack` (undo/redo, 128-entry history, burst coalescing) + `NinkCommands` (add, add-subtree, delete with exact restore, rename, transform, flags, reparent, reorder, main camera, batch). |
| Editor facade | `SceneEditor` for all mutations; `SceneOps` (copySubtree, uniqueName); `SceneFactory` default scene. |
| Editor UI | Hierarchy panel (search, expand/collapse, drag-handle reparenting, per-row menus); Inspector (flags + transform + camera/light components); Add-Nink menu (NinkCatalog + prefabs); type-filtered context menus; dialogs; FPS overlay; undo/redo/save/panel toggles. |
| Persistence UX | Autosave (debounced 1.5 s) + save-on-exit flush; prefab create/instantiate. |
| Localization | en (base), pt-BR, pt-PT, fr string resources; no hardcoded UI strings. |
| Tests | 56 JVM unit tests (math, registry, transforms, scene ops, command stack, serializers). |

**Known Block 1 boundaries (by design, not defects):** component edits (camera/light values)
are live writes without undo; viewport picking is absent (selection happens via the Hierarchy
panel); prefab support covers create/instantiate only.

---

## Block 2 — PLANNED ONLY (NOT STARTED, NO CODE EXISTS)

Candidates recorded for planning. Nothing below has any implementation in this repository.

| Candidate | Notes |
|---|---|
| Gizmos / viewport picking | Tap-select and drag-manipulate Ninks in the viewport (currently selection is Hierarchy-panel-only). |
| Camera/light gizmo drawing | Dedicated viewport gizmos for cameras and lights (they currently render as generic pyramid meshes). |
| Advanced undo UX | Richer undo affordances beyond the top-bar undo/redo (e.g. grouped operations surfaced in UI). Component undo/redo would also be addressed here — today component edits are live writes. |
| Asset import | Bringing external assets into projects (today the engine uses procedural primitives only). |

---

## Block 3+ — PLANNED ONLY (NOT STARTED, NO CODE EXISTS)

Longer-term candidates in no committed order. None of these has any implementation.

| Candidate | Notes |
|---|---|
| Physics | Collision and rigid-body simulation; would attach via a new subsystem registered at `EngineRuntime` and new `NinkComponent`s. |
| Animation | Skeletal/keyframe animation; requires asset pipeline groundwork. |
| Audio | Sound playback subsystem. |
| Scripting | User-authored behavior attached to Ninks. |
| Vulkan backend | A second rendering backend. Vulkan is explicitly a **future** backend; the current renderer is OpenGL ES 3.0 only. |
| KENKERN Intelligence | Planned intelligence features of the product; no design or code exists yet. |
| Prefab editing UI | Beyond today's create/instantiate: editing and re-syncing prefab contents. |
| Play mode | Using `Scene.mainCameraId` (already stored) to run scenes through the active camera. |

---

## Tracking rules

- The authoritative status of the repo is the code plus `STATE.md`/`CHANGELOG.md` maintained
  after each change; this file records the high-level trajectory only.
- A PLANNED item becomes IMPLEMENTED only when its code is merged, tests pass, and the change
  is validated (see [CONTRIBUTING_AI.md](CONTRIBUTING_AI.md) — never mark untested work as
  complete).
