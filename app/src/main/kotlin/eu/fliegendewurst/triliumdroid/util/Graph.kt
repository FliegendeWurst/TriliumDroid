package eu.fliegendewurst.triliumdroid.util

class Graph<V, E> {
	val nodes: MutableList<V> = mutableListOf()
	val edges: MutableList<Edge<V, E>> = mutableListOf()

	val vertexPositions: MutableMap<V, Position> = mutableMapOf()

	fun addNodeAtPosition(v: V, pos: Position) {
		nodes.add(v)
		vertexPositions[v] = pos
	}

	fun addEdge(source: V, target: V, weight: E) {
		edges.add(Edge(source, target, weight))
	}
}