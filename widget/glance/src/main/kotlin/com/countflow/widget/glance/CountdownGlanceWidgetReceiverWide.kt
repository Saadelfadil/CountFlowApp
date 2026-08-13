package com.countflow.widget.glance

import dagger.hilt.android.AndroidEntryPoint

/**
 * The "CountFlow Wide" widget picker entry — 4×2 default placement footprint
 * (`res/xml/countdown_widget_info_wide.xml`). See [BaseCountdownGlanceWidgetReceiver]'s own class
 * doc for why a distinct receiver subclass exists at all despite sharing every bit of real
 * rendering logic with [CountdownGlanceWidgetReceiver] and [CountdownGlanceWidgetReceiverCompact].
 */
@AndroidEntryPoint
class CountdownGlanceWidgetReceiverWide : BaseCountdownGlanceWidgetReceiver()
