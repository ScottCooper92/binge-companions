# Status

This page shows where each contract and each platform piece stands. The stages are:

**Not started → In design → Draft → Spike-validated → Stable (published)**

- *Draft*: the `.proto` is in `contracts/`.
- *Spike-validated*: a real companion app has served the contract on hardware.
- *Stable*: the artifacts are on Maven Central. The append-only guarantee applies.

A contract can also be *Deferred*: a decision, not a stage in the progression above — set aside on
purpose, with what would bring it back written down.

_Last updated: 2026-10-03._

## Contracts

| Contract | Action | Stage | Notes |
| --- | --- | --- | --- |
| REQUEST | `com.binge.companion.REQUEST` | **Draft** | The v1 messages and `RequestService` are in `contracts/`. The transport spike held on a phone and a SHIELD-class TV (ScottCooper92/Binge#2306), and the reference companion serves every v1 operation except `ListRequests` and `GetStatuses` against a real Seerr instance (those two are #133). The stage moves once that companion has served the contract on hardware with production traffic. |
| LIBRARY | `com.binge.companion.LIBRARY` | **Draft** | The user's own media server: availability, a play hand-off, watch state both ways, continue watching. The decisions are recorded in `Architecture.md`. The v1 messages are in `contracts/` (#19); the SDK support is #20. Jellyfin is the first companion. v1 ships without `PLAYBACK_SOURCE`; it arrives later as an additive capability. |
| STREAM | `com.binge.companion.STREAM` | **Draft** | Playable sources that are not the user's library, as a server-streaming `Resolve` of shared `PlaybackSource`s. The v1 messages are in `contracts/`, and the host protections are recorded in `Architecture.md` > STREAM. Hand-off only. Gated: no host ships STREAM UI until a first-party companion exists that resolves licit sources, and the stage moves once one has served it. |
| TRACKING | `com.binge.companion.TRACKING` | Not started | |
| PLAYER | `com.binge.companion.PLAYER` | Deferred | The Play sheet and the players people already have cover the case for now (#51). See `Architecture.md` > Where LIBRARY stops and `Ecosystem.md` > Players for what would bring it back. |

## Platform pieces

| Piece | Stage | Notes |
| --- | --- | --- |
| `binge-companion-contracts` | Draft | Builds in this repository (protobuf-javalite + grpc-kotlin stubs). Consumed as source: Binge vendors a pinned copy of the `.proto` files, and binge-seerr includes this build through a submodule. Not yet published. |
| `binge-companion-sdk` | Draft | `sdk/` builds here: `IntegrationService` (Binder server bootstrap), `HostPolicy` (the Service's caller verification) and `HandOffPolicy` (the hand-off Activities': the advanced picker's and the settings one's), `CompanionManifest` keys, hand-off actions and extras, handshake helper. binge-seerr consumes it as source through the same submodule. `BingeHosts.release` pins release Binge's Play App Signing certificate, the one every Play install carries. Not yet published. |
| Conformance harness | Not started | Runs over any channel, so companion authors need no emulator. |
| Reference companion (Binge Seerr) | Draft | [binge-seerr](https://github.com/ScottCooper92/binge-seerr) serves every REQUEST v1 operation except `ListRequests` and `GetStatuses` (#133) against the connected Seerr instance, the capability set is derived from the signed-in user's permissions, and the setup screen is built on the shared design system. The in-tree Seerr integration it replaces is deleted from Binge. |
| Host support in Binge | Draft | Discovery, consent and the grpc-binder client are written and unit-tested, and each companion is exposed through a `RequestIntegration` contributed by the bridge. Binge's emulator lane drives bind → handshake → request between Binge and a stub companion over a real Binder, and the hardware spike measured latency, memory and APK cost (ScottCooper92/Binge#2306, #1784). |
