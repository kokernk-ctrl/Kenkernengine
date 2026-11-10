# KENKERN — Project State

> Updated at the end of Block 1 (Phases 0, 1, 2).
> This file always reflects the *actual* state of the repository — completed,
> partial and broken things alike. Do not mark work complete here unless its
> acceptance criteria were verified.

## Current status

| Area | Status |
| --- | --- |
| Phase 0 — Project foundation | ✅ Implemented (see acceptance checklist below) |
| Phase 1 — Graphics foundation | ✅ Implemented (see acceptance checklist below) |
| Phase 2 — Nink system | ✅ Implemented (see acceptance checklist below) |
| Unit tests | ✅ 56/56 passing (local JVM run + GitHub Actions run) |
| Debug APK | ✅ Builds locally and on CI (`kenkern-debug-apk` artifact, 15.4 MB) |
| Release APK | ✅ Builds locally and on CI (`kenkern-release-apk` artifact, 9.9 MB; signing via secrets optional, currently unsigned) |
| GitHub Actions | ✅ Run 42868608173 — **success** on ubuntu-latest (all steps green: tests + assembleDebug + assembleRelease + artifacts) |
| Localization | ✅ en (base), pt-BR, pt-PT, fr — 127 keys each, key-complete |

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
- [x] Triangle renders correctly (real GLES draw; demo triangle in Phase 1 payload, as `MESH_TRIANGLE` Nink from Phase 2)
- [x] 3D cube renders correctly
- [x] Camera projection and movement work (perspective + lookAt; orbit/pan/zoom)
- [x] Rendering loop survives pause/resume (GL thread stop/start, stats reset)
- [x] Context recreation does not permanently break rendering (resources rebuilt in `onSurfaceCreated`, `preserveEGLContextOnPause` set)
- [x] Viewport integrated into the editor (GLSurfaceView behind transparent Compose window; panels float above)
- [x] No fake renderer — all rendering is real OpenGL ES 3.0 (the Phase 1 "demo payload" was a real GL scene, replaced by live scene data in Phase 2)

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
| Matrix4Test | 15 | ✅ pass |
| Vector3Test (in Matrix4Test.kt) | included above | ✅ pass |
| NinkRegistryTest (incl. TransformTest + SceneOpsTest) | 13 | ✅ pass |
| CommandStackTest | 12 | ✅ pass |
| SceneSerializerTest | 10 | ✅ pass |
| ProjectSerializerTest | 6 | ✅ pass |
| **Total** | **56** | **56 passed, 0 failed** |

Notes:
- Tests are JVM (JUnit 4) unit tests; the real `org.json` artifact is used so
  serializers run without Android stubs.
- GL rendering behavior requires a device/emulator and is validated by manual
  smoke testing of the built APK; it is not covered by JVM tests.

## Build status (local sandbox validation)

- `./gradlew assembleDebug` → BUILD SUCCESSFUL (app-debug.apk, ~16.9 MB)
- `./gradlew assembleRelease` → BUILD SUCCESSFUL (app-release-unsigned.apk, ~10.9 MB)
- `./gradlew testDebugUnitTest` → BUILD SUCCESSFUL (56/56)
- Local environment note: Maven Central rate-limited this sandbox (HTTP 429),
  so local builds used a mirror repository injected into `settings.gradle.kts`
  **temporarily**; the committed settings file keeps the standard
  `google()`/`mavenCentral()`/`gradlePluginPortal()` set used by CI.
- Release APK is unsigned locally because no signing secrets exist in this
  environment; CI signing activates automatically when the four
  `KENKERN_*` secrets are configured.

## Known bugs

None known at the time of writing. The first CI run failed on a
third-party SDK setup action (`android-actions/setup-android@v3` — legacy
`tools` package removed upstream); fixed by using the runner's preinstalled
SDK directly. (All other issues found during development were fixed and are
listed in CHANGELOG.md.)

## Known limitations (by design, not bugs)

- Component edits (camera FOV/near/far, light intensity/range) are live
  writes without undo entries; flags and transforms are undoable.
- Non-mesh Nink types (camera, lights, characters) render with placeholder
  gizmo shapes (pyramid/cube); gizmo picking and dedicated icons are future
  work.
- Drag-handle reparenting drops onto a row as a child (append); reordering
  uses the context menu (Move Up/Down). Edge-positioned insert is future work.
- Editor viewport camera is independent of the scene's active camera; play
  mode that uses the active camera is future work.
- `Create Prefab` / `Instantiate Prefab` are implemented (subtree JSON),
  but there is no prefab library browser beyond the Add menu section.

## Next recommended task

1. Manual device smoke test of the debug APK (download the
   `kenkern-debug-apk` artifact from the successful run): home → create
   project → editor → add/move/delete Ninks → rotate device (context
   recreation) → background/foreground (pause/resume) → save → reopen.
2. Optionally configure the four `KENKERN_*` signing secrets to produce
   signed release APKs.
3. Start Block 2 planning (see ROADMAP.md): viewport gizmos + picking,
   camera/light gizmo drawing, component undo support, edge-reparenting UX.
