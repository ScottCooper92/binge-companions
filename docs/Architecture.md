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
| `com.binge.integration.LIBRARY` | The user's own media server (availability, play, watch state) |

- Binge declares matching `<queries>` entries. Android 11+ needs them for package visibility.
- The Service's manifest `<meta-data>` carries the display name, the icon, and the supported
  contract majors. From this data alone, Binge renders its integrations list and detects a version
  mismatch. Binge does not need to start the companion process for either. The three keys are
  `com.binge.integration.name`, `com.binge.integration.icon` and `com.binge.integration.majors`:

  ```xml
  <meta-data android:name="com.binge.integration.name" android:value="@string/companion_name" />
  <meta-data android:name="com.binge.integration.icon" android:resource="@drawable/ic_companion" />
  <meta-data android:name="com.binge.integration.majors" android:value="1" />
  ```

  A host reads `majors` as **either a String or an Int**, and must read both. aapt types a bare
  number as an Int, so `android:value="1"` — the ordinary way to declare a single major — never
  arrives as a String, while `android:value="1,2"` does. A host that reads only the String form
  reports every single-major companion as having declared nothing, and its author has no way to
  see why. The same applies to `name`, which is a String when written literally and a resource id
  when written as `@string/…`.

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

A second hand-off has no title and no result: `CompanionManifest.ACTION_SETTINGS`, an Activity a
companion may export for the host's "manage" affordance on its row — its own settings or hub. It
takes no extras and answers nothing; the host resolves it by action and package, shows the
affordance only when something resolves, and starts it the same way as the advanced hand-off —
for a result, even though it discards it. A companion with nothing to manage declares nothing.

The check is mutual here too. An exported Activity is reachable by every app on the device, so
before it acts on its extras the companion asks the SDK's `HandOffPolicy` whether the caller is a
host it serves — the same package-and-certificate allowlist its Service pins with `HostPolicy`,
read from `Activity.callingPackage`, which only a caller that asked for a result carries and which
the system, not the caller, sets. `Activity.referrer` is not used for this: it is populated from
ordinary Intent extras before it falls back to the system-tracked caller, so any app could set it
to impersonate a host. Every hand-off is therefore started for a result, so every hand-off has a
`callingPackage` to check. A release companion pins; a debug one may admit any caller, as with the
Service.

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
- STREAM, PLAYER and LIBRARY prefer hand-off over in-app playback. Each contract makes its own
  render-surface decision.
- Binge never renders video. LIBRARY's opt-in `PLAYBACK_SOURCE` hands a short-lived source to a
  player the user chose, and that player renders it. See `Ecosystem.md` > Playback.

## LIBRARY: the user's own media server

Binge decides what to watch and remembers what you decided. REQUEST gets a title into a library.
Neither answers "watch it, and remember that I did". That is the media server's job, and LIBRARY is
the contract for it: whether a title is in the user's library, a way to play it, and what they have
played, with progress.

Jellyfin is the first companion. The shape has to fit Emby and Plex without a `v2`, so nothing in it
names a server's own concepts.

### What crosses, and what does not

- **Identity is the platform's**: media type + TMDB id (+ season/episode), as everywhere. The
  companion translates into its server's item ids and keeps whatever index that needs. A server item
  with no TMDB id is not addressable through this contract; building that index is the companion's
  problem, and it is the same problem the reference companion already solves for REQUEST.
- **Availability answers in the response, not in a status code.** "Not in the library" is what
  `GetAvailability` is for, so it is data: the rpc succeeds and says no. `NOT_FOUND` is left to the
  rpcs that need an item to exist — a play target or a watch state for something the server does not
  have.
- **Every list is paged and artwork is a URL.** The Binder ceiling is a ceiling here too, and a
  continue-watching row is exactly the shape that tempts an author to inline a poster.

### Play is a hand-off, not a stream

`GetPlayTarget` returns what the host should start: an Intent description — package, action, data
URI — for the server's own app where it is installed, and a web URL where it is not.

