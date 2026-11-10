# PROJECT.md — Product Definition

This document defines what KENKERN is, the principles it follows, the platform it targets and
the explicit scope boundaries of the current implementation. Everything marked **IMPLEMENTED**
exists in code today; everything marked **PLANNED** does not exist and must not be assumed by
contributors.

## 1. Identity

**KENKERN** is an **Android-first 3D game engine and editor**, delivered as a single Android
application (`com.kenkern.engine`), engine version **0.1.0** (`EngineConfig.VERSION`).

- The fundamental scene entity is a **Nink**. This name is part of the product vocabulary and
  is never replaced by "GameObject", "Node" or similar terms in UI, documentation or code.
- Visual identity: dark background `#0E0E10`, white `#FFFFFF`, blue accent `#007BFF`.
  The identity is defined in `values/colors.xml`, applied by `KenkernTheme` (Compose) and the
  GL clear color (`EngineConfig`).
- English is the base locale; Portuguese (Brazil), Portuguese (Portugal) and French are shipped
  translations.

## 2. Goals

1. **A complete, usable editor on a phone or tablet.** The editor is designed for touch first:
   floating panels, per-row context menus, 1-finger orbit / 2-finger pan / pinch-zoom viewport.
2. **A trustworthy scene model.** Scenes are hierarchical Nink graphs with unique stable ids,
   acyclic structure, full undo/redo, and robust file loading (a damaged file either loads into
   a valid hierarchy or fails loudly — it never produces a silently corrupted scene).
3. **A foundation that later blocks can extend without rewrites.** Rendering, scene, editor and
   persistence are separated behind small, explicit contracts so future systems (physics,
   animation, scripting, Vulkan, KENKERN Intelligence) can attach without mutating the core.

## 3. Principles

| Principle | Meaning in KENKERN |
|---|---|
| **Modularity** | Engine subsystems are Kotlin packages with narrow, documented entry points (`EngineConfig`, `SceneEditor`, `ProjectRepository`, `RenderFramePublisher`). The editor never reaches into renderer internals; the renderer never reads live scene objects. |
| **Determinism** | Scene mutations flow through reversible commands with a single Euler convention (`R = Rz(z)·Ry(y)·Rx(x)`) and stable serialization. Loading repairs damage in deterministic ways (unknown types → `EMPTY`, cycles broken, duplicate ids skipped). The same input always yields the same result. |
| **Maintainability** | Centralized configuration (`EngineConfig`), a single logging facade with an in-memory ring buffer, no per-frame allocations in the render loop, and JVM-testable pure logic (math, registry, commands, serializers — 56 unit tests). |
| **Clear contracts** | Ownership and threading rules are explicit: registry invariants documented in `NinkRegistry`, immutable `RenderFrameData` snapshots for the GL thread, IO confined to `Dispatchers.IO`, scene writes confined to the main thread. |

## 4. Target platform and constraints

| Constraint | How KENKERN handles it |
|---|---|
| **Android lifecycle** | The activity is not recreated on configuration changes (declared in the manifest) so the GL surface and editor state survive rotation/resize. EGL context preservation is requested (`preserveEGLContextOnPause`), and the renderer still fully rebuilds GPU resources on context loss. Autosave is debounced and a save-on-exit flush protects unsaved work. |
| **GL context recreation** | `GLSurfaceView` may destroy and recreate the EGL context at any time. `GLRenderer` treats `onSurfaceCreated` as "rebuild everything": shader programs and meshes are re-created, never cached across contexts. |
| **Memory** | The device is a phone: the render loop performs zero per-frame allocations (thread-confined scratch matrices, cached meshes/programs, one shared vertex buffer per mesh family). Scene data is plain Kotlin objects sized for mobile scenes. |
| **Touch input** | The viewport is gesture-driven via `ViewportInputHandler` (1-finger orbit, 2-finger pan, pinch zoom). Panels are floating and draggable; row actions live in three-dot menus sized for fingers. |
| **GPU support** | OpenGL ES 3.0 is a hard requirement (`uses-feature android:glEsVersion="0x00030000"`, required). `GLCapabilities` probes the device and the UI shows a clear unsupported-device screen when GLES 3.0 is unavailable. |

