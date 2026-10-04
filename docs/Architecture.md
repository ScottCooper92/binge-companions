# Architecture

These decisions are locked at the direction level. The contract shapes stay a draft until a real
companion app proves them with real traffic.

## Transport: companion APKs over Binder IPC

A companion app exports one bound Service for each capability. Binge matches the Service by its
intent action:

| Action | Capability |
| --- | --- |
| `com.binge.companion.REQUEST` | Media-request servers (request, track, manage) |
| `com.binge.companion.STREAM` | Resolve a title to playable sources |
| `com.binge.companion.TRACKING` | Sync watch state with an external tracker |
| `com.binge.companion.PLAYER` | External playback with a progress callback |
| `com.binge.companion.LIBRARY` | The user's own media server (availability, play, watch state) |

- Binge declares matching `<queries>` entries. Android 11+ needs them for package visibility.
- The Service's manifest `<meta-data>` carries the display name, the icon, and the supported
  contract majors. From this data alone, Binge renders its integrations list and detects a version
  mismatch. Binge does not need to start the companion process for either. The three keys are
  `com.binge.companion.name`, `com.binge.companion.icon` and `com.binge.companion.majors`:

  ```xml
  <meta-data android:name="com.binge.companion.name" android:value="@string/companion_name" />
  <meta-data android:name="com.binge.companion.icon" android:resource="@drawable/ic_companion" />
  <meta-data android:name="com.binge.companion.majors" android:value="1" />
  ```

  A host reads `majors` as **either a String or an Int**, and must read both. aapt types a bare
  number as an Int, so `android:value="1"` — the ordinary way to declare a single major — never
  arrives as a String, while `android:value="1,2"` does. A host that reads only the String form
  reports every single-major companion as having declared nothing, and its author has no way to
  see why. The same applies to `name`, which is a String when written literally and a resource id
  when written as `@string/…`.
- A companion may serve more than one contract from the same exported Service — REQUEST and
  LIBRARY together, say, rather than one Service per action — since consent and the certificate pin
  are per package, not per Service. A Service is free to declare more than one `<intent-filter>`
  action; what it may not do is declare a single, bare `majors` for more than one of them, since
  `majors` alone cannot say which contract's package a value names. The host first reads which
  actions the Service's `<intent-filter>`s name, then reads majors **per contract** for a
  multi-action Service: `com.binge.companion.majors.request`, `com.binge.companion.majors.library`,
  and so on, one key per action the Service filters on. A single-action Service keeps using the
  bare `com.binge.companion.majors` key exactly as above; the per-contract keys only apply once a
  Service names more than one action. Both forms follow the same String-or-Int reading rule.

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
- A sentence for the user travels in gRPC's rich error model, never in the status description: a
  `google.rpc.Status` in the `grpc-status-details-bin` trailer, holding an `ErrorInfo` and a
  `LocalizedMessage` in `HostInfo.locale`. This applies to every contract, STREAM included;
  `request.proto` states it in full.
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
- Every paged rpc follows the same rules. A short page is not the end; only an empty
  `next_page_token` says that. A `page_token` is only valid with the request it was issued under:
  the same `page_size` and the same filter, where the rpc has one. The host keeps `page_size` the
  same on every page of one listing. A token the integration did not issue, or one that comes back
  with a different `page_size` or filter, is `INVALID_ARGUMENT`. Each `.proto` repeats this on its
  paged rpcs.
- The REQUEST stub spike validated the transport on real hardware. Bind-to-handshake p95 was
  119–128 ms on a phone (Android 16) and 22–37 ms on a SHIELD (Android 11), against a proposed
  ~300 ms bar — so the TV, the surface the spike existed to worry about, is the faster of the two
  by roughly 4x. The APK cost after R8 is a separate build measurement and is not yet recorded.

## Media identity

