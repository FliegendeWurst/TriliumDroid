package eu.fliegendewurst.triliumdroid.data

import eu.fliegendewurst.triliumdroid.data.Note

data class Relation(val source: Note, val target: Note?, val name: String)
