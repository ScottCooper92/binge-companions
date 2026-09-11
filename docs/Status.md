# Status

This page shows where each contract and each platform piece stands. The stages are:

**Not started → In design → Draft → Spike-validated → Stable (published)**

- *Draft*: the `.proto` is in `contracts/`.
- *Spike-validated*: a real companion app has served the contract on hardware.
- *Stable*: the artifacts are on Maven Central. The append-only guarantee applies.

_Last updated: 2026-09-11._

## Contracts

| Contract | Action | Stage | Notes |
| --- | --- | --- | --- |
| REQUEST | `com.binge.integration.REQUEST` | **Draft** | The v1 messages and `RequestService` are in `contracts/`. The phone/TV transport spike has run and held (ScottCooper92/Binge#2306) — the shape stays draft until a real companion serves it with production traffic. |
| STREAM | `com.binge.integration.STREAM` | In design | Hand-off-first render surface. No `.proto` yet. |
| TRACKING | `com.binge.integration.TRACKING` | Not started | |
| PLAYER | `com.binge.integration.PLAYER` | In design | External-player hand-off with a progress callback. Watch tracking survives the hand-off. |

## Platform pieces

| Piece | Stage | Notes |
| --- | --- | --- |
| `binge-integration-contracts` | Draft | Builds in this repository (protobuf-javalite + grpc-kotlin stubs). Not yet published. |
| `binge-integration-sdk` | Not started | Binder server bootstrap, `SecurityPolicy` wiring, handshake scaffold. |
| Conformance harness | Not started | Runs over any channel, so companion authors need no emulator. |
| Reference companion (Binge Seerr) | Not started | The repository exists and builds, but holds a placeholder rather than a companion. The extraction of the in-tree Seerr integration follows the spike. |
| Host support in Binge | In progress | Discovery, consent and the grpc-binder client are written and unit-tested. What they have never done is talk to a companion across a real Binder boundary — that is the spike. |
