package eu.fliegendewurst.triliumdroid

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.graphics.writeToTestStorage
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.captureToBitmap
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.pressBack
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.matcher.ViewMatchers
import eu.fliegendewurst.triliumdroid.activity.main.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.FixMethodOrder
import org.junit.rules.TestName
import org.junit.runners.MethodSorters

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
		onView(ViewMatchers.withId(R.id.button_setup_sync))
			.perform(click())
		onView(ViewMatchers.withId(R.id.server))
			.perform(typeText("http://10.0.2.2:8080"))
		onView(ViewMatchers.withId(R.id.password))
			.perform(typeText("1234"))
		Espresso.closeSoftKeyboard()
		Espresso.pressBack()
		Thread.sleep(30000) // wait for Sync to finish
		onView(ViewMatchers.isRoot())
			.perform(captureToBitmap { bitmap: Bitmap -> bitmap.writeToTestStorage("${javaClass.simpleName}_${nameRule.methodName}_${index++}") })
		onView(ViewMatchers.withText("Trilium Demo"))
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
		onView(ViewMatchers.withId(R.id.toolbar_title))
			.perform(click())
		// wait for WebView to load
		Thread.sleep(2000)
		onView(ViewMatchers.isRoot())
			.perform(captureToBitmap { bitmap: Bitmap -> bitmap.writeToTestStorage("${javaClass.simpleName}_${nameRule.methodName}_${index++}") })
		onView(ViewMatchers.withId(R.id.note_title))
			.perform(typeText(" Title Edited!"))
		onView(ViewMatchers.withId(R.id.button_rename_note))
			.perform(click())
		onView(ViewMatchers.isRoot())
			.perform(captureToBitmap { bitmap: Bitmap -> bitmap.writeToTestStorage("${javaClass.simpleName}_${nameRule.methodName}_${index++}") })
	}
}