The alternative, handing back a stream URL, is the one this contract refuses. It would make Binge a
player for someone else's server: the bytes would cross, and transcoding, codec negotiation,
subtitle selection and whatever DRM the server applies would all become the host's problem. The
server's own app already does that work, on the device, with its own account. So the platform moves
data and never bytes, which is the same rule the Play stance above states for STREAM and PLAYER.

A companion with nothing installed to hand to answers with the web URL rather than an error. That is
a worse experience, not a failure, and the host should not have to tell the two apart.

**One opt-in exception: `PLAYBACK_SOURCE`.** A companion may also hand the host a playback source for
an installed player the user chose: a URL minted for the signed-in viewer, short-lived and for one
title, with any headers it needs and an expiry. The host starts the player and renders nothing, so
the bytes still never cross Binder and the transcoding problem stays the server's and the player's.
It is a capability, so a companion that does not want it declares nothing and its titles play through
the server's own app only. The flow, the players and their limits are in `Ecosystem.md` > Playback.

### Watch state flows both ways, on consent

Reading is the default: once the user allows the integration, `GetWatchState` and
`ObserveWatchState` report played, progress and the last played instant, per episode for a series.

Writing is a second, explicit consent, because `SetPlayed` changes data on the user's server. The
two are therefore two capabilities — `WATCH_STATE` and `WATCH_STATE_WRITE` — and not one with a
flag. A companion whose signed-in user may read but not write declares only the first, and the host
hides the affordance rather than offering a control the server would refuse.

### Streams are companion-cadence

`ObserveAvailability` and `ObserveWatchState` push on the companion's schedule, exactly as
`ObserveStatus` does in REQUEST. A companion with a websocket to its server pushes on change; one
that polls pushes when it polls; the host cannot tell which it has and must not try.

The consequence for a host: render what you last received, and never read silence as a signal. "No
update for thirty seconds" means nothing in common between two companions.

### Capabilities

`AVAILABILITY`, `PLAY`, `PLAYBACK_SOURCE`, `WATCH_STATE`, `WATCH_STATE_WRITE`, `CONTINUE_WATCHING`. A companion
declares the set from what its server supports **and** what the signed-in user may do, and the host
hides UI for what is undeclared.

The mandatory core is the handshake alone — smaller than REQUEST's, which also requires submit and
status. REQUEST's core is what every request server does by definition. The servers LIBRARY has to
fit vary more: one may serve availability and nothing else, another may have continue-watching rows
and no way to mark anything played. Gating every rpc is what lets those be the same contract.

As everywhere: feature detection never uses version numbers.

### Discovery

The Service action is `com.binge.integration.LIBRARY`. A companion may serve REQUEST and LIBRARY
from one exported Service or from two; the host binds per action, so which it is stays the
companion's business. Consent is per package, as Security above describes, so a companion serving
both is consented once and its certificate pinned once.

### Where LIBRARY stops

LIBRARY is the user's own server. It is not the other three contracts, and the line
matters because the capability rule — a behaviour variation is a capability, a new data model is a
new contract — is what keeps them apart:

- **STREAM** resolves a title to playable sources that are not the user's library.
- **TRACKING** syncs watch state with a tracker that is not a media server.
- **PLAYER** would hand playback to an external player and receive a progress callback. It is
  deferred.

LIBRARY's play hand-off overlaps PLAYER's territory, and its watch state overlaps TRACKING's. The
overlap is deliberate: a media server plays its own media and knows what you watched, and splitting
that across three contracts would make one companion serve three actions to do one job.

PLAYER is deferred rather than built. The host's Play sheet offers the players already installed,
through Android's standard video intents, and `PLAYBACK_SOURCE` feeds them. What would bring PLAYER
back — live progress, decoding negotiation, track selection — is listed in `Ecosystem.md` > Players.
Whether TRACKING survives LIBRARY is still a decision for when it is actually built.

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
