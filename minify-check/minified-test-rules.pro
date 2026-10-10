# Test infrastructure only, for the `minified` build type. Never for release.
#
# AGP leaves from the test APK every class on the app's classpath and expects the app under test to provide it. R8
# strips whatever the app never calls, so the instrumentation runner dies with NoClassDefFoundError before any test is
# discovered. This file keeps what the runner itself loads, and the one entry point the test calls.
#
# Nothing from the SDK's or the contracts' packages (com.binge.companion.sdk, com.binge.companion.contracts), io.grpc.**
# or com.google.protobuf.** may ever appear in this file: the round trip exists to catch a missing consumer rule, and a
# rule here would mask it. The probe's own RoundTripClient is the exception, because it is the code under test.

# The runner's own needs.
-keep class androidx.tracing.** { *; }
-keep class kotlin.Lazy { *; }
-keep class kotlin.LazyKt { *; }
-keep class kotlin.LazyKt__* { *; }
-keep class kotlin.jvm.internal.Intrinsics { *; }

# The test's one entry point into the shrunk app. What it calls inside the SDK and the contracts is left to R8.
-keep class com.binge.companion.minifycheck.RoundTripClient { *; }
-keep class com.binge.companion.minifycheck.RoundTripClient$* { *; }
