---
name: audit
description: Whole-repo sweep of binge-companions (the contracts, the SDK and its detekt rules) for wire-compatibility hazards, proto convention violations, SDK security-policy weaknesses, doc-vs-code drift and dead code — the invariants CI does NOT gate, with the repo's exemption rules baked in so findings arrive pre-filtered, verified, and filed as issues. Use when asked to audit the contracts/SDK, hunt for drift or hazards, or health-check a module. For a diff, use /code-review or /security-review instead — this skill is not diff-scoped.
---

# Contracts and SDK audit

CI runs `./gradlew build` (Kotlin compile, JUnit 5 tests, `ktlintCheck`, `detekt` on `:sdk` with type
resolution, `koverVerify` at 85% on `:sdk`, Android lint on `:sdk`) and, separately, `buf lint` (STANDARD) plus
`buf breaking --against main` (FILE). There is **no** screenshot suite here — do not look for one
and do not report a finding as though one had caught it. Do not re-report what that set decides.
The audit's value is what it can't: whether a *breaking* change hides where `buf` can't see it,
whether the docs still describe the contracts, and whether the SDK's security policy does what
`docs/Architecture.md` says.

**`docs/Architecture.md` is authoritative.** Where `CLAUDE.md` and it disagree, the *other file* is the
bug — say which.

Modules: `contracts/` (`.proto` + generated stubs, not checked in), `sdk/` (`com.binge.companion.sdk`),
`detekt-rules/` (custom rules loaded by `:sdk`). Skip `build/`.

**Three disciplines make this useful rather than noisy:**

1. **Exemptions first.** Check each hit against the exemption. Recurring ones: `src/test`, generated
   code under `build/generated`, the four excluded Android-holder classes (`HostPolicy`,
   `HandOffPolicy`, `IntegrationService`, the `PackageManager` extension — each already splits its
   decision into a JVM-tested class), and `reserved` entries in protos (they are the right way to
   remove a field).
2. **Verify before reporting.** Re-read every candidate in context; label it **confirmed**
   (`file:line` plus the precise violation or failure scenario) or **plausible** (needs a design
   decision — for a proto, that decision belongs to the human, see `CLAUDE.md`). Never report a raw
   grep hit.
3. **Sanity-check the hunt.** Run each grep against a known positive before trusting a zero. Match by
   construction site, not bare name.

## Dimension 1 — proto conventions and additive-only (`CLAUDE.md` > Proto conventions)

Read every `.proto` (`contracts/src/main/proto/binge/companion/v1/common.proto`,
`.../request/v1/request.proto`, and any added since) against the rules `buf` cannot fully enforce:

- **Additive only within a published package**: compare against `git log -p` for the proto files — any
  renumbering, removal without a `reserved` number **and** name, type change, or change of meaning
  (a field whose documented semantics shifted). `buf breaking` catches the mechanical part; you catch
  semantic shifts and comment-only contract changes.
- **Field numbers** allocated in order, never reused; enum values appended; **zero value is the
  unspecified/unknown case** (`*_UNSPECIFIED = 0`); no enum whose zero value has meaning.
- **Errors are gRPC status codes, never fields on a response message.** Each rpc documents its code
  mapping in its own `.proto`; flag an rpc with no documented mapping, a response carrying an error
  string/code field, or a documented code that is ambiguous between two failures.
- **List rpcs page** (cursor/limit), and **artwork is a URL, never bytes**: any `bytes` field that could
  carry image data, any unbounded `repeated` in a response, any request that lets the caller choose an
  unbounded page size — the Binder ceiling is ~1 MB and is hard.
- **Media is identified as media type + TMDB id (+ season/episode)**: any message keyed on a provider's
  own id, IMDb or TVDB id space (translation is the companion's job, never the contract's).
- **A behaviour variation over the same data model is a capability; a new data model with its own
  lifecycle is a new contract.** Flag a contract that smuggles a second lifecycle in, or a boolean that
  should be a capability.
- Package is `binge.companion.<contract>.v<major>` and the directory mirrors it; `buf.yaml` has no
  `lint.ignore`/`breaking.ignore` and no `# buf:lint:ignore` comment anywhere (never-silence rule).
- Doc comments on messages/fields/rpcs exist and match behaviour; enums document unknown-value
  handling ("receivers ignore values they do not know").

## Dimension 2 — the SDK's security policy (`sdk/`)

The bound Service and hand-off Activity are the attack surface of every companion built on this SDK,
so a weakness here is copied into every one. Read `HostPolicy.kt`, `HandOffPolicy.kt`, `KnownHost.kt`,
`IntegrationService.kt`, `AdvancedRequest.kt`, `CompanionManifest.kt`, `Capabilities.kt` and their
tests against `docs/Architecture.md` > Security: mutual verification and > Hand-offs:

- Caller verification: identity established from the **kernel-supplied** calling UID/package and
  signature, never a parameter or extra; comparison uses a constant-time/exact digest match on the
  signing certificate, not a package-name prefix or a `contains`; multiple signers / rotated
  certificates handled deliberately; behaviour when the host is unknown or `PackageManager` throws
  **fails closed**.
