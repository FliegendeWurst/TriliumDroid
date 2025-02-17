package eu.fliegendewurst.triliumdroid.fragment

import android.os.Bundle
import android.util.Log
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
import kotlinx.coroutines.runBlocking
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class NoteMapFragment : Fragment(R.layout.fragment_note_map), NoteRelatedFragment {
	private var noteId: String? = null

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		val binding = FragmentNoteMapBinding.inflate(inflater, container, false)
		if (noteId != null) {
			if (noteId == "GLOBAL") {
				binding.viewNoteMap.g = createGraphGlobal()
			} else {
				binding.viewNoteMap.g = createGraph(Cache.getNote(noteId!!)!!)
			}
		}
		return binding.root
	}

	fun loadLater(id: String) {
		noteId = id
	}

	fun loadLaterGlobal() {
		noteId = "GLOBAL"
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

	private fun createGraphGlobal(): Graph<Note, Relation> {
		val g = Graph<Note, Relation>()

		val data = runBlocking { Cache.getAllNotesWithRelations() }

		// start at a random note and keep going!
		val processed = mutableSetOf<Note>()
		while (processed.size != data.size) {
			val startNote = data[data.indices.random()]
			val relations = startNote.getRelationsBypassCache()
			if (processed.contains(startNote)) {
				continue
			}
			if (relations.isEmpty()) {
				processed.add(startNote)
				continue
			}
			g.addNodeAtPosition(startNote, Position(0F, 0F))
			rec(g, processed, startNote)
		}

		return g
	}

	private fun rec(g: Graph<Note, Relation>, processed: MutableSet<Note>, note: Note) {
		if (processed.contains(note)) {
			return
		}
		processed.add(note)
		val relations = note.getRelationsBypassCache()
		Log.d("map", "at note ${note.id} with ${relations.size} relations")
		for ((i, relation) in relations.withIndex()) {
			Log.d("map", "at relation ${relation.name} = ${relation.target}")
			if (relation.target == null) {
				continue
			}
			if (!g.nodes.contains(relation.target)) {
				g.addNodeAtPosition(relation.target, Position(0F, 0F))
			}
			g.addEdge(note, relation.target, relation)
			rec(g, processed, relation.target)
		}
	}

	override fun getNoteId(): String? {
		return noteId
	}
}
