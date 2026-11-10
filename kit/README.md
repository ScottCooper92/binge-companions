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
