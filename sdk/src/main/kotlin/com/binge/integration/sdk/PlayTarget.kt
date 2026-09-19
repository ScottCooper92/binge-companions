package com.binge.integration.sdk

import com.binge.integration.contracts.library.v1.AndroidIntent
import com.binge.integration.contracts.library.v1.IntentExtra
import com.binge.integration.contracts.library.v1.PlayTarget
import com.binge.integration.contracts.library.v1.WebUrl

/**
 * Builds the [PlayTarget] a LIBRARY companion answers `GetPlayTarget` with, without hand-rolling
 * the generated protobuf message builders. Call exactly one of [PlayTargetScope.androidIntent] or
 * [PlayTargetScope.webUrl] inside [block] — `target` is a oneof, so whichever is called last wins.
 * This is the companion half of the hand-off shape; [PlayTarget.toIntent] is the host's.
 */
fun playTarget(block: PlayTargetScope.() -> Unit): PlayTarget {
    val builder = PlayTarget.newBuilder()
    PlayTargetScope(builder).block()
    return builder.build()
}

/** The receiver [playTarget] hands its [block], scoped to setting exactly one `target` arm. */
class PlayTargetScope internal constructor(
    private val builder: PlayTarget.Builder,
) {
    /**
     * The Intent the host should start in the server's own installed app — the common case.
     * [dataUri] and [extras] default to none. The host's [PlayTarget.toIntent] verifies
     * [packageName] resolves on the host's device before it starts anything built from this, so
     * answering with a target that used to be installed is not a failure on the companion's side.
     */
    fun androidIntent(
        packageName: String,
        action: String,
        dataUri: String = "",
        extras: Map<String, String> = emptyMap(),
    ) {
        val intentBuilder =
            AndroidIntent
                .newBuilder()
                .setPackageName(packageName)
                .setAction(action)
                .setDataUri(dataUri)
        extras.forEach { (key, value) -> intentBuilder.addExtras(IntentExtra.newBuilder().setKey(key).setValue(value)) }
        builder.androidIntent = intentBuilder.build()
    }

    /**
     * A URL to open when nothing is installed to hand off to. A worse experience, not a failure —
     * answer with this rather than an error, and the host does not have to tell the two apart.
     */
    fun webUrl(url: String) {
        builder.webUrl = WebUrl.newBuilder().setUrl(url).build()
    }
}

/**
 * What a [PlayTarget] resolves to, before it becomes an Android [android.content.Intent] — the
 * decision half of [PlayTarget.toIntent]. [resolveIntent] takes the [android.content.pm.PackageManager]
 * lookup as a plain function rather than looking it up itself, so the decision is testable on the
 * JVM without a [android.content.Context]. The same split [HostSecurityPolicy] and
 * [HandOffCallerPolicy] use for their own Android-touching halves.
 */
internal sealed interface ResolvedPlayTarget {
    /** Start [action] addressed to [packageName], with [dataUri] as the Intent data if present and [extras] as string extras. */
    data class App(
        val packageName: String,
        val action: String,
        val dataUri: String?,
        val extras: List<Pair<String, String>>,
    ) : ResolvedPlayTarget

    /** Start `ACTION_VIEW` on [url]. */
    data class Web(
        val url: String,
    ) : ResolvedPlayTarget
}

/**
 * Decides what [target] resolves to, or null when nothing can be started: an unset `target`, or an
 * [AndroidIntent] arm whose package [packageResolves] reports as absent. [packageResolves] is asked
 * before an [ResolvedPlayTarget.App] is produced, never after — a host must not build an Intent
 * addressed to a package it cannot find.
 */
internal fun resolveIntent(target: PlayTarget, packageResolves: (packageName: String) -> Boolean): ResolvedPlayTarget? =
    when (target.targetCase) {
        PlayTarget.TargetCase.ANDROID_INTENT -> {
            val intent = target.androidIntent
            if (packageResolves(intent.packageName)) {
                ResolvedPlayTarget.App(
                    packageName = intent.packageName,
                    action = intent.action,
                    dataUri = intent.dataUri.ifBlank { null },
                    extras = intent.extrasList.map { it.key to it.value },
                )
            } else {
                null
            }
        }

        PlayTarget.TargetCase.WEB_URL -> ResolvedPlayTarget.Web(target.webUrl.url)
        PlayTarget.TargetCase.TARGET_NOT_SET -> null
    }
