package eu.fliegendewurst.triliumdroid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import eu.fliegendewurst.triliumdroid.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {
	private lateinit var binding: ActivityAboutBinding

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		binding = ActivityAboutBinding.inflate(layoutInflater)
		setContentView(binding.root)

		binding.buttonGithub.setOnClickListener {
			startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/zadam/trilium")))
		}
		binding.buttonGithub2.setOnClickListener {
			startActivity(
				Intent(
					Intent.ACTION_VIEW,
					Uri.parse("https://github.com/FliegendeWurst/TriliumDroid")
				)
			)
		}
		binding.buttonGithub3.setOnClickListener {
			startActivity(
				Intent(
					Intent.ACTION_VIEW,
					Uri.parse("https://github.com/TriliumNext/Notes")
				)
			)
		}
	}
}