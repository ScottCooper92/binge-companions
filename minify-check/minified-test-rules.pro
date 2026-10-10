# Test infrastructure only, for the `minified` build type. Never for release.
#
# AGP leaves androidx.tracing out of the test APK because the app's classpath carries it (through
# androidx.startup), and expects the app under test to provide it. The app never calls it, so R8 strips it, and
# AndroidJUnitRunner.onCreate then dies with NoClassDefFoundError before any test is discovered.
#
# Keep here only what the instrumentation runner itself loads. Nothing from com.binge.companion.**, io.grpc.** or
# com.google.protobuf.** may ever appear in this file: the round trip exists to catch a missing consumer rule, and
# a rule here would mask it.
-keep class androidx.tracing.** { *; }
