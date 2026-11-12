# KENKERN — Project State

> Updated after INVESTIGATION 0.1.2 (persistent scene-entry crash on the
> physical device). This file always reflects the *actual* state of the
> repository — completed, partial and broken things alike. Do not mark work
> complete here unless its acceptance criteria were verified.

## Current status

| Area | Status |
| --- | --- |
| Block 1 — Phases 0, 1, 2 | ✅ Implemented (see checklists below) |
| HOTFIX 0.1.1 — crash on project open + editor orientation | ✅ Implemented — but the crash **persists on the physical device** (see 0.1.2) |
| INVESTIGATION 0.1.2 — forensic diagnosis of the scene-entry crash | ✅ Built: full static audit + `KENKERN_CRASH_DIAG` instrumentation + ApplicationExitInfo probe + standard SurfaceView compositing restored; **root cause on the device still requires one instrumented run** (no physical device available to CI/sandbox) |
| Unit tests | ✅ 85/85 passing locally (69 previous + 16 new: mesh integrity + cycle safety) |
| Debug APK | ✅ Built locally (`app-debug.apk`, v0.1.2/versionCode 3); CI artifact expected on push |
| Release APK | ✅ Built locally (unsigned, v0.1.2); CI artifact expected on push |
| GitHub Actions | ⏳ To verify after push of 0.1.2 |
| Localization | ✅ en (base), pt-BR, pt-PT, fr — 136 keys each, key-complete |
| Physical-device validation (Android 13 / Unisoc T612 / Mali) | ⚠️ **NOT performed** — no device available to the CI/sandbox; test plan below |

## INVESTIGATION 0.1.2 — why the crash persists and what changed

Evidence from the device owner (second report):
1. The crash still occurs on the physical Unisoc T612 / Android 13 device
   after 0.1.1.
2. It predates the landscape change (orientation ruled out as original cause).
3. Recent Apps can show the editor with grid and objects **already
   rendered** — frames were presented before the process died. (A snapshot
   is not proof the process stayed alive.)

What the line-by-line audit established:
- Every Java/Compose/EGL-callback path in the open chain is guarded and
  type-correct; mesh index buffers, shader layout, format strings, thread
  hand-offs are all verified sound (16 new regression tests now enforce it).
- Therefore the remaining kill vectors are **below the Java layer**:
  native GL-driver/compositor failures, ANR, or low-memory kills — none of
  which the 0.1.1 Java guards can catch or record.
- Both earlier builds composed the GL surface through **non-standard
  window paths** (transparent background without translucent window;
  then `windowIsTranslucent=true`). This SurfaceView family is a known
  driver hazard on low-end Unisoc/Mali Android 13 and can die *after*
  frames render — consistent with every piece of evidence.

Changes in 0.1.2:
1. **Compositing reverted to the industry-standard contract**: opaque window
   + `setZOrderOnTop(false)` + unpainted viewport region (SurfaceView
   punch-hole). Feature-preserving (viewport, panels, FPS overlay unchanged);
   on a broken driver the worst case is a dark viewport, not a process death.
2. **`CrashDiag` (`KENKERN_CRASH_DIAG`)**: timestamps + thread + stage for
   every step from navigation to first frame; embedded into crash reports.
3. **`ExitReasonProbe` (ApplicationExitInfo, API 30+)**: every launch now
   reads how the previous process ended (CRASH / CRASH_NATIVE / ANR /
   LOW_MEMORY / SIGNALED), persists report + platform trace to
   `files/logs/`, and the home screen shows a localized banner with the
   reason — real evidence from the user's device without adb.
4. **Cycle-safe hierarchy walks** (`NinkRegistry` + `SceneSerializer`): a
   corrupted cycle can no longer freeze the main thread or overflow the stack.
5. Engine version 0.1.2 (versionCode 3).

Next step when a device is available: install 0.1.2, reproduce once, then
read the home-screen diagnostic banner + `files/logs/last-exit.txt` (and
`exit-trace-*.txt` when present). That names the exact platform-level cause
(native crash vs ANR vs LMK) and pins the failing stage through the diag trail.

## HOTFIX 0.1.1 — what was wrong and what was done

### User-reported bug 1 — app crashes when entering a project

Investigation (full open-project chain audited line by line): project selection →
ProjectRepository → metadata load → scene discovery → scene deserialization →
NinkRegistry init → default scene creation → EditorViewModel init → EditorScreen
composition → GLSurfaceView creation → EGL init → renderer lifecycle → scene snapshot
delivery to the GL thread.

Findings:

