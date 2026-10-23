# Status

This page shows where each contract and each platform piece stands. The stages are:

**Not started → In design → Draft → Spike-validated → Stable (published)**

- *Draft*: the `.proto` is in `contracts/`.
- *Spike-validated*: a real companion app has served the contract on hardware.
- *Stable*: the artifacts are on Maven Central. The append-only guarantee applies.

A contract can also be *Deferred*: a decision, not a stage in the progression above — set aside on
purpose, with what would bring it back written down.

_Last updated: 2026-10-23._

## Contracts

| Contract | Action | Stage | Notes |
| --- | --- | --- | --- |
| REQUEST | `com.binge.companion.REQUEST` | **Draft** | The v1 messages and `RequestService` are in `contracts/`. The transport spike held on a phone and a SHIELD-class TV (tracked privately), and the reference companion serves every v1 operation against a real Seerr instance (`ListRequests` and `GetStatuses` since ScottCooper92/binge-seerr#717). `REQUEST_FILTER_MINE_RECENTLY_AVAILABLE` and its capability, `CAPABILITY_LIST_MINE_RECENTLY_AVAILABLE`, are additive in the same `v1` package: the signed-in user's own recently available requests, which a host notifies from, since `RECENTLY_AVAILABLE` holds every user's (#193). Notification forwarding began here as `CAPABILITY_FORWARD_NOTIFICATIONS` and moved to the NOTIFICATIONS contract (below); that capability number and name are reserved. Request ids are opaque strings, so a provider with UUID or slug ids can serve v1; empty means unset (#119). That was a type change, accepted because v1 is still Draft. The same holds for the generic-provider revision: `is_4k` became a string `version_id` declared through `HandshakeResponse.versions` (which replaces `CAPABILITY_REQUEST_4K`; a companion that lists none has the standard version only), `ReportIssue` gained a version, a season, an episode and provider-defined categories (`CAPABILITY_ISSUE_CATEGORIES`, `ListIssueCategories`), `DeclineRequest` gained an optional `reason`, and `CancelRequest` is documented as never deleting media files. `buf breaking` fails on that revision by design, and the old numbers and names are reserved. The stage moves once that companion has served the contract on hardware with production traffic. |
| LIBRARY | `com.binge.companion.LIBRARY` | **Draft** | The user's own media server: availability, a play hand-off, watch state both ways, continue watching. The decisions are recorded in `Architecture.md`. The v1 messages are in `contracts/` (#19); the SDK support is #20. Jellyfin is the first companion. `PLAYBACK_SOURCE` and `PLAYBACK_PROGRESS` landed additively: a source Binge's own player renders, with the device profile, subtitle files, chapters and skippable segments beside it, and live progress reported back against the session it came with. |
| STREAM | `com.binge.companion.STREAM` | **Draft** | Playable sources that are not the user's library, as a server-streaming `Resolve` of shared `PlaybackSource`s. The v1 messages are in `contracts/`, and the host protections are recorded in `Architecture.md` > STREAM. Hand-off only. Gated: no host ships STREAM UI until a first-party companion exists that resolves licit sources, and the stage moves once one has served it. Not deferred: binge-archive, that companion, is being built (#141), and so is Binge's STREAM host, behind a feature flag. `Resolve` answers sources, OK and empty, `NOT_FOUND` (not in the catalogue, not a failure) or `INVALID_ARGUMENT`. The host's `Resolve` deadline is 10 s (#148). A source carries its licence as `licence_label` and `licence_kind` (#247). |
| NOTIFICATIONS | `com.binge.companion.NOTIFICATIONS` | **Draft** | The companion hands the host the notifications it would have shown itself: `SetNotificationForwarding`, `ListNotifications` (paged, polled on the host's schedule) and `AcknowledgeNotifications`, carried as the shared `ForwardedNotification` (`Architecture.md` > Notifications). Its own contract so a companion has one switch, one queue and one id space. A core `Handshake` with an empty capability set, so a later optional rpc can be gated by a capability. Needs no provider session. Moved out of REQUEST while both were Draft. |
| TRACKING | `com.binge.companion.TRACKING` | Not started | |
| PLAYER | `com.binge.companion.PLAYER` | Deferred | Binge's own player covers a LIBRARY title, and the Play sheet the players people already have (#51). See `Architecture.md` > Where LIBRARY stops and `Ecosystem.md` > Players for what would bring it back. |

## Platform pieces

| Piece | Stage | Notes |
| --- | --- | --- |
| `binge-companion-contracts` | Draft | Builds in this repository (protobuf-javalite + grpc-kotlin stubs). Consumed as source: Binge vendors a pinned copy of the `.proto` files, and binge-seerr includes this build through a submodule. Not yet published. |
| `binge-companion-sdk` | Draft | `sdk/` builds here: `IntegrationService` (Binder server bootstrap), `HostPolicy` (the Service's caller verification) and `HandOffPolicy` (the hand-off Activities': the advanced picker's, the view-request one's and the settings one's), `CompanionManifest` keys, hand-off actions and extras, the host title and companion links (`hostTitleUri`, `binge://title/…`; `hostCompanionUri`, `binge://companions/…`), Binge's Play listing URL, handshake helper. binge-seerr consumes it as source through the same submodule. `BingeHosts.release` pins release Binge's Play App Signing certificate, the one every Play install carries. Not yet published. |
| Conformance harness | Not started | Runs over any channel, so companion authors need no emulator. |
| Reference companion (Binge Seerr) | Draft | [binge-seerr](https://github.com/ScottCooper92/binge-seerr) serves every REQUEST v1 operation against the connected Seerr instance, the capability set is derived from the signed-in user's permissions, and the setup screen is built on the shared design system. The in-tree Seerr integration it replaces is deleted from Binge. |
| Host support in Binge | Draft | Discovery, consent and the grpc-binder client are written and unit-tested, and each companion is exposed through a `RequestIntegration` contributed by the bridge. Binge's emulator lane drives bind → handshake → request between Binge and a stub companion over a real Binder, and the hardware spike measured latency, memory and APK cost (tracked privately). |