**Platform facts (IMPLEMENTED):** Kotlin + Jetpack Compose (Material3) UI, OpenGL ES 3.0
rendering (`GLSurfaceView` + GLSL ES 3.00 shaders), minSdk 26 / target & compile SDK 35.
**Vulkan is explicitly a FUTURE backend and is NOT implemented. There is no C++ code and no
backend/cloud component.**

## 5. Scope of Block 1 (IMPLEMENTED)

Block 1 consists of three phases; all are implemented in the current code.

### Phase 0 — Foundation
Centralized engine configuration (`EngineConfig`), logging facade with ring buffer
(`EngineLogger`), runtime initialization and crash reports written to app-private storage
(`EngineRuntime`), project repository with atomic writes (`ProjectRepository`), project/scene/
prefab JSON serialization, home screen for project list/create/delete.

### Phase 1 — Graphics
OpenGL ES 3.0 renderer on a dedicated GL thread, EGL ES3 context (RGBA8 + 24-bit depth),
capability probing and diagnostics (`GLCapabilities`), mesh factory (triangle, plane, cube,
sphere, cylinder, pyramid, grid, wire cube — with baked unlit shading), builtin shaders (unlit
solid + vertex-color), editor orbit camera, touch gestures, FPS/frame-time overlay.

### Phase 2 — Nink system
The full Nink scene model (14 Nink types), registry with invariants, TRS transforms with world
preservation on reparent, command system with undo/redo (128-entry history, burst coalescing
for transform edits), duplicate/prefab-instantiate support, hierarchy panel (search,
expand/collapse, drag-handle reparenting), inspector (flags + transform + camera/light
components), Add-Nink menu driven by an extensible catalog, autosave (debounced 1.5 s) and
save-on-exit.

**Explicitly OUT of scope for Block 1** (not started, no code exists):

- Physics
- Advanced asset pipeline (imported models/textures; only procedural primitives exist)
- Animation
- Audio
- Scripting
- Vulkan backend
- KENKERN Intelligence
- Prefab UI beyond create/instantiate (no prefab editing UI)
- Component undo/redo — component edits (camera/light values) are live writes; flags and
  transforms are undoable, component edits are not
- Light/camera gizmo picking in the viewport (lights/cameras render as pyramid meshes, but you
  cannot pick them by tapping the viewport)

## 6. Localization policy

- All user-facing strings live in Android string resources. **UI strings are never hardcoded.**
- Locales: `values/` (English, base), `values-pt-rBR/`, `values-pt-rPT/`, `values-fr/`.
  `SupportedLocales` is the authoritative list (`en`, `pt-BR`, `pt-PT`, `fr`).
- **Identifiers are never translated**: Nink type names as serialized ids, enum names, file
  format keys and API names stay English. Only display strings (including default Nink names,
  which are resolved via `NinkType.nameRes`) are localized.
- Any new UI string must be added to **all** locale files in the same change.

## 7. Build & release policy

- **Official builds run on GitHub Actions** (`.github/workflows/android-build.yml`):
  unit tests (`testDebugUnitTest`) + `assembleDebug` + `assembleRelease`, with APK artifacts
  uploaded. CodeAssist is not used.
- Toolchain: Gradle 8.10.2 wrapper, AGP 8.7.3, Kotlin 2.0.21 (+ `kotlin.plugin.compose`),
  JDK 17, Android SDK 35.
- **Signing is secret-driven**: the CI workflow decodes `KENKERN_KEYSTORE_BASE64` into a
  runner-temp file and passes `KENKERN_KEYSTORE_PASSWORD`, `KENKERN_KEY_ALIAS`,
  `KENKERN_KEY_PASSWORD` as environment variables. Without these secrets the release APK is
  unsigned. No keystore or credential is ever committed to the repository.
- Local builds require JDK 17 + Android SDK; they are for development, but CI is the source of
  truth for "does it build".

## 8. Documentation map

| Document | Audience |
|---|---|
| [README.md](README.md) | First contact: what this repo is, how to build and test. |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Contributors: how the engine and editor are put together. |
| [ROADMAP.md](ROADMAP.md) | Everyone: what is implemented vs planned. |
| [CONTRIBUTING_AI.md](CONTRIBUTING_AI.md) | AI contributors: mandatory working rules. |
