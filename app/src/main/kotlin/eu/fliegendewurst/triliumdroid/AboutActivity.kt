package eu.fliegendewurst.triliumdroid

import android.os.Bundle
import android.os.PersistableBundle
import androidx.appcompat.app.AppCompatActivity
import eu.fliegendewurst.triliumdroid.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {
	override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
		super.onCreate(savedInstanceState, persistentState)

		val binding = ActivityAboutBinding.inflate(layoutInflater)
		setContentView(binding.root)
	}
}