- Hand-off Activity: every Intent extra validated (type, range, media type, id) before use; no Intent
  redirection; the caller-policy tests actually cover the deny paths (read `HostSecurityPolicyTest`,
  `HandOffCallerPolicyTest` for the deny cases, not just the allow ones).
- `KnownHost` list: entries match what the docs say is published (Binge's release certificates, the Play
  App Signing one and the upload one, are pinned — confirm the code and `docs/Status.md` agree on that state).
- Manifest metadata parsing (`majors` read as **either String or Int**, `name` as String or resource id):
  both forms handled, malformed values do not throw or fail open.
- Capability handling: unknown enum values ignored; no behaviour keyed on a version number
  (the `Capability` enum in `request.proto` is the list; `Capabilities.kt` holds only `handshakeResponse()` and
  `requireDeclared()`; also `CompanionManifest.kt`); a gated rpc reachable when its capability is undeclared.
- Coverage honesty: the four excluded classes' KDocs must still say where the decision was split out,
  and `AdvancedRequestKt` must stay **included** (it holds both halves). Check the exclusion list in
  `sdk/build.gradle.kts` has not grown to hide untested logic.

## Dimension 3 — the custom detekt rules (`detekt-rules/`)

- The ruleset is the comment gates (`BannerComment`, `ConsecutiveInlineComments`, `DeclarationCommentShouldBeKDoc`, `KDocLength`, registered in `BingeRuleSetProvider`). Each rule needs a test with a positive **and** a negative case (`CommentRulesTest`); a rule that never fires (malformed
  pattern, wrong PSI type) is a confident false "clean" — run/read the test to confirm it fires.
- Rules registered in the provider and enabled in `detekt.yml`; `detekt.yml` records only deviations
  from defaults (no full ruleset dump); `UnsafeCallOnNullableType` still fires (type-resolution
  classpath wired in `sdk/build.gradle.kts` — without it the `!!` ban silently never runs).
- `!!` or suppressions anywhere in `sdk/src/main`; no `detekt-baseline.xml` exists (the first run found
  nothing; **keep it that way**).

## Dimension 4 — doc-vs-code drift

Extract every checkable claim from `docs/Architecture.md`, `docs/Status.md`, `docs/Roadmap.md`,
`docs/Ecosystem.md`, `README.md`, `CLAUDE.md` and `.ai/agents/*.md` and verify each against the protos,
the SDK, `build.gradle.kts` and the workflows: the action-string table (`com.binge.companion.REQUEST`,
`STREAM`, `TRACKING`, `PLAYER`, `LIBRARY`) vs what exists; the three manifest meta-data keys; capability
lists vs the `Capability` enum in `request.proto` (`Capabilities.kt` has no list); **Status.md contract stages** (Draft / Spike-validated / Stable) vs reality and
its "last updated" date; the 85% floor and the "a new module must apply kover" rule (`:detekt-rules` is the documented exception); JDK/`jvmTarget`
figures; Maven Central / published-artifact claims. Also check whether the docs mention consumers
(`ScottCooper92/binge-seerr`, `ScottCooper92/Binge`) accurately. Report claim → reality → which side
should change. Contract *definitions* must appear only here (nothing in the consumers may redefine one —
if you see a redefinition in a sibling checkout, file it there and link it).

## Dimension 5 — dead, unreachable & orphaned code

- Unused `internal`/`private`/public declarations in `sdk/src/main` and `detekt-rules`; dead enum entries;
  constants whose KDoc describes an abandoned mechanism (check the doc against the implementation).
- Proto messages/fields/rpcs nothing in `contracts/src/test` or the sdk exercises, and enum values no doc
  or test mentions (candidates for "reserved" or "document as unused" — a **human** decision).
- Unused version-catalog entries in `libs.versions.toml`; build-script dead config.
- Check `../binge-seerr` (and `../Binge`) before calling a public SDK symbol dead — the reference companion
  is its main consumer.

## Output contract

- Rank by severity; **confirmed** before **plausible**; every finding grounded at `file:line`
  (`file:field` for protos) with the rule or the failure scenario stated. Wire-compat findings state the
  wire-compatibility story explicitly.
- Substantive findings → GitHub Issues on this repo, deduped against the open backlog first
  (`search_issues`), using labels the repository already has, grouped by theme. Scope, not severity,
  decides issue vs fix-in-this-PR (`CLAUDE.md` > Follow-ups). Micro-nits → a short list in chat. Never a
  `// TODO` comment. **Never propose silencing a gate** (`buf.yaml` exclusions, `# buf:lint:ignore`,
  ktlint disables, a detekt baseline); the answer to a `buf breaking` finding is to make the change
  additive or ask whether it needs a `v2` — that is a human decision, never a refactor.
- State what was **not** audited. A silent partial audit reads as a clean bill; "checked, clean" per
  dimension is a valid result.

## Scaling

- **Quick** (default): reading the two protos, the SDK policy classes and the docs, one pass — this repo
  is small enough that a single agent covers it in minutes.
- **Thorough**: parallel read-only subagents (protos / SDK security / doc drift), each told the exemptions
  above, then an adversarial verification pass (two skeptics per finding, default-refute) before anything
  is filed. Only when the user opts in ("use a workflow").
- **Re-runs**: diff against the previous audit's issues — annotate or reopen rather than re-file.
