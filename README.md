# binge-integrations

This repository holds the companion-app integration platform for [Binge](https://github.com/ScottCooper92).
Binge is an Android app for movie and TV tracking. It uses TMDB for its data.

Binge ships as a simple TMDB client that obeys Google Play policy. It bundles zero providers.
Companion apps add capability. A companion app is a separate APK. The user can install it from
anywhere. The companion app exports a bound Android Service. That Service implements a contract
from this repository. Binge finds companion apps on the device. The user gives consent for each
one. Binge then shows the companion app's data in Binge's own UI.

This is the Kodi model: a clean host app, with the ecosystem outside it. It is not the Stremio
model, which puts an addon catalog inside the app. There is no dynamic code loading. Each
companion app runs its own code in its own process. Only data crosses the IPC boundary.

> **Status: pre-alpha.** The contracts are in active design. Nothing is stable. No artifacts are
> published. See [docs/Architecture.md](docs/Architecture.md) for the platform decisions,
> [docs/Ecosystem.md](docs/Ecosystem.md) for how Binge, the contracts and the apps work together,
> [docs/Roadmap.md](docs/Roadmap.md) for the build order, and [docs/Status.md](docs/Status.md)
> for progress.

## How it works

- **Discovery.** A companion app exports one bound Service for each capability. Binge matches the
  Service by its intent action (`com.binge.integration.REQUEST`, `.STREAM`, `.TRACKING`,
  `.PLAYER`). The manifest `<meta-data>` carries the display name, the icon, and the contract
  version. Binge can list installed companion apps without a start of their processes.
- **Transport.** Calls cross the app boundary as gRPC. The transport is the official Android
  Binder transport (`io.grpc:grpc-binder`). The messages are protocol buffers. The `.proto` files
  in `contracts/` are the normative contract. A companion app implements a generated service base.
  With grpc-kotlin, the methods are suspend functions and `Flow`s. You can unit-test an
  implementation on the JVM without a device. Page all results. Send artwork as URLs, never as
  bytes. This respects the Binder transaction limit of about 1 MB.
- **Media identity.** Every payload identifies media as media type + TMDB id (+ season/episode).
  The companion app translates to other id spaces.
- **Versioning.** The proto package version (for example `v1` in `binge.integration.request.v1`)
  is the contract major version. Changes inside a package must be additive. CI enforces this with
  `buf breaking`. A handshake rpc opens every connection. The handshake declares the companion
  app's capabilities.

## Security model

Verification is mutual. It is part of the contract:

- **Host side.** Binge asks the user for consent for each companion app. The consent record holds
  the package name and the signing-cert hash. Binge validates every URL and Intent from a
  companion app before use. Binge's TMDB session never crosses the boundary.
- **Companion side.** A companion app must verify the caller's signing certificate before it
  serves a request. The SDK supplies this check. The exported Service guards the user's provider
  session from other apps on the device.

## Capabilities

| Action | Purpose | Status |
| --- | --- | --- |
| `com.binge.integration.REQUEST` | Request movies and shows on a media-request server; track and manage request status | Draft |
| `com.binge.integration.STREAM` | Resolve a title to playable sources (hand-off first) | Planned |
| `com.binge.integration.TRACKING` | Sync watch state with an external tracker | Planned |
| `com.binge.integration.PLAYER` | Hand off playback to an external player, with a progress callback | Planned |

## Planned artifacts

| Coordinates | Contents |
| --- | --- |
| `io.github.scottcooper92:binge-integration-contracts` | Protobuf messages and gRPC service stubs (protobuf-javalite, grpc-kotlin; Kotlin/JVM) |
| `io.github.scottcooper92:binge-integration-sdk` | Android library for companion apps: binder server bootstrap, `SecurityPolicy` caller verification, handshake scaffold |
| `io.github.scottcooper92:binge-integration-conformance` | Test harness that validates a companion app against the contract |

## Repository layout

```
contracts/   The .proto contracts + generated Kotlin/JVM stubs (messages, gRPC services)
sdk/         Android library for companion apps: the exported Service base, the caller policy,
             the manifest keys and the handshake helper
```

The conformance harness joins when it is built. The reference companion app lives in its own
repository, [binge-seerr](https://github.com/ScottCooper92/binge-seerr).

## Building

Run:

```sh
./gradlew build
```

That compiles both modules, runs the tests, checks formatting, runs detekt over the SDK and
holds its line coverage to 85%. `./gradlew ktlintFormat` fixes formatting in place.

You need JDK 17 or later.

## License

[Apache-2.0](LICENSE)
