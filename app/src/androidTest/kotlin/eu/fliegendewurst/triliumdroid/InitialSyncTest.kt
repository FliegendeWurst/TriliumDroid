package eu.fliegendewurst.triliumdroid

import android.graphics.Bitmap
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.test.core.graphics.writeToTestStorage
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.CoordinatesProvider
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.ViewActions.captureToBitmap
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.action.ViewActions.swipeDown
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import eu.fliegendewurst.triliumdroid.activity.main.MainActivity
import eu.fliegendewurst.triliumdroid.data.CanvasNoteViewport
import eu.fliegendewurst.triliumdroid.database.Cache.Versions.DATABASE_VERSION_0_92_6
import eu.fliegendewurst.triliumdroid.database.Cache.Versions.SYNC_VERSION_0_90_12
import eu.fliegendewurst.triliumdroid.database.Cache.Versions.SYNC_VERSION_0_91_6
import eu.fliegendewurst.triliumdroid.database.Notes
import eu.fliegendewurst.triliumdroid.sync.ConnectionUtil
import eu.fliegendewurst.triliumdroid.util.Preferences
import kotlinx.coroutines.runBlocking
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.TypeSafeMatcher
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.IOException


@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class InitialSyncTest {
	companion object {
		private const val LOCAL_TEST = true
		private val SYNC_WAIT_MS: Long = if (LOCAL_TEST) {
			10000
		} else {
			50000
		}
		private val SYNC_SERVER = if (LOCAL_TEST) {
			"http://127.0.0.1:8080"
		} else {
			"http://10.0.2.2:8080"
		}
	}

	@get:Rule
	var nameRule = TestName()

	@get:Rule
	val activityScenarioRule = activityScenarioRule<MainActivity>()

	@Test
	@Throws(IOException::class)
	fun test_010_initialSync() {
		Thread.sleep(10000) // wait for network
		onView(withId(R.id.button_setup_sync))
			.perform(click())
		// see ./app/test/setup-sync-server.sh
		onView(withId(R.id.server))
			.perform(typeText(SYNC_SERVER))
		onView(withId(R.id.password))
			.perform(typeText("1234"))
		Espresso.closeSoftKeyboard()
		Thread.sleep(2000)
		onView(withText(android.R.string.ok))
			.perform(click())
		Thread.sleep(SYNC_WAIT_MS * 7 / 5) // wait for Sync to finish

		// create lots of date notes in 2021-12
		for (day in 1..31) {
			ConnectionUtil.fetch(
				"/api/special-notes/days/2021-12-${
					day.toString().padStart(2, '0')
				}", null, false, {}, {})
		}
		// (these will only be synced after the DB nuke in 020)

		// Trilium 0.91+: Demo Document has different default expanded state
		if (Preferences.databaseVersion()!! >= DATABASE_VERSION_0_92_6) {
			for (name in listOf("Inbox", "Formatting examples")) {
				onView(withText(name))
					.perform(longClick())
				Thread.sleep(2000)
			}
			onView(withIndex(withText("Journal"), 0)).perform(longClick())
			Thread.sleep(2000)
		} else if (Preferences.syncVersion()!! >= SYNC_VERSION_0_91_6) {
			onView(withText("Trilium Demo"))
				.perform(longClick())
			Thread.sleep(2000)
		} else if (Preferences.syncVersion()!! == SYNC_VERSION_0_90_12) {
			onView(withText("Journal"))
				.perform(longClick())
			Thread.sleep(2000)
			onView(withText("11 - November"))
				.perform(longClick())
			Thread.sleep(2000)
			onView(withText("12 - December"))
				.perform(longClick())
			Thread.sleep(2000)
		}
		// End Trilium 0.91+
		saveScreenshot()
		onView(withText("Trilium Demo"))
			.perform(click())
		Thread.sleep(2000) // wait for WebView to load
		saveScreenshot()
	}

	@Test
	fun test_011_canvas() {
		// first override the viewport for a nicer screenshot!
		runBlocking {
			val id = Notes.getNotesByType("canvas")[0]
			Preferences.setCanvasViewportOverride(id, CanvasNoteViewport(12.7F, 307.4F, 0.6F))
		}
		Thread.sleep(2000) // wait until ready

		openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
		onView(withText(R.string.jump_to_dialog))
			.perform(click())
		onView(withId(R.id.jump_input))
			.perform(typeText("canvas"))
		Thread.sleep(2000) // wait for DB query
		onView(allOf(withIndex(withText("Canvas"), 0)))
			.perform(click())
		Thread.sleep(15000) // wait for note to load
		saveScreenshot()
		Espresso.pressBack()
	}

	@Test
	fun test_011_help() {
		Thread.sleep(2000) // wait until ready

		openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
		onView(withText(R.string.action_help))
			.perform(click())
		Thread.sleep(2000) // wait for note to load
		onView(withText("Installation & Setup"))
			.perform(click())
		onView(withText("Backup"))
			.perform(click())
		Thread.sleep(2000) // wait for note to load
		onView(withId(R.id.drawer_layout))
			.perform(DrawerActions.close(Gravity.START))
		saveScreenshot()
		Espresso.pressBack()
	}

	@Test
	fun test_011_jumpToNote() {
		Thread.sleep(2000) // wait until ready
		openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
		onView(withText(R.string.jump_to_dialog))
			.perform(click())
		saveScreenshot()
		onView(withId(R.id.jump_input))
			.perform(typeText("pho"))
		Thread.sleep(2000) // wait for DB query
		saveScreenshot()
		onView(withText("Phone call about work project"))
			.perform(click())
		Thread.sleep(3000) // wait for note to load
		onView(withContentDescription(R.string.action_edit))
			.perform(click())
		Thread.sleep(15000) // wait for editor to load
		saveScreenshot()
		/*
		TODO: figure out automated typing ...
		onWebView(withId(R.id.webview_editable))
			.withElement(findElement(Locator.TAG_NAME, "p"))
			.perform(webClick()) // clickAt(286F, 175F)
		onView(withId(R.id.webview_editable)).check(object : ViewAssertion {
			override fun check(
				view: View?,
				noViewFoundException: NoMatchingViewException?
			) {
				assert(view!!.rootWindowInsets.isVisible(WindowInsets.Type.ime()))
			}
		})
		onView(withId(R.id.webview_editable))
			.perform(typeText("Let's hope I don't get fired :) "))
		Espresso.closeSoftKeyboard()
		saveScreenshot()
		 */
		onView(withContentDescription(R.string.action_edit))
			.perform(click())
		Thread.sleep(500)
//		saveScreenshot()
		Espresso.pressBack()
	}

	@Test
	fun test_012_globalNoteMap() {
		Thread.sleep(2000) // wait until ready
		onView(withId(R.id.drawer_layout))
			.perform(DrawerActions.open(Gravity.START))
		onView(withText(R.string.sidebar_tab_2))
			.perform(click())
		onView(withText(R.string.action_note_map))
			.perform(click())
		// The output is unpredictable, but it should not crash.
		Thread.sleep(5000)
	}

	@Test
	@Throws(IOException::class)
	fun test_015_renameDialog() {
		// wait for note to load
		Thread.sleep(5000)
		// click to open rename dialog
		onView(withId(R.id.toolbar_title))
			.perform(click())
		// wait for WebView to load
		Thread.sleep(2000)
		saveScreenshot()
		onView(withId(R.id.note_title))
			.perform(typeText(" Title Edited!"))
		onView(withId(R.id.button_rename_note))
			.perform(click())
		saveScreenshot()
		// click to sync
		openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
		onView(withText(R.string.action_sync))
			.perform(click())
		// wait for sync to finish
		Thread.sleep(5000)
	}

	@Test
	@Throws(IOException::class)
	fun test_020_nukeDatabase() {
		Thread.sleep(2000) // wait until ready
		// click to open settings
		openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
		Thread.sleep(500)
		onView(withText(R.string.action_settings))
			.perform(click())
		// wait to load
		Thread.sleep(2000)
		onView(withId(R.id.button_nuke_database))
			.perform(click())
		// confirm delete
		onView(withText(android.R.string.ok))
			.perform(click())
		Espresso.pressBack()
		// wait to sync
		Thread.sleep(SYNC_WAIT_MS)
		saveScreenshot()
	}

	@Test
	fun test_030_noteNavigation() {
		Thread.sleep(2000) // wait until ready
		onView(withId(R.id.fab))
			.perform(click())
		Thread.sleep(2000) // wait until ready
		saveScreenshot()
		for (text in arrayOf("root", "Journal", "2021", "12 - December")) {
			onView(
				allOf(
					withText(text),
					hasSibling(withId(R.id.navigation_button_icon))
				)
			).perform(click())
			Thread.sleep(500)
		}
		saveScreenshot()
		onView(withId(R.id.navigation_list))
			.perform(swipeDown())
		Thread.sleep(500)
		saveScreenshot()
	}

	@Test
	fun test_035_noteLabels() {
		// wait for note to load
		Thread.sleep(5000)
		onView(withId(R.id.drawer_layout))
			.perform(DrawerActions.open(Gravity.END))
		onView(withId(R.id.button_labels_modify))
			.perform(click())
		// wait for dialog to load
		Thread.sleep(500)
		saveScreenshot()
		onView(withId(R.id.button_add_label))
			.perform(click())
		saveScreenshot()
		onView(withId(R.id.note_title))
			.perform(typeText("santaClaus"))
		Espresso.closeSoftKeyboard()
		onView(withText(android.R.string.ok))
			.perform(click())
		onView(
			allOf(
				withText(""),
				withId(R.id.edit_label_content)
			)
		)
			.perform(typeText("comingToday"))
		Espresso.closeSoftKeyboard()
		saveScreenshot()
		onView(withText(android.R.string.ok))
			.perform(click())
		onView(withId(R.id.drawer_layout))
			.perform(DrawerActions.close(Gravity.END))
		saveScreenshot()

		// test deleting the note label
		onView(withId(R.id.drawer_layout))
			.perform(DrawerActions.open(Gravity.END))
		onView(withId(R.id.button_labels_modify))
			.perform(click())
		// wait for dialog to load
		Thread.sleep(500)
		onView(
			allOf(
				withId(R.id.button_delete_label),
				hasSibling(withText("santaClaus"))
			)
		)
			.perform(click())
		onView(
			allOf(
				withId(R.id.button_delete_label),
				hasSibling(withText("label:santaClaus"))
			)
		)
			.perform(click())
		onView(withText(android.R.string.ok))
			.perform(click())
	}

	@Test
	fun test_038_noteIcon() {
		// wait for note to load
		Thread.sleep(5000)

		// click on note icon
		onView(withId(R.id.toolbar_icon))
			.perform(click())
		// wait for dialog to load
		Thread.sleep(5000)
		saveScreenshot() // shows: dialog

		// select note icon
		val triliumDemoIcon = if (Preferences.databaseVersion()!! >= DATABASE_VERSION_0_92_6) {
			"\uE9f0"
		} else {
			"\uE946"
		}
		onView(withText(triliumDemoIcon))
			.perform(click())
		Thread.sleep(100)
		saveScreenshot() // shows: changed icon
	}

	@Test
	fun test_040_deleteJournal2021() {
		// wait for note to load
		Thread.sleep(5000)
		onView(withId(R.id.drawer_layout))
			.perform(DrawerActions.open(Gravity.START))
		onView(withText("2021"))
			.perform(click())
		// wait for note to load
		Thread.sleep(5000)
		// empty note, close sidebar
		onView(withId(R.id.drawer_layout))
			.perform(DrawerActions.close(Gravity.START))
		Thread.sleep(500)
		// click delete in overflow menu
		openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
		onView(withText(R.string.action_delete))
			.perform(click())
		saveScreenshot()
		onView(withText(android.R.string.ok))
			.perform(click())
		// wait for delete to finish
		Thread.sleep(5000)
		onView(withId(R.id.drawer_layout))
			.perform(DrawerActions.open(Gravity.START))
		saveScreenshot()
	}

	@Test
	fun test_090_darkMode() {
		Thread.sleep(2000) // wait until ready
		onView(withId(R.id.drawer_layout))
			.perform(DrawerActions.close(Gravity.START))
		// click to open settings
		openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
		Thread.sleep(500)
		onView(withText(R.string.action_settings))
			.perform(click())
		// wait to load
		Thread.sleep(2000)
		onView(withId(R.id.button_change_day_night))
			.perform(click())
		// confirm delete
		onView(withText(R.string.theme_night))
			.perform(click())
		Thread.sleep(2000) // wait to apply
		Espresso.pressBack()
		Thread.sleep(5000)
		saveScreenshot()
	}

	private val screenshotCounts = mutableMapOf<String, Int>()

	private fun saveScreenshot() {
		val id = "${javaClass.simpleName}_${nameRule.methodName}"
		if (screenshotCounts[id] == null) {
			screenshotCounts[id] = 1
		}
		onView(isRoot())
			.perform(captureToBitmap { bitmap: Bitmap -> bitmap.writeToTestStorage("${id}_${screenshotCounts[id]}") })
		screenshotCounts[id] = screenshotCounts[id]!! + 1
	}
}

// https://stackoverflow.com/a/39756832/5837178
private fun withIndex(matcher: Matcher<View>, index: Int): Matcher<View> {
	return object : TypeSafeMatcher<View>() {
		var currentIndex: Int = 0

		override fun describeTo(description: Description) {
			description.appendText("with index: ")
			description.appendValue(index)
			matcher.describeTo(description)
		}

		override fun matchesSafely(view: View): Boolean {
			return matcher.matches(view) && currentIndex++ == index
		}
	}
}

private fun clickAt(x: Float, y: Float) =
	GeneralClickAction(Tap.SINGLE, object : CoordinatesProvider {
		override fun calculateCoordinates(view: View): FloatArray? {
			val screenPos = IntArray(2)
			view.getLocationOnScreen(screenPos)

			val screenX = (screenPos[0] + x).toFloat()
			val screenY = (screenPos[1] + y).toFloat()

			return floatArrayOf(screenX, screenY)
		}
	}, Press.FINGER, InputDevice.SOURCE_TOUCHSCREEN, MotionEvent.BUTTON_PRIMARY)
