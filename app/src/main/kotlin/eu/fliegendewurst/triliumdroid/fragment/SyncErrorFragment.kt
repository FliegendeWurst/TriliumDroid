package eu.fliegendewurst.triliumdroid.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.databinding.FragmentSyncErrorBinding
import java.net.ConnectException
import java.net.UnknownHostException


class SyncErrorFragment : Fragment(R.layout.fragment_sync_error) {
	private var binding: FragmentSyncErrorBinding? = null
	private var error: Throwable? = null

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		binding = FragmentSyncErrorBinding.inflate(inflater, container, false)
		showInternal()
		return binding!!.root
	}

	fun showError(error: Throwable) {
		this.error = error
		showInternal()
	}

	@SuppressLint("SetTextI18n")
	private fun showInternal() {
		if (binding != null && error != null) {
			val causes = mutableListOf<Throwable>()
			var cause = error!!.cause
			while (cause != null) {
				causes.add(cause)
				cause = cause.cause
				if (causes.size > 5) {
					// unrealistic, probably a loop
					break
				}
			}
			val toString =
				{ t: Throwable -> "${t.javaClass.simpleName}: ${t.localizedMessage ?: t.message}" }
			binding!!.labelSyncErrorCauses.text =
				toString(error!!) + causes.map { "\n ${toString(it)}" }.joinToString("")
//				binding!!.labelSyncErrorStacktrace.text = error!!.stackTraceToString()
			causes.add(error!!)
			val s = { it: Int -> resources.getString(it) }
			var text = s(R.string.error_generic)
			if (causes.any { it is ConnectException }) {
				text += " ${s(R.string.error_connect)}"
			}
			if (causes.any { it is UnknownHostException }) {
				text += " ${s(R.string.error_unknown_host)}"
			}
			binding!!.labelSyncErrorHint.text = text
		}
	}
}
