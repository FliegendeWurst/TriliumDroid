package eu.fliegendewurst.triliumdroid.util

import java.util.TreeSet

class Graph<V, E> {
	val nodes: TreeSet<V> = TreeSet()
	val edges: MutableList<Edge<V, E>> = mutableListOf()
	val incomingEdges: MutableMap<V, MutableList<Edge<V, E>>> = mutableMapOf()
	val outgoingEdges: MutableMap<V, MutableList<Edge<V, E>>> = mutableMapOf()

	val vertexPositions: MutableMap<V, Position> = mutableMapOf()

	fun addNodeAtPosition(v: V, pos: Position) {
		nodes.add(v)
		vertexPositions[v] = pos
	}

	fun addEdge(source: V, target: V, weight: E) {
		val e = Edge(source, target, weight)
		edges.add(e)
		if (incomingEdges[target] == null) {
			incomingEdges[target] = mutableListOf()
		}
		incomingEdges[target]!!.add(e)
		if (outgoingEdges[source] == null) {
			outgoingEdges[source] = mutableListOf()
		}
		outgoingEdges[source]!!.add(e)
	}
}