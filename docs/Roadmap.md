# Roadmap

Build in this order. Each stage proves the previous one before the surface grows.

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
6. **STREAM contract** — resolve a title to playable sources. Hand-off first.
7. **PLAYER contract** — hand off playback to an external player, with a progress callback. Watch
   tracking survives the hand-off.

Artifacts publish to Maven Central under `io.github.scottcooper92` when the REQUEST contract is
stable.
