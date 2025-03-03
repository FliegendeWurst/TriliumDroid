package eu.fliegendewurst.triliumdroid.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.preference.PreferenceManager
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.activity.main.HistoryItem
import eu.fliegendewurst.triliumdroid.activity.main.MainActivity
import eu.fliegendewurst.triliumdroid.activity.main.NoteEditItem
import eu.fliegendewurst.triliumdroid.activity.main.NoteItem
import eu.fliegendewurst.triliumdroid.activity.main.StartItem
import eu.fliegendewurst.triliumdroid.database.Cache
import eu.fliegendewurst.triliumdroid.database.Notes
import eu.fliegendewurst.triliumdroid.util.Preferences
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

/**
 * Implementation of App Widget functionality.
 */
class NoteWidget : AppWidgetProvider() {
	companion object {
		const val TAG = "NoteWidget"
	}

	override fun onUpdate(
		context: Context,
		appWidgetManager: AppWidgetManager,
		appWidgetIds: IntArray
	) {
		// There may be multiple widgets active, so update all of them
		for (appWidgetId in appWidgetIds) {
			updateAppWidget(context, appWidgetManager, appWidgetId, null)
		}
	}

	override fun onAppWidgetOptionsChanged(
		context: Context,
		appWidgetManager: AppWidgetManager,
		appWidgetId: Int,
		newOptions: Bundle?
	) {
		updateAppWidget(context, appWidgetManager, appWidgetId, newOptions)
	}

	override fun onEnabled(context: Context) {
		Preferences.prefs =
			PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
		if (Cache.haveDatabase(context)) {
			runBlocking {
				Cache.initializeDatabase(context)
			}
		}
	}

	override fun onDisabled(context: Context) {
		// Enter relevant functionality for when the last widget is disabled
	}
}

fun parseWidgetAction(action: String?): HistoryItem? {
	if (action == null) {
		return null
	}
	val parts = action.split(';')
	if (parts.isEmpty()) {
		return null
	}
	return when (parts[0]) {
		"launch" -> StartItem()
		"open" -> {
			if (parts.size >= 2) {
				val note = runBlocking {
					Notes.getNoteWithContent(parts[1])
				}
				NoteItem(note!!, null)
			} else {
				null
			}
		}

		"edit" -> {
			if (parts.size >= 2) {
				val note = runBlocking {
					Notes.getNoteWithContent(parts[1])
				}
				NoteEditItem(note!!)
			} else {
				null
			}
		}

		else -> null
	}
}

internal fun updateAppWidget(
	context: Context,
	appWidgetManager: AppWidgetManager,
	appWidgetId: Int,
	newOptions: Bundle?
) {
	val views = RemoteViews(context.packageName, R.layout.widget_note)

	val options = newOptions ?: appWidgetManager.getAppWidgetOptions(appWidgetId)
	val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
	val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

	// TODO: landscape sizing
	var w = ((minWidth + 16).toFloat() / 73F).roundToInt()
	var h = ((minHeight + 16).toFloat() / 105F).roundToInt()
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
		val sizes = options.getParcelableArrayList<SizeF>(
			AppWidgetManager.OPTION_APPWIDGET_SIZES
		)
		if (sizes != null) {
			w = ((sizes[0].width + 16F) / 73F).roundToInt()
			h = ((sizes[0].height + 16F) / 105F).roundToInt()
		}
	}
	Log.d(NoteWidget.TAG, "id = $appWidgetId, size = $minWidth / $minHeight, cells = $w / $h")

	val intent = Intent(context, MainActivity::class.java)
	intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
	intent.putExtra("appWidgetId", appWidgetId)
	val pendingIntent: PendingIntent = PendingIntent.getActivity(
		context, System.currentTimeMillis().toInt(), intent,
		PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
	)

	views.setOnClickPendingIntent(R.id.widget_note_icon, pendingIntent)
	views.setOnClickPendingIntent(R.id.widget_note_title, pendingIntent)
	views.setOnClickPendingIntent(R.id.appwidget_text, pendingIntent)
	views.setOnClickPendingIntent(R.id.note_content, pendingIntent)

	val action = Preferences.widgetAction(appWidgetId)
	if (action != null && h == 1) {
		if (w == 1) {
			views.setViewVisibility(R.id.widget_note_icon, View.VISIBLE)
			views.setViewVisibility(R.id.widget_note_title, View.GONE)
		} else {
			views.setViewVisibility(R.id.widget_note_icon, View.GONE)
			val note = runBlocking { Notes.getNote(action.noteId()) }!!
			views.setTextViewText(R.id.widget_note_title, note.title())
			views.setViewVisibility(R.id.widget_note_title, View.VISIBLE)
		}
		views.setViewVisibility(R.id.appwidget_text, View.GONE)
		views.setViewVisibility(R.id.note_content, View.GONE)
	} else if (action != null) {
		val content = runBlocking { Notes.getNoteWithContent(action.noteId()) }!!
		views.setTextViewText(
			R.id.note_content,
			Html.fromHtml(
				content.content()!!.decodeToString(),
				Html.FROM_HTML_MODE_COMPACT
			)
		)
		views.setViewVisibility(R.id.widget_note_icon, View.GONE)
		views.setViewVisibility(R.id.widget_note_title, View.GONE)
		views.setViewVisibility(R.id.appwidget_text, View.GONE)
		views.setViewVisibility(R.id.note_content, View.VISIBLE)
	} else {
		views.setTextViewCompoundDrawables(R.id.appwidget_text, R.drawable.bx_cog, 0, 0, 0)
		views.setViewVisibility(R.id.widget_note_icon, View.GONE)
		views.setViewVisibility(R.id.widget_note_title, View.GONE)
		views.setViewVisibility(R.id.appwidget_text, View.VISIBLE)
		views.setViewVisibility(R.id.note_content, View.GONE)
	}

	// Instruct the widget manager to update the widget
	appWidgetManager.updateAppWidget(appWidgetId, views)
}
