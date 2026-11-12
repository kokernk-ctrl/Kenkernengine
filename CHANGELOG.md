# Changelog

All notable changes to the KENKERN Engine are documented here.
The format follows Keep a Changelog principles; the project has not yet
adopted semantic versioning for public APIs (engine version: 0.1.2).

## [0.1.2] — FORENSIC INVESTIGATION: persistent scene-entry crash on device

User-reported after 0.1.1: the app still terminates when entering the
scene editor on the physical target device (Android 13 / Unisoc T612 /
Mali / GLES 3.0), and the Recent-Apps snapshot can show the editor with
grid and objects already rendered — evidence that frames were presented
before the process died. The crash predates the orientation change, so
landscape was ruled out as the original cause.

### Investigation findings (static audit, no device available)
- The complete open-project chain (navigation → repository →
  deserialization → registry → ViewModel → Compose → GLSurfaceView → EGL →
  renderer → shaders → meshes → first frame) was audited line by line.
  All Java/Compose paths are guarded and type-correct; mesh index data,
  shader sources/layout, format strings and thread hand-offs are verified
  sound (now enforced by 16 new JVM regression tests).
- The kill vector that matches all evidence is **below the Java layer**:
  native process death (GL driver / compositor / ANR / low-memory kill).
  Java guards from 0.1.1 cannot catch or record these.
- Both previous builds (pre-0.1.1 and 0.1.1) used **non-standard
  window/GLSurfaceView compositing** (transparent background without
  translucent window; then a translucent window). SurfaceView-behind-window
  through those paths is a documented driver hazard on low-end Unisoc/Mali
  Android 13 devices, and can kill the process *after* frames render.

### Changed — compositing reverted to the standard contract
- The window is **opaque** again (`windowIsTranslucent=false`, opaque
  `#0E0E10` window background). The viewport composites through the
  standard SurfaceView punch-hole (surface behind the window, viewport
  region unpainted) — the same mechanism every video/GPU app uses. On a
  broken driver the worst case is a dark viewport instead of a process
  death. No feature was removed; panels, FPS overlay and the viewport all
  keep working.

### Added — forensic instrumentation (KENKERN_CRASH_DIAG)
- `CrashDiag`: single-tag (`KENKERN_CRASH_DIAG`) wall-clock + thread +
  uptime log of every stage: activity lifecycle, orientation requests,
  project load, scene JSON read, deserialize, default scene, editor
  compose, EGL pre-flight, config choice, surface created/changed, shader
  results, mesh build, frames 1–5 and first frame completion. The full
  trail is embedded into crash reports.
- `ExitReasonProbe` (ApplicationExitInfo, API 30+): on every start the app
  reads **how the previous process actually died** (CRASH / CRASH_NATIVE /
  ANR / LOW_MEMORY / SIGNALED / …), saves a report plus the platform-provided
  trace to `files/logs/`, and the home screen shows a localized diagnostic
  banner with the reason — so the user can report the real cause without adb.
- Crash reports now include device identity and the execution trail.
- `EngineRuntime` resets the diag session and runs the exit probe at start.

### Fixed — latent hierarchy-walk hazards (defensive)
- `NinkRegistry.worldMatrix/depthOf/subtreeContains/collectPreOrder` and
  `SceneSerializer.collectPreOrder` are now bounded by visited sets: a
  corrupted parent/child cycle can no longer freeze the main thread (ANR)
  or overflow the stack.

### Tests
- 16 new JVM regression tests (85 total): `MeshIntegrityTest` (9 — every
  mesh's index buffer within vertex range, stride/layout integrity, 16-bit
  index bound) and `NinkRegistryCycleTest` (7 — termination and sane
  results under injected parent/child cycles).
- Localization: +7 keys in en/pt-BR/pt-PT/fr (136 per locale).
- Engine version 0.1.2 (versionCode 3).

### Known limitation
- Physical-device validation was **not** performed (no device/emulator
  available to CI/sandbox). The 0.1.2 APKs are the instrumented
  investigation builds: if the crash persists, the exit-reason banner and
  `files/logs/` reports will name the exact cause on the next run.

## [0.1.1] — HOTFIX: crash on project open + editor orientation

User-reported: (1) the app closed automatically when entering the scene editor
after creating a project; (2) the editor opened in portrait instead of
landscape.

### Fixed — crash containment around the GL subsystem
- New `EGLPreflight`: validates EGL config availability on the UI thread with
  a fallback chain **before** the GL surface exists, so a broken driver
  surfaces as a localized UI error instead of an uncaught GL-thread exception
  (which terminates the process).
- `KenkernGLSurfaceView`: rigid single EGL spec replaced by a fallback chooser
  (RGBA8 + D24 + S8 → RGBA8 + D16 → RGB565 → any window config); `setRenderer`
  guarded with the error surfaced to the editor UI.
- `GLRenderer`: all three `GLSurfaceView.Renderer` callbacks exception-guarded;
  failures are logged with full stack traces and shown in the existing viewport
  error banner; the draw loop halts on persistent failure and recovers on
  context recreation.
- Viewport creation inside the editor is fully guarded; any residual failure
  renders an opaque error state with the actual exception detail.

### Fixed — viewport compositing
- The window is now translucent (`windowIsTranslucent`) and the editor paints
  opaque chrome only (status-bar strip, top bar, navigation-bar strip),
  leaving the viewport region unpainted — the behind-window GL surface is
  actually visible now (previously it was permanently covered by an opaque
  Compose background, and the window/EGL combination was the fragile
  configuration most likely responsible for driver-level EGL failures).

### Fixed — project-opening error handling
- `EditorViewModel.ensureLoaded` no longer swallows exceptions silently: the
  real exception is logged and recorded.
- New pure `SceneOpenResolver` (unit-tested) decides missing/corrupt/unexpected
  scene outcomes; corrupt scene data falls back to a fresh default scene with a
  localized recovery banner, and unexpected failures render a full-screen
  localized error screen with a way back to the project launcher. Project
  files are never deleted or rewritten by a failing load.
- Closing the editor now clears its ViewModel so the save-on-exit flush runs
  (previously the flush was dead code because editor ViewModels were scoped to
  the activity lifetime).

### Fixed — orientation
- The launcher is portrait (manifest base state); entering the editor requests
  `SENSOR_LANDSCAPE` and every exit path restores portrait. The activity is
  never recreated (declared `configChanges`), so scene state, undo history,
  the GL surface and floating panels survive the transition.

### Changed
- Engine version bumped to 0.1.1 (versionCode 2).
- Localization: +2 keys in en/pt-BR/pt-PT/fr (`error_editor_open_title`,
  `error_scene_load_recovered`) — 129 keys per locale.

### Tests
- 13 new JVM regression tests (69 total): `SceneOpenResolverTest` (8 — missing
  file, corrupt JSON, wrong format id/version, missing parents, empty
  hierarchy, saved scene, default-scene factory failure), `MeshKindTest`
  (3), and create-then-reopen + empty-hierarchy serializer roundtrips in
  `SceneSerializerTest` (2).

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
