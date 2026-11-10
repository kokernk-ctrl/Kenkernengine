# Changelog

All notable changes to the KENKERN Engine are documented here.
The format follows Keep a Changelog principles; the project has not yet
adopted semantic versioning for public APIs (engine version: 0.1.0).

## [0.1.0] — Block 1: Phases 0, 1 and 2 (initial implementation)

The repository was empty when this block started; everything below is the
initial implementation, delivered in three phase-scoped commits.

### Phase 0 — Project foundation
- Android Gradle project: Kotlin 2.0.21, Jetpack Compose (Material3,
  Compose BOM 2024.12.01), AGP 8.7.3, Gradle 8.10.2 wrapper, compileSdk/
  targetSdk 35, minSdk 26, JDK 17.
- Package architecture prepared: `core`, `editor`, `rendering`, `scene`,
  `math`, `input`, `project`, `serialization`, `localization`.
- Application core: `KenkernApplication`, single-activity `MainActivity`,
  `EngineConfig` (centralized configuration), `EngineLogger` (facade +
  ring buffer), `EngineRuntime` (init + crash reports to app-private logs).
- KENKERN home screen: identity header, Projects section with
  create/open/delete workflow, loading/error/empty states.
- Editor shell: top bar, viewport region, exit flow.
- Project persistence: `project.json` metadata model, atomic writes,
  strict validation, typed exceptions for missing/corrupt data.
- Localization system: Android string resources with English base
  (127 keys) and locales pt-BR, pt-PT, fr; identifiers never translated.
- Visual identity: dark #0E0E10 background, white text, #007BFF accent,
  adaptive launcher icon.
- GitHub Actions workflow (`android-build.yml`): JDK 17, Android SDK,
  Gradle validation, unit tests, Debug + Release APK artifacts, optional
  secure release signing from GitHub secrets, failure summaries.

### Phase 1 — Graphics foundation
- OpenGL ES 3.0 rendering subsystem: `GLSurfaceView` host (EGL client
  version 3, RGBA8 + 24-bit depth), device capability pre-check with a
  clear unsupported-GPU message, runtime GL version probe and diagnostics.
- Context-loss strategy: all GPU resources rebuilt on
  `onSurfaceCreated`; `preserveEGLContextOnPause` enabled; rendering
  lifecycle wired to the Android lifecycle (pause/resume).
- Controlled render loop with FPS/frame-time statistics (`FrameStats`)
  and zero per-frame allocations (thread-confined scratch matrices).
- Shader system: `ShaderProgram` with compile/link error reporting,
  cached uniforms; built-in unlit solid + vertex-color programs
  (GLSL ES 3.00, fixed attribute locations).
- Mesh system: interleaved VBO/IBO/VAO static meshes; procedural
  triangle, plane, cube, sphere, cylinder, camera pyramid, reference
  grid (faded lines + colored axes) and selection wire cube; baked
  directional shading for unlit readability.
- Math foundation: `Vector3`, column-major `Matrix4` (multiply with
  aliasing safety, general inverse, perspective, lookAt, TRS with the
  single documented Euler convention R = Rz·Ry·Rx).
- Editor orbit camera (yaw/pitch/distance/target) with view matrix
  computation on the GL thread; touch input: one-finger orbit,
  two-finger pan, pinch zoom.
- Viewport integration: GL surface behind a transparent Compose window
  so floating panels composite above it; FPS + GL summary overlay;
  renderer init errors surfaced in the UI.

### Phase 2 — Nink system
- Nink data model: `NinkId` (stable UUID identity), `Nink` (name, type,
  transform, components, parent/children, active/visible/locked, custom
  properties), `NinkType` (14 initial types across six categories),
  `NinkComponent` (sealed: Camera, Light).
- `NinkRegistry`: single source of truth with enforced invariants
  (unique ids, acyclic hierarchy, consistent two-way links, stable
  ordering) and world-matrix composition.
- Command system: `CommandStack` (undo/redo, 128 entries, burst
  coalescing for continuous transform edits) + commands for add,
  add-subtree, delete-with-exact-restore, rename, transform, flags,
  reparent-with-world-preservation, reorder, set-main-camera, batch
  (ungroup). `SceneEditor` is the single mutation facade.
- Dynamic floating Hierarchy panel: draggable, scene name, Add Nink,
  search with subtree matching, expand/collapse (per-node + all),
  selection highlighting, visual states (hidden/inactive/locked),
  drag-handle reparenting, per-row three-dot context menus with
  type-filtered actions (rename, duplicate, add child, move up/down,
  move to group, ungroup, set active camera, hide/show, lock/unlock,
  create prefab, delete).
- Add Nink menu: category-driven and extensible via `NinkCatalog`
  (3D Objects, Characters, Groups, Cameras, Lighting, Other) plus a
  project Prefabs section; target hint (root or under selection).
- Inspector panel: live name/type/active-camera badge, Active/Visible/
  Locked switches, transform editing (position, rotation in degrees,
  scale) with two-way sync and coalesced undo, camera (FOV/near/far)
  and light (intensity/range) component editing.
- Scene serialization: JSON format v1 for scenes and prefabs; defensive
  loading (unknown types → EMPTY, missing parents → root, duplicate ids
  skipped, cycles broken, zero scales repaired); prefab export and
  instantiation with fresh ids; default starter scene with localized
  names; autosave (debounced 1.5 s) + manual save + save-on-exit.

### Tests
- 56 JVM unit tests (JUnit 4) covering vector/matrix math (including
  invert roundtrip and TRS↔decompose), registry invariants, command
  undo/redo and coalescing, world-preserving reparenting, ungroup,
  scene/project (de)serialization and corruption handling. All passing.

### Fixed during development
- Kotlin nested block-comment gotchas in documentation comments.
- `NinkRegistry.removeSubtree`/`restoreSubtree`: stale child id lists
  caused duplicated children after delete→undo.
- `SceneSerializer.deserialize`: HashMap ordering could add children
  before their parents; now uses insertion-ordered map (file order).
- Localized delete-confirmation suffix could lose its leading space
  after Android string trimming; wrapped in quotes.