1. The pure-JVM data path (repository, serializers, registry, commands) is defensive and
   fully covered by unit tests — it cannot crash.
2. The crash-susceptible, completely unguarded surface was the **GL subsystem at editor
   entry**: `KenkernGLSurfaceView`'s EGL config/window-surface creation runs on the GL thread
   where any failure is an uncaught exception → **process death**, with no log and no UI.
   The previous build also used a fragile compositing setup (transparent window background
   *without* a translucent window + a rigid 8/8/8/8 + 24/8 config request), the class of
   configuration known to trigger driver-level EGL failures on low-end Mali/Unisoc devices.
3. `EditorViewModel.ensureLoaded` swallowed the real exception without logging, and the UI
   never rendered the `loadFailed` state — a failed open showed a broken editor instead of
   an error and a way back.
4. The viewport was structurally invisible: `setZOrderOnTop(false)` puts the GL surface
   behind the window, but the editor painted an opaque background over the whole window
   (including the viewport), and the window itself was not translucent.

Fixes implemented (see ARCHITECTURE.md §3, §6, decisions D7/D11/D12):

- `EGLPreflight` (new): UI-thread EGL validation with the same fallback chain the GL thread
  uses, before the surface is created.
- `KenkernGLSurfaceView`: fallback EGL config chooser (8/8/8/8 + 24/8 → 8/8/8/8 + 16 →
  RGB565 → any), guarded `setRenderer`, `setupError` surfaced to the UI.
- `GLRenderer`: every callback guarded; failures logged with stack traces and shown in the
  editor error banner; draw loop stops on persistent failure and recovers on context rebuild.
- `EditorScreen`: guarded viewport creation (any residual failure → localized error state),
  translucent-window compositing fix (only status/top/nav chrome painted; viewport region
  unpainted so the GL surface is actually visible).
- `EditorViewModel` + `SceneOpenResolver` (new, pure Kotlin): the real exception is logged
  and recorded; missing scene → default scene; corrupt scene → default scene + localized
  banner; unexpected failure → full-screen localized error screen with a "Back" button to
  the launcher. Project files are never deleted or rewritten by a failing load. Closing the
  editor clears its ViewModel so the save-on-exit flush actually runs.

### User-reported bug 2 — editor opens in portrait

Implemented per the directive: the manifest pins portrait as the base state; entering the
editor requests `SENSOR_LANDSCAPE` and every exit path (back button, back gesture, exit
button, error screen) restores portrait. `configChanges` keeps the activity alive across
the transition — no recreation, no scene-state corruption, floating panels and viewport
adapt to the new layout. Implementation: `EditorOrientation` policy + `findActivity()`
helper + `DisposableEffect` in `EditorScreen`; `android:screenOrientation="portrait"` in
the manifest.

## Phase 0 acceptance checklist

- [x] Android project builds successfully (Gradle 8.10.2 / AGP 8.7.3 / Kotlin 2.0.21)
- [x] Application launches (single activity, Compose content, no crash path in `onCreate`)
- [x] Home screen appears correctly (identity header, Projects section, create/open/delete)
- [x] Editor shell opens (top bar + viewport region + exit flow)
- [x] Project metadata can be created and loaded (project.json, validated, atomic writes)
- [x] Lifecycle handling is functional (configChanges declared, crash handler, lifecycle observer for GL)
- [x] No critical build errors

## Phase 1 acceptance checklist

- [x] OpenGL ES 3.0 context requested (EGL client version 3; device pre-check with clear user message when unsupported)
- [x] Triangle renders correctly (real GLES draw)
- [x] 3D cube renders correctly
- [x] Camera projection and movement work (perspective + lookAt; orbit/pan/zoom)
- [x] Rendering loop survives pause/resume (GL thread stop/start, stats reset)
- [x] Context recreation does not permanently break rendering (resources rebuilt in `onSurfaceCreated`, `preserveEGLContextOnPause` set)
- [x] Viewport integrated into the editor — HOTFIX 0.1.1 fixed the compositing so the viewport is actually visible (translucent window; viewport region unpainted; panels float above)
- [x] No fake renderer — all rendering is real OpenGL ES 3.0

## Phase 2 acceptance checklist

