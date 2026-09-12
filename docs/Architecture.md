# Architecture

These decisions are locked at the direction level. The contract shapes stay a draft until a real
companion app proves them with real traffic.

## Transport: companion APKs over Binder IPC

A companion app exports one bound Service for each capability. Binge matches the Service by its
intent action:

| Action | Capability |
| --- | --- |
| `com.binge.integration.REQUEST` | Media-request servers (request, track, manage) |
| `com.binge.integration.STREAM` | Resolve a title to playable sources |
| `com.binge.integration.TRACKING` | Sync watch state with an external tracker |
| `com.binge.integration.PLAYER` | External playback with a progress callback |

- Binge declares matching `<queries>` entries. Android 11+ needs them for package visibility.
- The Service's manifest `<meta-data>` carries the display name, the icon, and the supported
  contract majors. From this data alone, Binge renders its integrations list and detects a version
  mismatch. Binge does not need to start the companion process for either.

## RPC layer: gRPC over Binder, protobuf payloads

Calls cross the app boundary as gRPC. The transport is the official Android Binder transport
(`io.grpc:grpc-binder`). The messages are protocol buffers (`protobuf-javalite` at runtime). The
`.proto` files in `contracts/` are the normative contract. The stubs, the docs, and the
conformance harness generate from them.

The earlier design was a thin AIDL surface with JSON payloads. gRPC replaced it for these reasons:

- The service definition documents itself. Typed rpcs replace AIDL methods that carry opaque JSON
  strings.
- An integration author implements a generated service base. With grpc-kotlin, the methods are
  suspend functions and `Flow`s. There is no marshalling code and no custom callback protocol.
- A gRPC service runs over any channel. An author can unit-test an implementation on the JVM
  without a device. The conformance harness needs no emulator.
- Streaming rpcs replace hand-made callback interfaces. Deadlines, cancellation, and a standard
  error model come with the framework.
- The `SecurityPolicy` API in `grpc-binder` does the mutual signing-cert verification. That makes
  it configuration, not custom security code.

Practical rules:

- Errors travel as gRPC status codes. Response messages never carry error fields. Each contract
  documents its code mapping in its `.proto` file.
- Some codes come from the transport rather than from an integration, and a host has to handle
  them even though no integration ever chooses them. An integration that is **not installed**
  reads as `UNIMPLEMENTED`, not `UNAVAILABLE` — grpc-binder reports a `bindService()` that
  returned false that way. `request.proto` documents the full set.
- Whether a **killed** integration surfaces as an error at all is platform-dependent. The same
  force-stop answered `UNAVAILABLE` on a phone and `OK` on a SHIELD, which had already restarted
  the service. So "the integration died" is not detectable from a status code alone: the
  package-removed broadcast covers the permanent case, and the rest is ordinary retry.
- The Binder transaction limit is about 1 MB. Page all results. Send artwork as URLs, never as
  bytes.
- The REQUEST stub spike validated the transport on real hardware. Bind-to-handshake p95 was
  119–128 ms on a phone (Android 16) and 22–37 ms on a SHIELD (Android 11), against a proposed
  ~300 ms bar — so the TV, the surface the spike existed to worry about, is the faster of the two
  by roughly 4x. The APK cost after R8 is a separate build measurement and is not yet recorded.

## Media identity

Every payload identifies media as **media type + TMDB id (+ season/episode)**. The companion app
owns translation into other id spaces: its server's ids, IMDb, TVDB, and so on.

## Capabilities

The intent action is the discovery unit. The capability set is the feature-detection unit inside
it. Each contract keeps a small mandatory core. For REQUEST, the core is handshake, submit, and
status. A declared capability gates every other rpc.

- **Static capabilities.** The handshake response declares them once. They cover everything this
  connection can ever do, for this provider and this user. The host hides UI for undeclared
  capabilities. The host never calls a gated rpc without its capability.
- **Dynamic per-item actions.** Each status response lists the subset that applies to that title
  now. For example, approve appears only on a pending request that the user may moderate.

The boundary rule: a behavior variation over the same data model is a capability. A new data model
with its own lifecycle is a new contract. Capability enums grow by appending. Peers ignore values
they do not know. Feature detection never uses version numbers.

### Hand-offs

Some capabilities are not an rpc but a screen. Provider-specific UI lives in the companion app, so
where the host cannot render a choice — REQUEST's advanced options are the first case: destination
server, quality profile, root folder — the companion exports an Activity and the host starts it for
a result with the title as extras. The companion owns the whole flow and the submit. It answers
`RESULT_OK` once it has submitted; the host re-reads status either way. No option schema crosses
the boundary. The action and the extras are named in the SDK's `CompanionManifest`, and the
host resolves the Activity by action and package, on the companion the user consented to, before
it starts anything.

The check is mutual here too. An exported Activity is reachable by every app on the device, so
before it acts on its extras the companion asks the SDK's `HandOffPolicy` whether the caller is a
host it serves — the same package-and-certificate allowlist its Service pins with `HostPolicy`,
read from `Activity.callingPackage`, which only a caller that asked for a result carries. A
release companion pins; a debug one may admit any caller, as with the Service.

## Security: mutual verification

- **Host side.** Binge asks the user for consent for each companion app. The consent record holds
  the package name and the signing-cert hash. Binge validates every URL or Intent from a companion
  app before use. Binge's TMDB session never crosses the boundary.
- **Companion side.** The companion app verifies the caller's signing certificate before it serves
  a request. Its exported Service fronts the user's provider session. Without the check, any app
  on the device could drive that session.
- Both checks use `grpc-binder` `SecurityPolicy` instances. The SDK wires them on each side.

## Play stance

- No bundled providers. No in-app plugin directory. No promotion of infringing companion apps.
- STREAM and PLAYER prefer hand-off over in-app playback. Each contract makes its own
  render-surface decision.

## Versioning

Capabilities answer "what can you do?". Versions answer "can we parse each other?".

- The proto package version (`binge.integration.request.v1`) is the contract **major**. Inside a
  package, every change must be additive: new fields, new enum values, new rpcs. CI fails any
  other change with `buf breaking`. Additive changes need no negotiation. Protobuf field numbers
  and unknown-field preservation keep old and new peers compatible in both directions.
- A breaking change becomes a new package (`v2`) with a new service. A companion app serves `v1`
  and `v2` side by side from the same exported Service. The manifest `<meta-data>` lists the
  majors a companion app serves. The host picks the highest common major before it binds. When
  there is no common major, the host shows "update Binge" or "update the companion app".
- A major bump is an escape hatch, not a tool. The append-only rule is the compatibility story.
