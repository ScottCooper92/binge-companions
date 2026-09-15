# Status

This page shows where each contract and each platform piece stands. The stages are:

**Not started → In design → Draft → Spike-validated → Stable (published)**

- *Draft*: the `.proto` is in `contracts/`.
- *Spike-validated*: a real companion app has served the contract on hardware.
- *Stable*: the artifacts are on Maven Central. The append-only guarantee applies.

_Last updated: 2026-09-15._

## Contracts

| Contract | Action | Stage | Notes |
| --- | --- | --- | --- |
| REQUEST | `com.binge.integration.REQUEST` | **Draft** | The v1 messages and `RequestService` are in `contracts/`. The transport spike held on a phone and a SHIELD-class TV (ScottCooper92/Binge#2306), and the reference companion serves every v1 operation against a real Seerr instance. The stage moves once that companion has served the contract on hardware with production traffic. |
| LIBRARY | `com.binge.integration.LIBRARY` | **Draft** | The user's own media server: availability, a play hand-off, watch state both ways, continue watching. The decisions are recorded in `Architecture.md`. The v1 messages are in `contracts/` (#19); the SDK support is #20. Jellyfin is the first companion. |
| STREAM | `com.binge.integration.STREAM` | In design | Hand-off-first render surface. No `.proto` yet. |
| TRACKING | `com.binge.integration.TRACKING` | Not started | |
| PLAYER | `com.binge.integration.PLAYER` | In design | External-player hand-off with a progress callback. Watch tracking survives the hand-off. |

## Platform pieces

| Piece | Stage | Notes |
| --- | --- | --- |
| `binge-integration-contracts` | Draft | Builds in this repository (protobuf-javalite + grpc-kotlin stubs). Consumed as source: Binge vendors a pinned copy of the `.proto` files, and binge-seerr includes this build through a submodule. Not yet published. |
| `binge-integration-sdk` | Draft | `sdk/` builds here: `IntegrationService` (Binder server bootstrap), `HostPolicy` (the Service's caller verification) and `HandOffPolicy` (the hand-off Activities': the advanced picker's and the settings one's), `CompanionManifest` keys, hand-off actions and extras, handshake helper. binge-seerr consumes it as source through the same submodule. Binge's release certificate digest is not yet published, so `BingeHosts.release` matches nothing until it is, and only a debug build of Binge passes a companion's policy. Not yet published. |
| Conformance harness | Not started | Runs over any channel, so companion authors need no emulator. |
| Reference companion (Binge Seerr) | Draft | [binge-seerr](https://github.com/ScottCooper92/binge-seerr) is at contract parity: its exported Service serves every REQUEST v1 operation against the connected Seerr instance, the capability set is derived from the signed-in user's permissions, and the setup screen is built on the shared design system. The in-tree Seerr integration it replaces is deleted from Binge. |
| Host support in Binge | Draft | Discovery, consent and the grpc-binder client are written and unit-tested, and each companion is exposed through a `RequestIntegration` contributed by the bridge. Binge's emulator lane drives bind → handshake → request between Binge and a stub companion over a real Binder, and the hardware spike measured latency, memory and APK cost (ScottCooper92/Binge#2306, #1784). |
