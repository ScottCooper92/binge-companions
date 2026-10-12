#!/usr/bin/env bash
#
# Fails when binge-ci is pinned at more than one commit, or when this build's detekt version is not
# the one binge-ci's gates apply.
#
# binge-ci is pinned twice: by SHA in every `uses:` line under .github/workflows/, and as the
# binge-ci submodule that supplies the Gradle gates. Renovate bumps the first and not the second,
# so without this a bump moves the workflows and leaves the gates behind. The detekt version has to
# follow too: the gate applies dev.detekt at binge-ci's catalog version, and :detekt-rules compiles
# against this catalog's.
#
# Run from the repository root, with the submodule checked out.
set -euo pipefail

submodule=$(git rev-parse HEAD:binge-ci)
failed=0

while IFS= read -r line; do
  file=${line%%:*}
  ref=$(sed -E 's#.*ScottCooper92/binge-ci/[^@]*@([^[:space:]]+).*#\1#' <<<"$line")
  if [ "$ref" != "$submodule" ]; then
    echo "::error file=${file}::${file} pins binge-ci at ${ref}, but the binge-ci submodule is at ${submodule}."
    failed=1
  fi
done < <(grep -H -E '^[^#]*uses:[[:space:]]*ScottCooper92/binge-ci/' .github/workflows/*.yml)

catalog_detekt() {
  sed -n -E 's/^detekt[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$1"
}
theirs_file=binge-ci/gradle-plugins/gradle/libs.versions.toml
[ -f "$theirs_file" ] || { echo "::error::${theirs_file} is missing. Check out the submodules first."; exit 1; }
ours=$(catalog_detekt gradle/libs.versions.toml)
theirs=$(catalog_detekt "$theirs_file")
if [ -z "$ours" ] || [ "$ours" != "$theirs" ]; then
  echo "::error file=gradle/libs.versions.toml::detekt is '${ours}' here and '${theirs}' in binge-ci's gates. Move them together."
  failed=1
fi

if [ "$failed" -eq 0 ]; then
  echo "binge-ci is pinned at ${submodule} everywhere, and detekt is ${ours} on both sides."
fi
exit "$failed"
