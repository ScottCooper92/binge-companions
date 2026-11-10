# binge-companion-kit

Optional building blocks that more than one companion app would otherwise copy from another.

**This module is outside the contract's versioning promise.** The append-only rule in `CLAUDE.md` covers the
`.proto` packages. It does not cover the kit. `buf breaking` does not check it, and a kit release may break a
companion's build. `contracts/` and `sdk/` do not depend on the kit, and the kit does not depend on them.

A piece moves here only when at least two companions already have it, or both need it. Pieces only one
companion uses stay in that companion.

## Contents

- `AddressRange`, `ipLiteralBytes` and `ByteArray.isIn`: a table of address ranges (loopback, link-local,
  RFC 1918, CGNAT, unique-local, Tailscale) and a parser that reads an IP literal without resolving it. Which
  ranges a check counts is policy and stays in each app.
- `SecretCipher`, `AesGcmSecretCipher` and `KeystoreSecretCipher`: AES-256-GCM for the secrets an app keeps on
  disk. `KeystoreSecretCipher(alias)` keeps the key in the Android Keystore under the app's own alias; the alias is part
  of the on-disk format. `AesGcmSecretCipher` takes any key, so tests use a software key. The blob is a 12-byte IV and
  the ciphertext, Base64-encoded, the same as the companions' copies write today.
- `Configuration.isTelevision()`: whether the UI mode type is a television's. Three companions had the same line.
