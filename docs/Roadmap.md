# Roadmap

Build in this order. Each stage proves the previous one before the surface grows. `Ecosystem.md` is
the journey these stages add up to.

1. **Platform record** — this repository, the architecture pages, CI. ✅
2. **REQUEST contract v1 (draft)** — the `.proto` messages and the `RequestService` definition in
   `contracts`. CI gates them with `buf lint` and `buf breaking`. ✅ (draft — the spike below can
   still reshape it)
3. **Host client + stub companion** — the discovery and consent flow in Binge, plus its
   grpc-binder client. A stub companion app proves them on real hardware: a phone and a
   SHIELD-class TV device. This spike validates the transport choice. It also measures the APK
   cost after R8. ✅ (held — see ScottCooper92/Binge#2306 and #1784)
4. **Reference companion: Binge Seerr** — extract the in-tree Seerr integration from Binge into a
   real companion app. It serves REQUEST v1 with production traffic. ✅ at contract parity
   ([binge-seerr](https://github.com/ScottCooper92/binge-seerr)); production traffic is what
   moves the contract out of draft.
5. **SDK + conformance harness** — publish `binge-integration-sdk`: the binder server bootstrap,
   the `SecurityPolicy` wiring, and the handshake scaffold. Also publish a conformance suite.
   Companion authors run it in their own CI. It needs no emulator.
6. **LIBRARY contract** — the user's own media server: whether a title is in their library, a way
   to play it, and what they have played. The shape has to fit Emby and Plex without a `v2`. Play
   is a hand-off; `PLAYBACK_SOURCE` is the one opt-in exception, for an installed player the user
   chose. See `Architecture.md` > LIBRARY.
7. **Jellyfin companion** — the first LIBRARY companion, serving `PLAYBACK_SOURCE`. Its own UI is
   sign-in; browsing and playing the library stay with Jellyfin's own app.
8. **Host playback in Binge** — LIBRARY discovery and consent, availability and watch state on
   title pages, and the Play sheet: the server's own app, or an installed player with progress
   written back. TV first. See `Ecosystem.md` > Playback.
9. **STREAM contract** — resolve a title to playable sources **that are not the user's library**.
   Hand-off first, through the same Play sheet. LIBRARY does not overlap this one: it answers for
   the server the user already runs, STREAM for everything else.

**Deferred: PLAYER.** Handing playback to an external player with a live progress callback. The
Play sheet and the players already installed cover the case for now. It comes back if users hit
the limits `Ecosystem.md` > Players lists: progress lost when a player dies, no decoding
negotiation, no track selection from the host. **Not yet sketched: TRACKING**, watch state synced
with a tracker that is not a media server.

Artifacts publish to Maven Central under `io.github.scottcooper92` when the REQUEST contract is
stable.