Every payload identifies media as **media type + TMDB id**, with seasons or an episode carried beside it where an rpc needs them (`season_numbers`, LIBRARY's `EpisodeRef`), never inside `MediaId`. The companion app
owns translation into other id spaces: its server's ids, IMDb, TVDB, and so on.

## Capabilities

The intent action is the discovery unit. The capability set is the feature-detection unit inside
it. Each contract keeps a small mandatory core. For REQUEST, the core is handshake, submit, and
status. A declared capability gates every other rpc.

- **Static capabilities.** The handshake response declares them. They cover everything the
  connection can do for this provider and this user, with the session the integration holds when
  it answers. The host hides UI for undeclared capabilities. The host never calls a gated rpc
  without its capability.
  - For REQUEST: with no working session (nothing connected, or a broken one), the handshake still answers OK,
    declaring only what needs no session plus `CAPABILITY_ATTENTION`, so the host can learn about
    the session at all (#106).
  - For REQUEST, that set is not final for the connection. The host handshakes again when the session may have
    come back: when `needs_reconnect` turns false, and when the user returns from the integration
    app after the host sent them there for an `UNAUTHENTICATED`. The integration answers each
    handshake from its current session (#111).
  - For LIBRARY, which has no attention read, the handshake answers `UNAUTHENTICATED` with no working
    session, and that error is the signal. The host handshakes again when the user returns from the
    integration app after it sent them there for an `UNAUTHENTICATED` (#113).
- **Dynamic per-item actions.** Each status response's `allowed_actions` lists the title-level
  subset — capabilities that aren't about any one existing request. A capability that is about one
  specific request (approve, decline, retry, cancel, edit seasons) travels on that request's own
  `allowed_actions` instead, so it can say "approve applies to request #7, not #8" — one flat
  title-level list cannot (REQUEST#63).

The boundary rule: a behavior variation over the same data model is a capability. A new data model
with its own lifecycle is a new contract. Capability enums grow by appending. Peers ignore values
they do not know. Feature detection never uses version numbers.

### Hand-offs

Some capabilities are not an rpc but a screen. Provider-specific UI lives in the companion app, so
where the host cannot render a choice, the companion exports an Activity and the host starts it for
a result with the title as extras. The companion owns the whole flow and the submit. It answers
`RESULT_OK` once it has submitted; the host re-reads status either way. No option schema crosses
the boundary. The action and the extras are named in the SDK's `CompanionManifest`, and the
host resolves the Activity by action and package, on the companion the user consented to, before
it starts anything.

REQUEST's advanced options — destination server, quality profile, root folder — were this pattern's
first case (`CAPABILITY_ADVANCED_OPTIONS`), and still are for a companion whose advanced flow
genuinely isn't reducible to a choice list. But "the host cannot render a choice" turned out to be
true only while the choices had no shared shape to cross the boundary in. Once a destination is
modelled as a handful of named axes, each a list of `{id, label}` choices with one preselected
(`CAPABILITY_ADVANCED_REQUEST_OPTIONS`, `GetAdvancedRequestOptions` / `GetDestinationOptions` /
`SubmitAdvancedRequest`), the host renders its own picker from data instead of handing off —
same boundary rule as everywhere else in the contract (a behavior variation over one data model is
a capability), not an exception to it. The two capabilities coexist: a companion declares whichever
fits its flow, and a host that only understands the older one keeps getting the hand-off.

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
`callingPackage` to check. A release companion pins; a debug one admits Binge's package names under any certificate
(`HandOffPolicy.anyCertificateOf`), as with the Service.

One link runs the other way, from the companion to the host: the title link,
`binge://title/<movie|tv>/<tmdbId>`. It is part of the platform. Binge answers it, and a companion
that offers "open in Binge" builds it with the SDK's `CompanionManifest.hostTitleUri` and starts it
with `ACTION_VIEW`. The scheme is `CompanionManifest.HOST_TITLE_SCHEME`. The link carries the media
type and the TMDB id and nothing else, as Media identity requires.

Its trust stance differs from the other hand-offs on purpose. They are pinned by package and
certificate. This link is resolved by its scheme alone, and any installed app can claim `binge://`.
That is acceptable because the payload is a public TMDB id: an app that intercepts it learns which
title the user opened, and nothing about the provider or the user's session. A companion that wants
to narrow it can `setPackage` to `BingeHosts.RELEASE_PACKAGE_NAME` (or `DEBUG_PACKAGE_NAME` in a
debug build). That only narrows it, since a package name is re-claimable on a sideloading device;
it does not check the certificate. The SDK offers no helper for it.

On API 30 and above, package visibility hides Binge from a companion that has not declared it. To
ask whether Binge answers the link before starting it, a companion declares
`<queries><intent><action android:name="android.intent.action.VIEW" /><data android:scheme="binge" /></intent></queries>`
in its manifest.

## Security: mutual verification

- **Host side.** Binge asks the user for consent for each companion app. The consent record holds
  the package name and the signing-cert hash. Binge validates every URL or Intent from a companion
  app before use: a URL is http or https, or it is refused. Binge's TMDB session never crosses the boundary.
- **Companion side.** The companion app verifies the caller's signing certificate before it serves
  a request. Its exported Service fronts the user's provider session. Without the check, any app
  on the device could drive that session.
- Both checks use `grpc-binder` `SecurityPolicy` instances. The SDK wires them on each side.
- Both match a certificate anywhere in the peer's signing lineage, so a key rotated through APK
  Signature Scheme v3 (as Play App Signing rotates one) keeps matching a digest pinned before it (#115).
- A debug companion cannot pin debug Binge's certificate, since each developer signs with their own key,
  so it uses `HostPolicy.anyCertificateOf`: Binge's package names, any signer. `anyCaller` admits every
  app and is only for a conformance harness or an author's own test host (#122).

## Play stance

- No bundled providers. No in-app plugin directory. No promotion of infringing companion apps.
- STREAM, PLAYER and LIBRARY prefer hand-off over in-app playback. Each contract makes its own
  render-surface decision; STREAM's is hand-off only, with the host protections under STREAM below.
- Binge never renders video. LIBRARY's opt-in `PLAYBACK_SOURCE` (planned; v1 ships without it) will
  hand a short-lived source to a player the user chose, and that player renders it. See
  `Ecosystem.md` > Playback.

## LIBRARY: the user's own media server

Binge decides what to watch and remembers what you decided. REQUEST gets a title into a library.
Neither answers "watch it, and remember that I did". That is the media server's job, and LIBRARY is
the contract for it: whether a title is in the user's library, a way to play it, and what they have
played, with progress.

Jellyfin is the first companion. The shape has to fit Emby and Plex without a `v2`, so nothing in it
names a server's own concepts.

### What crosses, and what does not

- **Identity is the platform's**: media type + TMDB id, with an episode beside it as `EpisodeRef`, as everywhere. The
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

**One opt-in exception: `PLAYBACK_SOURCE`.** It is planned, not in v1: the `Capability` enum does not
have it yet, and it arrives later as an additive change. Once it exists, a companion may also hand
the host a playback source for an installed player the user chose: a URL minted for the signed-in
viewer, short-lived and for one title, with any headers it needs and an expiry. The host starts the
player and renders nothing, so the bytes still never cross Binder and the transcoding problem stays
the server's and the player's. It is a capability, so a companion that does not want it declares
nothing and its titles play through the server's own app only. The flow, the players and their
limits are in `Ecosystem.md` > Playback.

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

`AVAILABILITY`, `PLAY`, `WATCH_STATE`, `WATCH_STATE_WRITE`, `CONTINUE_WATCHING`. `PLAYBACK_SOURCE` joins
them later, additively. A companion declares the set from what its server supports **and** what the
signed-in user may do, and the host hides UI for what is undeclared.

The mandatory core is the handshake alone — smaller than REQUEST's, which also requires submit and
status. REQUEST's core is what every request server does by definition. The servers LIBRARY has to
fit vary more: one may serve availability and nothing else, another may have continue-watching rows
and no way to mark anything played. Gating every rpc is what lets those be the same contract.

As everywhere: feature detection never uses version numbers.

### Discovery

The Service action is `com.binge.companion.LIBRARY`. A companion may serve REQUEST and LIBRARY
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
through Android's standard video intents, and `PLAYBACK_SOURCE`, once it exists, feeds them. What would
bring PLAYER back — live progress, decoding negotiation, track selection — is listed in
`Ecosystem.md` > Players.
Whether TRACKING survives LIBRARY is still a decision for when it is actually built.

## STREAM: sources outside the library

LIBRARY answers for the server the user already runs. STREAM answers for everything else an
integration can legitimately resolve a title to. It is the contract with the thinnest licit
surface and the largest policy exposure, so its first decision is a gate, not a message shape.

### The gate

A host ships no STREAM UI until a first-party companion exists that resolves licit sources, and
the proto stays `Draft` until that companion has served it. Two reasons, both about Play:

- An app whose screens list third-party stream links is the pattern that gets a media app removed,
  hand-off or not. The host cannot tell an infringing source from a licit one, so "no promotion of
  infringing companions" protects nothing here on its own. What protects the host is what it does
  and does not do with a source, which is the rest of this section.
- The licit cases are mostly already covered. The user's own server is LIBRARY. Ad-supported and
  subscription services are TMDB watch providers with deep links. STREAM has to earn its place with
  a companion that is neither, and until one is named the contract is a draft the shape can be
  argued over, not a feature.

### What crosses, and what does not

- **Identity is the platform's**: media type + TMDB id, with an `EpisodeRef` beside it for a
  series. `Resolve` resolves one playable thing, so a series needs the episode.
- **A source is a `binge.companion.v1.PlaybackSource`**, the shared message, plus structured
  quality, a label and optional subtitles. Structured quality (resolution class, HDR, codec,
  size) rather than labels, so the host can sort and a TV can pick without a screen.
- **Resolution streams.** `Resolve` is server-streaming: one source per message, as found, and the
  stream ends when the integration is done. Partial results are normal. The host's deadline bounds
  it, cancellation abandons it, and an integration stops work when cancelled. The host reads at
  most fifty sources and cancels.
- **No rank crosses.** Nothing in a message ranks a source against another integration's. The host
  orders by quality and then by arrival, and that is all the ordering there is.
- **Every source expires.** `expires_at` is required and the host drops a source without one.
  Players keep history, so a URL a player was given is a URL that is stored; the expiry bounds that.

### Protecting the host

The host is a courier for a source and never a consumer of it. In order of how much each buys:

1. **Hand-off only, no in-app player, no web view.** A source goes to a player the user chose,
   through Android's standard video intents, or it goes nowhere. This is the decision the whole
   stance rests on, and it is not revisited casually: an app cannot be un-removed.
2. **The host validates and drops; it never repairs.** Only https URLs. Only `Authorization`,
   `Cookie`, `Referer` and `User-Agent` headers, each at most 4 KiB. A source whose headers the
   chosen player cannot carry is hidden for that player, not sent without them. Labels are
   truncated, never parsed. A source failing any rule is dropped silently; the integration learns
   nothing from the host about why, which keeps the rules from being probed.
3. **The host fetches nothing.** No thumbnails from a source host, no HEAD requests, no metadata
   scraping, no following redirects. What the host knows about a source is what the message says.
4. **Nothing persists.** Sources live in the Play sheet that showed them and are gone when it
   closes, and never past `expires_at`. The host keeps no history of sources, no cache across
   title pages, and nothing on disk.
5. **Attribution, not ranking.** Every source row names the integration that produced it, with
   the name from its manifest and its `provider_name`. Sources are grouped by integration. The host
   never badges one as best, never merges two integrations' lists into one ranking, and never
   autoplays. Attributing a source is how the host says "this came from an app you installed",
   which is not the same as listing or recommending a provider.
6. **Consent says what the integration is.** A STREAM companion's consent text is its own: this
   app will give Binge links to video sources, and Binge does not check where they come from.
   Consent is per package with the certificate pinned, as everywhere.
7. **A remote kill switch, and a remote revocation list.** The host renders STREAM UI only while
   a feature flag is on, so a policy problem is answered in minutes without a release. Separately,
   the host carries a remote list of companion signing-certificate digests it refuses to bind to,
   for any contract; a companion that turns out to be what the stance forbids is cut off on every
   device the same day, and the user is told why on its row. Both are host configuration, not
   contract.
8. **Resolve on demand only.** The host resolves when the user opens the sources affordance, never
   on a title page's appearance, never in a background sweep, never to build a row. `Probe` exists
   so a title page can ask "is there anything here?" cheaply; an integration that would have to
   resolve to answer it should not declare `CAPABILITY_PROBE`.

The companion side is unchanged from every other contract: `HostPolicy` on the Service, and no
privileged path for any host.

### Capabilities

`PROBE`, `SUBTITLES`, `REPORT_SOURCE`. The mandatory core is `Handshake` and `Resolve`. A STREAM
companion with no backend session answers `Handshake` with `UNAUTHENTICATED`, as LIBRARY does, and
the host handshakes again when the user returns from the companion app. As everywhere: feature
detection never uses version numbers.

### Discovery

The Service action is `com.binge.companion.STREAM`, with `com.binge.companion.majors.stream` for a
Service that also names another action. Several STREAM companions may be consented at once; the
host resolves against each under its own deadline and shows what arrives, grouped, with none
preferred.

### Where STREAM stops

- It never answers for the user's own server; that is LIBRARY, even when the same backend could.
- It hands out playable sources and nothing else: no browsing, no search, no catalogue. A
  companion with a catalogue of its own is a different product, and the host does not render it.
- It does not record what was watched. A STREAM source has no viewer session to write back to.

## Versioning

Capabilities answer "what can you do?". Versions answer "can we parse each other?".

- The proto package version (`binge.companion.request.v1`) is the contract **major**. Inside a
  package, every change must be additive: new fields, new enum values, new rpcs. CI fails any
  other change with `buf breaking`. Additive changes need no negotiation. Protobuf field numbers
  and unknown-field preservation keep old and new peers compatible in both directions.
- A breaking change becomes a new package (`v2`) with a new service. A companion app serves `v1`
  and `v2` side by side from the same exported Service. The manifest `<meta-data>` lists the
  majors a companion app serves. The host picks the highest common major before it binds. When
  there is no common major, the host shows "update Binge" or "update the companion app".
- A major bump is an escape hatch, not a tool. The append-only rule is the compatibility story.
