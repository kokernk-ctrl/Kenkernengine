# KENKERN

KENKERN is an **Android-first 3D game engine and editor**. It is a single Android application
(`com.kenkern.engine`) that ships both the engine core and a touch-based scene editor.
The fundamental scene entity is a **Nink** — 3D objects, characters, cameras, lights, groups
and empty entities are all Ninks.

> **Status — Block 1 implemented.** This repository was previously empty; the current code is
> the initial implementation covering Phases 0–2 (foundation, graphics, Nink system).
> Everything else (physics, animation, audio, scripting, Vulkan, …) is **planned only** — see
> [ROADMAP.md](ROADMAP.md).

## Visual identity

| Token | Value |
|---|---|
| Background | `#0E0E10` |
| Foreground | `#FFFFFF` |
| Accent | `#007BFF` |

The identity is defined once in `app/src/main/res/values/colors.xml` and mirrored in the GL
clear color (`EngineConfig`) and the Compose theme (`KenkernTheme`).

## Tech stack (implemented)

| Layer | Technology |
|---|---|
| Language | Kotlin 2.0.21 (JVM target 17) |
| UI | Jetpack Compose + Material3 (Compose BOM 2024.12.01) |
| Rendering | OpenGL ES 3.0 via `GLSurfaceView`, GLSL ES 3.00 shaders |
| Build | Gradle 8.10.2 wrapper, Android Gradle Plugin 8.7.3 |
| SDK | compileSdk 35 / targetSdk 35 / minSdk 26 |
| Testing | JUnit 4 (JVM unit tests, 56 tests passing); `org.json:json:20240303` test artifact |
| CI | GitHub Actions (`.github/workflows/android-build.yml`) |

There is **no C++ code, no backend/cloud component, and no Vulkan implementation**.
Vulkan is a future backend candidate only (see [ROADMAP.md](ROADMAP.md)).

## Repository layout

| Path | Contents |
|---|---|
| `app/` | The single Gradle module (Android application). |
| `app/src/main/java/com/kenkern/engine/core/` | Engine config, logging facade, runtime init + crash reports. |
| `app/src/main/java/com/kenkern/engine/math/` | `Vector3`, `Matrix4` (column-major, TRS, perspective, lookAt, invert). |
| `app/src/main/java/com/kenkern/engine/scene/` | Nink model, registry, transforms, scene, editor facade, ops, factory. |
| `app/src/main/java/com/kenkern/engine/scene/commands/` | Command system: undo/redo stack (128 entries) + concrete Nink commands. |
| `app/src/main/java/com/kenkern/engine/rendering/` | GLES 3.0 renderer, surface view, capability probe, frame stats, frame snapshots. |
| `app/src/main/java/com/kenkern/engine/rendering/mesh/` | `Mesh` (VBO/IBO/VAO) and `MeshFactory` (primitives with baked unlit shading). |
| `app/src/main/java/com/kenkern/engine/rendering/shader/` | Shader compile/link with error reporting; unlit solid + vertex-color builtins. |
| `app/src/main/java/com/kenkern/engine/rendering/camera/` | Editor orbit camera controller. |
| `app/src/main/java/com/kenkern/engine/input/` | Viewport gestures: 1-finger orbit, 2-finger pan, pinch zoom. |
| `app/src/main/java/com/kenkern/engine/project/` | Project metadata + on-disk repository (atomic writes, validation). |
| `app/src/main/java/com/kenkern/engine/serialization/` | Project / scene / prefab JSON (de)serialization with defensive loading. |
| `app/src/main/java/com/kenkern/engine/editor/` | Compose editor: home screen, editor screen, view models, theme. |
| `app/src/main/java/com/kenkern/engine/editor/panels/` | Hierarchy, Inspector, Add-Nink menu, context menus, dialogs, Nink catalog. |
| `app/src/main/java/com/kenkern/engine/localization/` | `SupportedLocales` registry. |
| `app/src/main/res/values*/` | String resources (en base + `values-pt-rBR`, `values-pt-rPT`, `values-fr`), colors, theme. |
| `app/src/test/` | JVM unit tests (JUnit 4) — 56 tests. |
| `.github/workflows/android-build.yml` | Official build workflow. |

## Building

### Official builds (GitHub Actions)

The official build is the GitHub Actions workflow `android-build.yml`, which runs on pushes and
pull requests to `main`/`master` (and on manual dispatch):

1. `./gradlew --no-daemon testDebugUnitTest` (JVM unit tests)
2. `./gradlew --no-daemon assembleDebug`
3. `./gradlew --no-daemon assembleRelease`

Debug and release APK artifacts are uploaded on every run. Release signing is optional and
driven entirely by GitHub secrets — when the secrets are absent, the release build produces an
unsigned APK:

| Secret | Purpose |
|---|---|
| `KENKERN_KEYSTORE_BASE64` | Base64-encoded release keystore (decoded to a temp file by CI) |
| `KENKERN_KEYSTORE_PASSWORD` | Keystore password |
| `KENKERN_KEY_ALIAS` | Signing key alias |
| `KENKERN_KEY_PASSWORD` | Signing key password |

No credentials are ever stored in source files. CodeAssist is not used and is not part of the
workflow.

### Local builds

Requirements: **JDK 17** and an **Android SDK** (API 35 platform, build-tools 35).

```bash
./gradlew assembleDebug        # debug APK
./gradlew assembleRelease      # release APK (signed only if env vars are set)
```

## Testing

```bash
./gradlew testDebugUnitTest
```

56 JVM unit tests (JUnit 4) cover math, scene/registry invariants, transforms, scene ops,
the command stack, and both serializers. `org.json:json` is included as a real test artifact so
the serializers are exercised on the JVM instead of hitting the `android.jar` stubs.

## Localization

UI strings live exclusively in Android string resources — they are never hardcoded. Shipped
locales:

| Locale | Resource directory |
|---|---|
| English (base) | `values/` |
| Portuguese (Brazil) | `values-pt-rBR/` |
| Portuguese (Portugal) | `values-pt-rPT/` |
| French | `values-fr/` |

Identifiers, APIs and internal engine names are never translated. See
[PROJECT.md](PROJECT.md) for the full localization policy.

## Documentation index

| Document | Contents |
|---|---|
| [PROJECT.md](PROJECT.md) | Product definition, goals, principles, platform constraints, scope. |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Package map, threading model, rendering pipeline, scene system, persistence, decisions. |
| [ROADMAP.md](ROADMAP.md) | Implemented blocks and planned future blocks. |
| [CONTRIBUTING_AI.md](CONTRIBUTING_AI.md) | Rules for AI contributors working on this repository. |
