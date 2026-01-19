package eu.fliegendewurst.triliumdroid.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import eu.fliegendewurst.triliumdroid.BuildConfig
import eu.fliegendewurst.triliumdroid.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {
	private lateinit var binding: ActivityAboutBinding

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		binding = ActivityAboutBinding.inflate(layoutInflater)

		binding.textAppVersion.text = BuildConfig.VERSION_NAME

		setContentView(binding.root)

		binding.buttonGithub.setOnClickListener {
			startActivity(
				Intent(
					Intent.ACTION_VIEW,
					"https://github.com/TriliumNext/Trilium".toUri()
				)
			)
		}
		binding.buttonGithub2.setOnClickListener {
			startActivity(
				Intent(
					Intent.ACTION_VIEW,
					"https://github.com/FliegendeWurst/TriliumDroid".toUri()
				)
			)
		}
	}
}
