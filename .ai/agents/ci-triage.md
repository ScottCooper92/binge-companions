# CI triage

Maps a red CI run to its correct fix. `author-ci-fix.yml` reads this file from
`main` and works from the table below; it gets exactly one repair attempt per
commit, so a guess is expensive and stopping is cheap.

CI is two independent jobs:

| Job | Runs |
| --- | --- |
| `build` | `./gradlew build` — compile, generate protos, run tests, `ktlintCheck`, `detekt` on `:sdk`, `koverVerify` (85% of `:sdk` lines), Android lint on `:sdk` (`lintDebug`), the R8 shrink of `:minify-check` and its `MinifiedKeepRulesTest` |
| `proto` | `buf lint`, then `buf breaking --against main` on PRs |

There is no screenshot suite in this repository.
If the log shows a failure that is not in the table below, that is a **stop**, not
an invitation to improvise.

## The table

| Failure | Why | Fix |
| --- | --- | --- |
| `buf breaking` reports a changed field number, name, type or label | The branch changed the wire contract | **Usually stop.** Make the change additive instead: a new field with the next unused number, leaving the original alone. If the intent really was to change the existing field, that needs a `v2` package and a human. Never touch the `breaking` config |
| `buf breaking` reports a deleted field, enum value or rpc | Same | **Stop.** A removal is a `reserved` entry plus a human decision, not a repair |
| `buf breaking` on a file rename or move | `FILE` category treats the file as the compatibility unit | **Stop.** Moving a published `.proto` is a breaking change by definition |
| `buf lint` — naming (`FIELD_LOWER_SNAKE_CASE`, `ENUM_VALUE_UPPER_SNAKE_CASE`, `SERVICE_SUFFIX`, …) | `STANDARD` category convention | Rename to match, **only if the symbol is new in this diff**. Renaming an existing published field is a breaking change wearing a lint failure's clothes — check `buf breaking` before and after |
| `buf lint` — `ENUM_ZERO_VALUE_SUFFIX` / first value not `_UNSPECIFIED` | Every enum needs an unspecified zero value | Add it as value `0` and renumber the rest **only if the enum is new in this diff**. Otherwise stop |
| `buf lint` — `PACKAGE_DIRECTORY_MATCH` | Directory does not mirror the proto package | Move the file to match the package. Safe when the file is new; a **stop** if it is published |
| `buf lint` — `RPC_REQUEST_RESPONSE_UNIQUE` / `RPC_REQUEST_STANDARD_NAME` | Each rpc needs its own `FooRequest`/`FooResponse` | Add the dedicated message types. Safe and additive |
| `ktlintCheck` fails | Formatting | Run `./gradlew ktlintFormat` and commit the result. Never add a ktlint baseline or a `ktlint-disable` comment |
| `detekt` fails on `:sdk` | Static analysis, with type resolution | Fix the finding in the code. Never add a `detekt-baseline.xml` (there is none, on purpose) or a `@Suppress`. If `UnsafeCallOnNullableType` is the rule, remove the `!!` |
| `:sdk:lintDebug` fails | Android lint, AGP's defaults with no baseline; `NewApi` is an error at `minSdk` 26 | Read `sdk/build/reports/lint-results-debug.txt` for every finding, not just the first. For `NewApi`, keep the call in a function marked `@TargetApi(n)` and call it only behind a visible `Build.VERSION.SDK_INT >= n` check: lint does not follow a guard that lives in another function which takes the call as a lambda. Never add a `lint-baseline.xml`, `abortOnError = false` or a broad `@SuppressLint` |
| `koverVerify` fails | `:sdk` line coverage fell below 85% | Add a test for the logic the diff added. Never lower the floor or widen the kover exclusions to make it pass: an exclusion is only for a class that holds an Android type and has its decision split into a tested class |
| Kotlin compile error in `contracts` | Ordinary | Fix it. Note that generated stubs are not checked in — a symbol that "does not exist" after a proto edit usually means the proto and the Kotlin disagree, so fix the side that is wrong rather than deleting the reference |
| `MinifiedKeepRulesTest` fails | A consumer keep rule no longer holds what R8 needs in a minified host (a message field, or grpc-binder's name resolver constructor) | Add or restore the rule in `sdk/consumer-rules.pro` or `contracts/src/main/resources/META-INF/proguard/binge-companion-contracts.pro`. Never add a keep rule to `:minify-check` or lower `MIN_MESSAGES`: that hides exactly what the check exists to catch |
| `MinifiedRoundTripTest` fails (`device-round-trip.yml`) | A minified build broke across a real Binder: `ServiceConfigurationError` from `forAddress`, `Field … not found` on a parse, or a call that timed out | Read the logcat in the `device-round-trip-results` artifact. A missing rule goes in `sdk/consumer-rules.pro` or the contracts' consumer rules, never in `:minify-check`. If the emulator never booted (no `/dev/kvm`, a system image download), it is the runner, not the code: re-run once |
| `ProtoRoundTripTest` fails | A message no longer serialises and deserialises to itself | Read the assertion. This is usually a real contract bug — a field number collision, or a `oneof` that lost a case. Fix the proto, not the test |
| Any other test failure | Ordinary | Fix the code the test is describing. Changing an assertion to match new behaviour is only correct when the PR deliberately changed that behaviour and says so |
| Gradle "could not resolve" / dependency failure | Usually transient or a `libs.versions.toml` edit | If the diff touched `gradle/libs.versions.toml`, fix that. Otherwise it is infrastructure — **stop** and say so |
| `protoc` / codegen plugin failure | Version mismatch between `protoc`, `grpc` and `grpc-kotlin` | **Stop** unless the diff itself changed those versions. Bumping one to make an error go away breaks the others |
| Empty or unreadable log | Nothing to triage from | **Stop.** The workflow already handles this and comments on the PR |

## Verifying

Run only what failed, scoped:

```sh
buf lint
buf breaking --against ".git#branch=main"
./gradlew :contracts:test
```

Do not run the full gate. CI does that on push.

## Never

- Add an exclusion to the `lint` or `breaking` configuration in `buf.yaml`.
- Add a `# buf:lint:ignore` comment.
- Change, renumber or remove a field, enum value or rpc in a published package.
- Delete a `reserved` range.

A `buf breaking` failure is the gate working. Making it pass without making the
change additive is not a repair, it is the specific harm this repository exists to
prevent — see `CLAUDE.md` and `docs/Architecture.md` > Versioning.

## When in doubt

Contract shape is a design decision, not a build failure. A red build is not
permission to redesign a message. Leaving the working tree clean and saying why is
a valid and preferred outcome; a wrong guess costs more than stopping.
