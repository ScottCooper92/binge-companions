## What and why

<!-- One short paragraph, then `Closes #N` (or `Part of #N`). Say what a change to a public signature means for consumers. -->

## Checked before pushing

<!-- Tick what you ran locally on the final head. If you couldn't run one (it runs out of memory, or needs the network), leave it unticked and say so. Don't tick a box you didn't run. -->
- [ ] `./gradlew ktlintCheck build` for the modules this touches
- [ ] `buf lint`, and `buf breaking --against main`; a contract change stays additive
- [ ] Status.md and the contract docs match any contract change
- [ ] Left to CI, and why: <!-- e.g. the full validateDebugScreenshotTest runs out of memory locally -->
