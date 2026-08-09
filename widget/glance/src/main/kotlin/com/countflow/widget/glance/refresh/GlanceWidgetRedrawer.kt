package com.countflow.widget.glance.refresh

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.countflow.widget.engine.refresh.WidgetRedrawer
import com.countflow.widget.glance.CountdownGlanceWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** [WidgetRedrawer] backed by Glance's own `updateAll`. */
@Singleton
class GlanceWidgetRedrawer @Inject constructor(
    @ApplicationContext private val context: Context,
) : WidgetRedrawer {

    override suspend fun redrawAll() {
        CountdownGlanceWidget().updateAll(context)
    }
}
