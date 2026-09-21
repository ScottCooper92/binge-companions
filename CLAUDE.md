# CLAUDE.md

Conventions for this repository. The agent workflows in `.github/workflows/` read
this file from `main` and treat it as the source of truth; so should you.

## What this repository is

The companion-app integration platform for [Binge](https://github.com/ScottCooper92).
Binge ships as a plain TMDB client with zero bundled providers. A companion app is
a separate APK that exports a bound Android Service implementing a contract from
this repository. Only data crosses the IPC boundary — there is no dynamic code
loading.

Read `README.md` for the shape of it and `docs/Architecture.md` for the decisions.
**`docs/Architecture.md` is authoritative.** Where this file and that one disagree,
that one wins and this one is the bug.

Status is pre-alpha. Nothing is published, and `docs/Status.md` records where each
contract stands.

## The one rule everything else serves

**Inside a published proto package, every change is additive.**

New fields, new enum values, new rpcs. Never a renumbering, never a removal, never
a change of meaning. `buf breaking` enforces the mechanical part in CI; the part it
cannot enforce is designing a message you will not later want to renumber.

A genuinely breaking change is a new package — `binge.companion.request.v2` — with
its own service, served side by side with `v1` from the same exported Service. That
is an escape hatch and a deliberate human decision, not a refactor. See
`docs/Architecture.md` > Versioning.

Corollary: **feature detection never uses version numbers.** A peer discovers what
the other end can do from the handshake's capability set, and ignores enum values
it does not know. Version numbers answer "can we parse each other?" and nothing else.

## Proto conventions

- Package is `binge.companion.<contract>.v<major>`, and the directory mirrors it.
- `buf lint` runs the `STANDARD` category; `buf breaking` runs `FILE` against `main`.
  Both gate CI. Neither is negotiable in a PR — see Gates below.
- Field numbers are allocated in order and never reused. Removing a field means a
  `reserved` entry for its number *and* its name.
- Enum values are appended. The zero value is the unspecified/unknown case.
- **Errors are gRPC status codes, never fields on a response message.** A response
  type carries the result or is empty. Each contract documents its code mapping in
  its own `.proto`.
- **Page every list, and send artwork as a URL rather than bytes.** The Binder
  transaction limit is about 1 MB and it is a hard ceiling, not a guideline.
- **Media is identified as media type + TMDB id (+ season/episode).** Translation
  into a provider's own id space, IMDb or TVDB is the companion app's job, never
  the contract's.
- A behaviour variation over the same data model is a capability. A new data model
  with its own lifecycle is a new contract.

## Kotlin and Gradle

- Kotlin/JVM, `jvmTarget` 17, built and tested on JDK 21.
- Runtime is `protobuf-javalite` with `grpc-kotlin` stubs — suspend functions and
  `Flow`s, not callbacks. Generated code is not checked in.
- Tests are JUnit 5. Scope Gradle to a module (`:contracts:test`) rather than
  running the whole tree when you are checking one thing.

## Gates

CI runs `./gradlew build` and, separately, `buf lint` plus `buf breaking --against main`.
`build` covers the Kotlin compile, the JUnit 5 tests, `ktlintCheck`, `detekt` and
`koverVerify` — all three wire themselves into `check`, and `build` depends on it. There
is no screenshot suite in this repository, so do not look for one and do not report a
finding as though one had caught it.

Formatting is `ktlint_official` with the settings in `.editorconfig`, which are Binge's
minus its Compose lines. `:contracts` excludes `build/generated` — protoc's output is
not checked in, so there is nothing there to format.

detekt runs on `:sdk` only, off the root `detekt.yml`, which records deviations from
detekt's defaults rather than a full ruleset. It runs **with type resolution**: the
compile classpath is wired onto the task in `sdk/build.gradle.kts`, and without it
`UnsafeCallOnNullableType` — the `!!` ban — loads and silently never fires. There is no
`detekt-baseline.xml`, because the first run found nothing; keep it that way rather than
creating one.

Coverage gates `:sdk` at **85% of lines**, measured after excluding the handful of classes
that exist only to hold an Android type — `HostPolicy`, `HandOffPolicy`, `IntegrationService`
and the `PackageManager` extension. That is not a fudge: each of those already has its
decision split out into a JVM-testable class the tests do cover, and the KDoc at each one
says so. `AdvancedRequestKt` is deliberately not excluded, because it holds both halves.
`:contracts` has no gate at all — generated stubs, nothing to write a test for.

The floor was set from a measurement (90.4% at the time) rather than inherited, so it
ratchets. **A third module has to apply kover or say in its build file why not.** Binge
enforces that with a root `checkCoverageGate` task; with two modules that is overkill, so
here it is a convention — which means the next module is where it can silently go wrong.

**Never silence a gate instead of fixing it.** Do not add an exclusion to the
`lint` or `breaking` configuration in `buf.yaml`, do not add a
`# buf:lint:ignore` comment, and do not add a ktlint baseline or a
`ktlint-disable` comment — `./gradlew ktlintFormat` is the fix. A `buf breaking` failure is the gate working: the
correct response is to make the change additive, or to stop and ask whether this
needs a `v2`.

## Follow-ups

Anything worth doing that does not belong in the diff in front of you becomes a
GitHub issue, not a TODO comment and not a line in a PR description.

The test is **scope, not severity**. A low-severity problem that reaches beyond
the current diff is still an issue; a serious problem inside the diff is a change
to make now. "Worth tracking", "worth confirming" and "shouldn't fall off the
backlog" all describe issues that should have been filed.

Dedupe against the open backlog before filing — and label it, using the labels the
repository already has rather than inventing new ones.

## Commits and pull requests

- Conventional-commit subjects (`feat:`, `fix:`, `docs:`, `chore:`), imperative mood.
- One reviewable idea per PR. A PR that changes a contract and refactors the build
  is two PRs.
- A PR that changes anything under `contracts/src/main/proto` says in its body what
  the wire-compatibility story is, even when it is "purely additive, `buf breaking`
  is green".
- Documentation in this repository is written in plain, direct English —
  short sentences, one idea each. Match the surrounding prose rather than
  introducing a different register.

## Agent workflows

`.github/workflows/` holds a review bot and three author bots, ported from Binge and
adapted for a public repository on GitHub-hosted runners. They act only on PRs
carrying the `agent` label.

**That label is maintainers-only.** Applying it grants an agent code execution with
this repository's secrets in scope. Do not apply it to a PR you have not read, and
never to one from a fork — the workflows already refuse fork PRs, and that guard is
the load-bearing control here rather than a formality.
