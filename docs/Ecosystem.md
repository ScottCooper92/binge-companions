# Ecosystem

How Binge, the contracts and the apps around them work together, from finding a title to watching
it. `Architecture.md` holds the decisions each contract makes on its own. This page is the whole
journey across them, and where one hands to the next. Where the two disagree, `Architecture.md`
wins.

## The loop

A person wants to find something, decide on it, get it if they do not have it, watch it, and have
that remembered. Each step has one owner.

| Step | Owner | How |
| --- | --- | --- |
| Find and decide | Binge | TMDB: discovery, search, title pages, lists and ratings. |
| Where can I watch it? | Binge, LIBRARY, REQUEST | The user's streaming services from TMDB. "In your library" from LIBRARY. "Requested" and its progress from REQUEST. |
| Get it | A REQUEST companion | The request server adds the title to the user's library. |
| Watch it | A LIBRARY companion, or the user's chosen player | See Playback below. A streaming service's title opens in that service's app. |
| Remember it | The media server, through LIBRARY | Watch state lives on the server. Lists and ratings live on the user's TMDB account. |

Every title page shows a next step: play it, open it in a service, request it, or say that none
of those is possible. A page with no next step is a bug in the loop.

## Who does what

- **Binge is the host.** It finds titles, shows where they are, and routes each action to the app
  that owns it. It bundles no provider, holds no provider credentials, and never plays media.
- **A companion serves one backend.** It signs in to that backend and serves the contracts that
  backend can answer. One companion may serve several contracts from one APK; a backend that is a
  request server and a media server at once can be one app.
- **A player is an ordinary Android app.** VLC, MX Player, mpv and the like are not companions and
  implement no contract. The user picks one, and Binge talks to it through Android's standard video
  intents.

## Contracts and their edges

| Contract | Answers | Does not |
| --- | --- | --- |
| REQUEST | Request a title, follow it, moderate it. Its `watch_url` is a link to the title's page in the media server's web client. | Hand out anything playable. |
| LIBRARY | Is it in the user's library? Play it. What have they watched, and how far? | Resolve sources outside the user's own server. |
| STREAM | Playable sources that are not the user's library. | Anything about the user's own server. |
| TRACKING | Sync watch state with a tracker that is not a media server. | Not yet sketched. |
| PLAYER | Deferred. See Players below. | – |

### Playable links come from LIBRARY, not REQUEST

A request server can already name a title's page on the media server, and REQUEST carries that
link. It must not hand a player a stream, for three reasons.

- **It acts with its own credentials, not the viewer's.** A request server usually talks to the
  media server with an administrator key. A stream it opened would run as that administrator, and
  the server would record the viewing against the wrong person.
- **It cannot record what was watched.** The media server is the source of truth for watch state.
  Writing progress back needs a companion signed in as the viewer, which is a LIBRARY companion.
- **A playable source is a new data model.** It has an expiry, credentials and, later, tracks. By
  the capability rule, that is a contract's business, and it belongs to the contract that holds
  the viewer's session.

## Playback

Playback is a hand-off. Binge never renders the video and never touches its bytes.

### The Play sheet

When a title is in the user's library, Binge offers Play, or Resume from the server's position.
Play opens a sheet with the ways the device can play it:

1. **The media server's own app.** LIBRARY's `GetPlayTarget` names it, or a web URL where the app
   is not installed. This is the default, and it is the best experience where it exists: the
   server's app handles transcoding, codecs, subtitles and its own account.
2. **The user's installed players.** Offered when the LIBRARY companion declares
   `PLAYBACK_SOURCE` (below). Binge lists the apps that handle video, and remembers the choice per
   device.

A device with no player installed still has the server's app or its web client, so Play always
does something.

### Playing in an installed player

1. Binge reads the title's watch state from LIBRARY.
2. Binge asks LIBRARY for a playback source for the chosen player.
3. Binge starts the player with the source, the title, and the resume position.
4. When the player returns, Binge reads what it reports and writes progress back through LIBRARY.

