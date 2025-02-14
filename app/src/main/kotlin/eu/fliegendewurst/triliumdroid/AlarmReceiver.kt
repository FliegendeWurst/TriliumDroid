package eu.fliegendewurst.triliumdroid

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.bundleOf
import eu.fliegendewurst.triliumdroid.activity.main.MainActivity


class AlarmReceiver : BroadcastReceiver() {
	companion object {
		const val TAG: String = "AlarmReceiver"
		const val CHANNEL_ID: String = ""
	}

	override fun onReceive(context: Context?, intent: Intent?) {
		Log.i(TAG, "received alarm")
		val extras = intent?.extras ?: return
		val title = extras.getString("message") ?: return
		val note = extras.getString("note") ?: return
		Log.i(TAG, "data: $title $note")
		val noteIntent = Intent(context, MainActivity::class.java)
		noteIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
		noteIntent.putExtras(bundleOf(Pair("note", note)))
		val pendingIntent =
			PendingIntent.getActivity(context, 0, noteIntent, PendingIntent.FLAG_IMMUTABLE)

		val builder = NotificationCompat.Builder(context ?: return, CHANNEL_ID)
			.setSmallIcon(R.drawable.icon_color)
			.setContentTitle(title)
			.setContentIntent(pendingIntent)
			.setAutoCancel(true)
			.setPriority(NotificationCompat.PRIORITY_DEFAULT)
		with(NotificationManagerCompat.from(context)) {
			// notificationId is a unique int for each notification that you must define
			try {
				notify((0..Int.MAX_VALUE).random(), builder.build())
			} catch (e: SecurityException) {
				Log.e(TAG, "failed to show notification", e)
			}
		}
	}
}