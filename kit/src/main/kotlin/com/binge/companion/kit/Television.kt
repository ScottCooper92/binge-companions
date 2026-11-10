package com.binge.companion.kit

import android.content.res.Configuration

/** Whether this configuration is a television's: the UI mode type is `UI_MODE_TYPE_TELEVISION`. */
fun Configuration.isTelevision(): Boolean = uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