What a player takes and returns is set by that player, not by this platform. From their own
documentation:

| Player | Resume position in | HTTP headers in | Returned on exit |
| --- | --- | --- | --- |
| [VLC](https://wiki.videolan.org/Android_Player_Intents/) | `position` | Not documented | `extra_position`, `extra_duration`. The same result code covers finished and stopped. |
| [MX Player](https://sites.google.com/site/mxvpen/api) | `position` | `headers` | `position`, `duration`, `end_by` (`user` or `playback_completion`), when started with `return_result` |
| [mpv-android](http://mpv-android.github.io/mpv-android/intent.html) | `position` | Not documented | `position`, `duration`, only when stopped early |
| Any other video app | Nothing assumed | Nothing assumed | Nothing |

Verify each against the player's current documentation when building; do not copy it from here.

The limits are the price of using players that already exist:

- **One result, at the end.** No live progress. If the player's process dies, or the result never
  comes back, that session's progress is lost.
- **No decoding negotiation.** The host cannot tell the server what the player can decode, so the
  source may need a transcode the player did not ask for. This matters most on TV.
- **No track choice from Binge.** The player picks audio and subtitle tracks itself.

### `PLAYBACK_SOURCE`: an opt-in exception to "a hand-off, not a stream"

LIBRARY's play rule refuses to hand back a stream, so that Binge never becomes a player for
someone else's server. `PLAYBACK_SOURCE` is a narrow exception for the installed-player path. It
is a capability, so a companion that does not want it declares nothing and its titles play through
the server's own app only.

A sketch, not locked:

- `GetPlaybackSource` takes the media identity and the player's package, and returns a URL, any
  headers the request needs, and an expiry.
- The companion mints the URL for the **signed-in viewer**, never with an administrator's
  credentials.
- The URL is **short-lived and for one title.** Players keep history, so a URL a player was given
  is a URL that gets stored.
- Credentials go in headers where the player accepts them. Where it does not, as with VLC and mpv,
  they have to be in the URL, which is why the expiry matters.

### Watch state

- **The server is the source of truth.** Binge shows what LIBRARY last reported and keeps no watch
  state of its own.
- **Resume comes from the server.** Binge reads the position before starting a player.
- **Progress goes back to the server.** Binge writes the player's reported position, or marks the
  title played, through LIBRARY. That needs `WATCH_STATE_WRITE`.
- **What counts as finished is open.** A player's `end_by` settles it where it exists. Otherwise the
  host needs a rule, such as a position near the end, and that rule is not decided yet.

## Consent

- **A companion is consented per package,** with its certificate pinned, as `Architecture.md` >
  Security describes.
- **Enabling a player is the consent to hand it playback sources.** Binge says so when the user
  enables one, and records the player's package and signing certificate as it does for a companion.
  A different app under the same package name gets nothing.
- **Writing watch state is still its own capability.** Binge asks for it once, when the user first
  enables a player, because that is the moment progress would start being written.

## Players

There is no PLAYER contract for now. The Play sheet and the intents above cover the case with the
players people already have.

What would bring PLAYER back is a limit above that users actually hit:

- live progress that survives the player being killed;
- telling the server what the player can decode, so it can direct-play instead of transcode;
- track selection from the host.

A PLAYER contract would add those for players that choose to implement it. A reference player is
not planned.

## First-party apps

| App | Serves | On TV |
| --- | --- | --- |
| Binge | The host | Leads. Every flow on this page works on phone, foldable and TV. |
| [binge-seerr](https://github.com/ScottCooper92/binge-seerr) | REQUEST, plus its own console for Seerr, Jellyseerr and Overseerr | Sign-in, "request with options", requests and light moderation. Administration stays on the phone and the web client (binge-seerr#37). |
| Jellyfin companion (planned) | LIBRARY, with `PLAYBACK_SOURCE` | Sign-in only. Browsing and playing the library stay with Jellyfin's own app. |

A companion's own UI is for signing in and for what only it can do. It is not a second Binge.
