package com.binge.companion.sdk

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

private const val COMPANION = "com.example.companion"
private const val OTHER = "com.example.other"
private const val ACTIVITY = "com.example.companion.PlayActivity"

/**
 * The PackageManager half of [HandOffPolicy.isStartable] (#280): it must resolve as `startActivity` does,
 * so an exported Activity with no `DEFAULT` category is not startable. The decision on what resolves is
 * [HandOffTargetPolicy], tested apart.
 */
@RunWith(RobolectricTestRunner::class)
class HandOffStartableTest {
    private val context: Application = RuntimeEnvironment.getApplication()

    private fun register(
        packageName: String = COMPANION,
        exported: Boolean = true,
        defaultCategory: Boolean = true,
    ) {
        val activity =
            ActivityInfo().apply {
                this.packageName = packageName
                name = ACTIVITY
                this.exported = exported
                applicationInfo = ApplicationInfo().apply { this.packageName = packageName }
            }
        val shadow = shadowOf(context.packageManager)
        shadow.addOrUpdateActivity(activity)
        shadow.addIntentFilterForActivity(
            ComponentName(packageName, ACTIVITY),
            IntentFilter(CompanionManifest.ACTION_PLAY).apply { if (defaultCategory) addCategory(Intent.CATEGORY_DEFAULT) },
        )
    }

    private fun playIntent(packageName: String? = COMPANION): Intent = Intent(CompanionManifest.ACTION_PLAY).setPackage(packageName)

    private fun startable(intent: Intent = playIntent()) = HandOffPolicy.isStartable(context, intent, COMPANION)

    @Test
    fun `an exported Activity with the DEFAULT category is startable`() {
        register()

        assertTrue(startable())
    }

    @Test
    fun `an exported Activity without the DEFAULT category is not startable`() {
        register(defaultCategory = false)

        assertFalse(startable())
    }

    @Test
    fun `an Activity that is not exported is not startable`() {
        register(exported = false)

        assertFalse(startable())
    }

    @Test
    fun `nothing resolving is not startable`() {
        assertFalse(startable())
    }

    @Test
    fun `an Activity in another package is not startable`() {
        register(packageName = OTHER)

        assertFalse(startable(playIntent(packageName = OTHER)))
    }

    @Test
    fun `an Intent that names a component is not startable`() {
        register()

        assertFalse(startable(playIntent().setComponent(ComponentName(COMPANION, ACTIVITY))))
    }

    @Test
    fun `an Intent with no package is not startable`() {
        register()

        assertFalse(startable(playIntent(packageName = null)))
    }
}
