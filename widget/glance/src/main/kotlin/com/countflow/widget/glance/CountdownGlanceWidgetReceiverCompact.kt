package com.countflow.widget.glance

import dagger.hilt.android.AndroidEntryPoint

/**
 * The "CountFlow Compact" widget picker entry — 2×1 default placement footprint
 * (`res/xml/countdown_widget_info_compact.xml`). See [BaseCountdownGlanceWidgetReceiver]'s own
 * class doc for why a distinct receiver subclass exists at all despite sharing every bit of real
 * rendering logic with [CountdownGlanceWidgetReceiver] and [CountdownGlanceWidgetReceiverWide].
 */
@AndroidEntryPoint
class CountdownGlanceWidgetReceiverCompact : BaseCountdownGlanceWidgetReceiver()
