package eu.fliegendewurst.triliumdroid.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import eu.fliegendewurst.triliumdroid.Cache
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.data.Relation
import eu.fliegendewurst.triliumdroid.databinding.FragmentNoteMapBinding
import eu.fliegendewurst.triliumdroid.util.Graph
import eu.fliegendewurst.triliumdroid.util.Position
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class NoteMapFragment : Fragment(R.layout.fragment_note_map) {
	var noteId: String? = null
		private set

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		val binding = FragmentNoteMapBinding.inflate(inflater, container, false)
		if (noteId != null) {
			binding.viewNoteMap.g = createGraph(Cache.getNote(noteId!!)!!)
		}
		return binding.root
	}

	fun loadLater(id: String) {
		noteId = id
	}

	private fun createGraph(note: Note): Graph<Note, Relation> {
		val g = Graph<Note, Relation>()

		g.addNodeAtPosition(note, Position(0F, 0F))

		val relations = note.getRelations()
		for ((i, relation) in relations.withIndex()) {
			if (relation.target == null) {
				continue
			}
			val x = 200F * sin(2F * PI * i.toFloat() / relations.size.toFloat()).toFloat()
			val y = 200F * cos(2F * PI * i.toFloat() / relations.size.toFloat()).toFloat()
			g.addNodeAtPosition(relation.target, Position(x, y))
			g.addEdge(note, relation.target, relation)
		}

		return g
	}
}
