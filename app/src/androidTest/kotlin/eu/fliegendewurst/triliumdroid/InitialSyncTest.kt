package eu.fliegendewurst.triliumdroid

import android.graphics.Bitmap
import androidx.test.core.graphics.writeToTestStorage
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.captureToBitmap
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.action.ViewActions.swipeDown
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import eu.fliegendewurst.triliumdroid.activity.main.MainActivity
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
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

	@get:Rule
	var nameRule = TestName()

	@get:Rule
	val activityScenarioRule = activityScenarioRule<MainActivity>()

	@Test
	@Throws(IOException::class)
	fun test_010_initialSync() {
		var index = 1
		onView(withId(R.id.button_setup_sync))
			.perform(click())
		onView(withId(R.id.server))
			.perform(typeText("http://10.0.2.2:8080"))
		onView(withId(R.id.password))
			.perform(typeText("1234"))
		Espresso.closeSoftKeyboard()
		Thread.sleep(2000)
		Espresso.pressBack()
		Thread.sleep(60000) // wait for Sync to finish

		// create lots of date notes in 2021-12
		runBlocking {
			for (day in 1..31) {
				ConnectionUtil.fetch(
					"/api/special-notes/days/2021-12-${
						day.toString().padStart(2, '0')
					}", null, false, {}, {})
			}
		}
		// (these will only be synced after the DB nuke in 020)

		// Trilium 0.91+: Demo Document has different default expanded state
		onView(withText("Trilium Demo"))
			.perform(longClick())
		Thread.sleep(2000)
		// End Trilium 0.91+
		onView(ViewMatchers.isRoot())
			.perform(captureToBitmap { bitmap: Bitmap -> bitmap.writeToTestStorage("${javaClass.simpleName}_${nameRule.methodName}_${index++}") })
		onView(withText("Trilium Demo"))
			.perform(click())
		Thread.sleep(2000) // wait for WebView to load
		onView(ViewMatchers.isRoot())
			.perform(captureToBitmap { bitmap: Bitmap -> bitmap.writeToTestStorage("${javaClass.simpleName}_${nameRule.methodName}_${index++}") })
	}

	@Test
	@Throws(IOException::class)
	fun test_015_renameDialog() {
		var index = 1
		// click to open rename dialog
		onView(withId(R.id.toolbar_title))
			.perform(click())
		// wait for WebView to load
		Thread.sleep(2000)
		onView(ViewMatchers.isRoot())
			.perform(captureToBitmap { bitmap: Bitmap -> bitmap.writeToTestStorage("${javaClass.simpleName}_${nameRule.methodName}_${index++}") })
		onView(withId(R.id.note_title))
			.perform(typeText(" Title Edited!"))
		onView(withId(R.id.button_rename_note))
			.perform(click())
		onView(ViewMatchers.isRoot())
			.perform(captureToBitmap { bitmap: Bitmap -> bitmap.writeToTestStorage("${javaClass.simpleName}_${nameRule.methodName}_${index++}") })
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
		var index = 1
		// click to open settings
		openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
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
		// wait to load
		Thread.sleep(2000)
		onView(ViewMatchers.isRoot())
			.perform(captureToBitmap { bitmap: Bitmap -> bitmap.writeToTestStorage("${javaClass.simpleName}_${nameRule.methodName}_${index++}") })
	}

	@Test
	@Throws(IOException::class)
	fun test_021_nukedDatabaseRestored() {
		var index = 1
		Thread.sleep(50000) // wait for sync
		onView(ViewMatchers.isRoot())
			.perform(captureToBitmap { bitmap: Bitmap -> bitmap.writeToTestStorage("${javaClass.simpleName}_${nameRule.methodName}_${index++}") })
	}

	@Test
	fun test_030_noteNavigation() {
		var index = 1
		Thread.sleep(2000) // wait until ready
		onView(withId(R.id.fab))
			.perform(click())
		Thread.sleep(2000) // wait until ready
		onView(ViewMatchers.isRoot())
			.perform(captureToBitmap { bitmap: Bitmap -> bitmap.writeToTestStorage("${javaClass.simpleName}_${nameRule.methodName}_${index++}") })
		for (text in arrayOf("Journal", "2021", "12 - December")) {
			println("navigating to $text")
			onView(
				allOf(
					withText(text),
					hasSibling(withId(R.id.navigation_button_icon))
				)
			).perform(click())
			Thread.sleep(500)
		}
		onView(ViewMatchers.isRoot())
			.perform(captureToBitmap { bitmap: Bitmap -> bitmap.writeToTestStorage("${javaClass.simpleName}_${nameRule.methodName}_${index++}") })
		onView(withId(R.id.navigation_list))
			.perform(swipeDown())
		for (i in 0..1) {
			onView(
				allOf(
					withText("24 - Sunday - Christmas Eve!"),
					hasSibling(withId(R.id.navigation_button_icon))
				)
			).perform(click())
			Thread.sleep(500)
		}
		onView(ViewMatchers.isRoot())
			.perform(captureToBitmap { bitmap: Bitmap -> bitmap.writeToTestStorage("${javaClass.simpleName}_${nameRule.methodName}_${index++}") })
	}
}