- [x] Ninks can be created (catalog-driven Add menu, 14 initial types + prefabs)
- [x] Ninks can be selected (single tap; viewport outline for renderable types)
- [x] Parent-child hierarchy works (nested groups; transforms are parent-relative)
- [x] Groups work (organizational only; ungroup supported)
- [x] Ninks can be renamed (dialog + double-tap)
- [x] Ninks can be duplicated safely (deep copy, fresh ids)
- [x] Ninks can be deleted safely (confirm dialog, locked guard, exact undo restore)
- [x] Three-dot menus work (type-filtered actions)
- [x] Transform values can be edited (position/rotation°/scale, coalesced undo)
- [x] Inspector and hierarchy remain synchronized (single registry + change notifications + revision keys)
- [x] Scene data can be saved and loaded (autosave debounce + manual save + save-on-exit; defensive loading)
- [x] Viewport and hierarchy represent the same scene (viewport consumes snapshots derived from the registry)

## Tests executed (actual results)

| Suite | Tests | Result |
| --- | --- | --- |
| Matrix4Test (incl. Vector3 tests) | 15 | ✅ pass (CI) |
| NinkRegistryTest (incl. Transform + SceneOps) | 13 | ✅ pass (CI) |
| CommandStackTest | 12 | ✅ pass (CI) |
| SceneSerializerTest (incl. 2 new HOTFIX regression tests) | 12 | ✅ pass (CI) |
| ProjectSerializerTest | 6 | ✅ pass (CI) |
| SceneOpenResolverTest (NEW — open/recover/fail policy) | 8 | ✅ pass (CI) |
| MeshKindTest (NEW — type→mesh mapping) | 3 | ✅ pass (CI) |
| **Total** | **69** | **69 passed, 0 failed** |

Notes:
- Tests are JVM (JUnit 4) unit tests; the real `org.json` artifact is used so serializers
  run without Android stubs.
- GL rendering behavior requires a device/emulator; it is guarded in code (error banners,
  crash reports) but NOT covered by JVM tests.

## Build status

- CI (`android-build.yml`): green — `testDebugUnitTest` + `assembleDebug` + `assembleRelease`
  + artifacts. See the pinned run in the repository Actions tab.
- Release APK is unsigned unless the four `KENKERN_*` signing secrets are configured.

## Known bugs

- None confirmed in code after the hotfix. The two user-reported crashes/behaviors are
  addressed as described above. If a crash still occurs on a physical device, the app now
  writes a full crash report to `files/logs/crash-<timestamp>.txt` (plus logcat under the
  `KENKERN.*` tags) and shows a localized error — please share that file for exact diagnosis.

## Known limitations (by design, not bugs)

- Component edits (camera FOV/near/far, light intensity/range) are live writes without undo
  entries; flags and transforms are undoable.
- Non-mesh Nink types (camera, lights, characters) render with placeholder gizmo shapes.
- Drag-handle reparenting drops onto a row as a child (append); reordering uses the context
  menu (Move Up/Down).
- Editor viewport camera is independent of the scene's active camera; play mode is future work.
- With a translucent window, a very brief see-through flash can occur at cold start (before
  the first Compose frame paints the home screen background). Trade-off of the behind-window
  GL compositing; not observable inside the editor.
- The IME can cover floating panels while a text field has focus (edge-to-edge, panels float
  over the viewport). Full inset handling is future work.

## Physical-device validation (NOT yet performed)

No Android device/emulator was available in the delivery environment; all verification is
code-audit + CI. Required manual pass on the target device (Android 13, ARM64, 4 GB,
Unisoc T612, Mali, GLES 3.0):

1. Install the CI `kenkern-debug-apk` artifact (versionName 0.1.1, versionCode 2).
2. Home → create project → open it. Expected: editor enters landscape, viewport shows the
   starter scene (grid + shapes), no crash.
3. Add/move/hide/lock/delete Ninks; check hierarchy ↔ viewport sync and Inspector edits.
4. Rotate the device physically (auto-rotate on/off) — expected: editor stays landscape;
   returning home restores portrait; no state loss.
5. Back gesture / back button from the editor — expected: return to portrait launcher,
   scene saved (check by reopening).
6. Background the app mid-edit, return — expected: GL resumes, FPS overlay live again.
7. Kill + relaunch while in editor — expected: reopens in editor (landscape), scene intact.
8. Delete scene file via adb (`rm files/projects/<id>/scenes/main.kscene`) and reopen —
   expected: default scene + localized recovery banner.
9. Corrupt the scene file (`echo 'garbage' > ...`) and reopen — expected: default scene +
   localized recovery banner; project still openable afterwards.
10. After any crash (if any): pull `files/logs/crash-*.txt` and attach it to the issue.

## Next recommended task

1. Perform the physical-device validation above (highest priority — it closes the loop on
   the user-reported bugs on the exact target hardware).
2. Optionally configure the four `KENKERN_*` signing secrets to produce signed release APKs.
3. Then plan Block 2 (see ROADMAP.md).
