# CONTRIBUTING_AI.md — Rules for AI Contributors

This document defines the mandatory working rules for AI agents (and humans acting as one)
modifying the KENKERN engine repository. These rules exist because AI contributors tend to
fail in predictable ways: implementing unplanned systems, presenting placeholders as finished
work, hardcoding strings, or skipping validation. Do not do these things.

---

## 1. Mandatory repo inspection before changes

**Never write code or documentation from memory or assumptions.**

- Before proposing or making any change, read the relevant source files, `README.md`,
  `PROJECT.md`, `ARCHITECTURE.md` and `ROADMAP.md` in the actual working tree.
- Verify facts against code: signatures, constants (`EngineConfig`), string resources,
  serialization keys and file paths. If a fact cannot be verified in the repo, either verify it
  with a build/test run or do not state it.
- Check `ROADMAP.md` to know what exists (IMPLEMENTED) versus what does not (PLANNED ONLY).
  Check `STATE.md` / `CHANGELOG.md` for the current validated status of the repository.

## 2. Phase / block scope control

KENKERN is developed in phases grouped into blocks (see `ROADMAP.md`).

- **Never implement future systems prematurely.** If a system is marked PLANNED ONLY (physics,
  animation, audio, scripting, Vulkan, KENKERN Intelligence, gizmo picking, prefab editing UI,
  …), do not create code for it, even "scaffolding" that looks complete. Implement what the
  current phase asks for and stop.
- Extending a **documented extension point** (new `NinkType`, new command class, new catalog
  entry, new builtin shader/mesh) within the current phase's scope is correct; inventing a new
  subsystem is not.
- **Never mark untested work complete.** "Implemented" means: code written, compiles, tests
  pass, and the change is validated. Untested work must be reported as in-progress.
- **No fake implementations or placeholders presented as real.** If a feature cannot be
  finished, say so explicitly: what is done, what is stubbed, what is missing. Never write
  stubs that pretend to be functioning systems, and never describe TODO-laden code as complete.
- Do not modify code that exists only to satisfy a planned feature's assumptions.

## 3. Centralized localization rule

All user-facing strings are Android string resources. This is a hard rule of the project.

- **Never hardcode user-facing strings** in Kotlin/Compose code, XML layouts, or generated
  content. Every string a user can see goes into `app/src/main/res/values/strings.xml`.
- When adding or changing a string key, add it to **ALL** locale files:
  - `values/strings.xml` (English, base)
  - `values-pt-rBR/strings.xml`
  - `values-pt-rPT/strings.xml`
  - `values-fr/strings.xml`
- **Identifiers are never translated**: Nink type enum names (serialized ids), file format
  keys, API names, file names. Only display strings (including default Nink names via
  `NinkType.nameRes`) are localized.
- The scene entity is called a **Nink**. Never call it "GameObject" or "Node" in UI strings,
  docs or code comments.
- When localizing, keep placeholders (`%1$s`, `%1$d`, …) intact and correctly ordered per
  language grammar.

## 4. Serialization format changes

Scene and prefab JSON are version-checked (`format: "kenkern-scene" | "kenkern-prefab"`,
`version`, currently 1).

- **Any change to the serialized schema requires a version bump** in `SceneSerializer`
  (`FORMAT_VERSION`) plus a written migration path (or an explicit, documented rejection of
  old files) and updated serializer tests.
- Renaming or removing a `NinkType` enum name is a breaking change: serialized files store enum
  names. Additive changes (appending types) are safe because unknown names deserialize to
  `EMPTY`.
- Preserve the defensive loading rules (unknown types → `EMPTY`, missing parents → root,
  duplicate ids skipped, cycles broken, zero scales repaired) and their tests when touching
  `SceneSerializer`.
- `project.json` (`ProjectSerializer`) follows the same policy: format key + version checked
  on load.

## 5. Testing requirements

- Pure logic (math, registry, commands, transforms, scene ops, serializers) gets JVM unit tests
  in `app/src/test` (JUnit 4). Current baseline: **56 tests, all passing locally**. New logic
  needs new tests; fixes need a regression test.
- Tests must not depend on Android framework runtime behavior; use the established pattern
  (e.g. the real `org.json` test artifact for serializer tests).
- **Before reporting any code change as done, run:**

  ```bash
  ./gradlew testDebugUnitTest
  ```

  and report the actual result (all tests passing, or the failures verbatim). Do not claim a
  test run you did not perform. Note: `./gradlew` requires JDK 17 and the Android SDK.

## 6. Build validation via GitHub Actions

- Local Gradle runs are for development; **the official validation is the GitHub Actions
  workflow** `.github/workflows/android-build.yml`, which runs unit tests + `assembleDebug` +
  `assembleRelease` on every push/PR to `main`/`master` and uploads APK artifacts.
- CodeAssist is not used and must not be introduced into the workflow.
- Do not edit the workflow to skip tests, bypass artifact uploads, or weaken the build.
- After pushing changes, check the workflow result before declaring a task complete; a red CI
  run means the work is not done.

## 7. Secrets & token security

- **Never commit tokens, passwords, keystores or signing material** to the repository, logs,
  screenshots, or documentation. Release signing uses GitHub secrets only:
  `KENKERN_KEYSTORE_BASE64`, `KENKERN_KEYSTORE_PASSWORD`, `KENKERN_KEY_ALIAS`,
  `KENKERN_KEY_PASSWORD` (decoded to a runner-temp file by CI — nothing touches the repo).
- Never echo secrets into build outputs or test reports.
- **If a token, key or keystore was ever pasted into a chat, an issue, a log, or any shared
  surface: treat it as compromised and rotate it immediately**, then report the rotation.
- `local.properties`, `.gradle/`, and build outputs are local-only; keep them out of commits
  (`.gitignore` already covers them — do not weaken it).

## 8. Documentation duties

Documentation is part of the deliverable, not an afterthought.

- After any code change, update `STATE.md` and `CHANGELOG.md` with what was done and the
  validation evidence (commands run, results). These are the project's working record.
- Keep `README.md`, `PROJECT.md`, `ARCHITECTURE.md` and `ROADMAP.md` accurate when a change
  affects them (new package, new decision, scope shift, new tests). Architecture decisions that
  shape future work should be added to the decisions list in `ARCHITECTURE.md`.
- Clearly separate IMPLEMENTED from PLANNED in all documentation. Never describe planned
  functionality in the present tense.

## 9. Git hygiene

- Keep commits **phase-scoped**: one commit (or a tight series) per coherent phase task, with a
  message that says what changed and why. No mega-commits mixing unrelated work.
- **No force pushes** to shared branches (`main`/`master`); do not rewrite public history.
- Do not commit unrelated generated files; build outputs and local config stay ignored.
- When a task cannot be finished, commit the consistent partial state (it must build and pass
  tests) and report exactly what remains — do not leave the tree broken, and do not finish it
  "quietly" with unvalidated shortcuts.

---

## Summary checklist (run before reporting any task)

1. Did I inspect the repo and verify every claim against actual code?
2. Is my change within the current phase/block scope (no premature future systems)?
3. Are all new user-facing strings in all four `strings.xml` files?
4. Did schema changes bump the format version with a migration path and tests?
5. Did I add/update JVM tests, and did `./gradlew testDebugUnitTest` actually pass?
6. Is CI green on the branch?
7. Are `STATE.md` and `CHANGELOG.md` updated with validation evidence?
8. Are IMPLEMENTED vs PLANNED labels accurate everywhere I wrote?
9. Did I keep secrets out of the repo — and rotate anything that leaked?
10. Are my commits phase-scoped, with no force pushes?
