package eu.fliegendewurst.triliumdroid.view


import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Align
import android.graphics.Path
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.activity.main.MainActivity
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.data.NoteId
import eu.fliegendewurst.triliumdroid.data.Relation
import eu.fliegendewurst.triliumdroid.util.Graph
import eu.fliegendewurst.triliumdroid.util.Position
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class GraphView(context: Context, attributes: AttributeSet?) : View(context, attributes) {
	companion object {
		private const val TAG: String = "GraphView"
	}

	var g: Graph<Note, Relation> = Graph()

	private var backgroundColor: Int = 0

	private var paint: Paint = Paint()
	private var backgroundPaint: Paint = Paint()
	private var fontPaint: Paint = Paint()
	private var fontPaintEdgeLabel: Paint = Paint()

	private var edgePath: Path = Path()

	private var offsetX: Float = 0F
	private var offsetY: Float = 0F
	private var xScale: Float = 1F
	private var yScale: Float = 1F

	private var offsetXOld: Float = 0F
	private var offsetYOld: Float = 0F
	private var xScaleOld: Float = 1F
	private var yScaleOld: Float = 1F

	private val simulationDelay: Long = 250

	private var scaleDetector: ScaleGestureDetector

	init {
		g = Graph()

		backgroundColor = resources.getColor(R.color.background, null)

		paint.strokeWidth = 4f
		paint.style = Paint.Style.STROKE
		paint.strokeJoin = Paint.Join.ROUND
		paint.isAntiAlias = true
		paint.color = resources.getColor(R.color.foreground, null)

		backgroundPaint.style = Paint.Style.FILL
		backgroundPaint.isAntiAlias = true
		backgroundPaint.color = resources.getColor(R.color.primary, null)

		fontPaint.textAlign = Align.CENTER
		fontPaint.textSize = 24f

		fontPaintEdgeLabel.textAlign = Align.CENTER
		fontPaintEdgeLabel.textSize = 24f
		fontPaintEdgeLabel.color = paint.color

		setOnClickListener {
			val point = Position((x!! - xOffset()) / xScale, (y!! - yOffset()) / yScale)
			for (node in g.nodes) {
				val dist = g.vertexPositions[node]!!.distance(point)
				if (dist < 100 * 100) {
					(context as MainActivity).navigateTo(node)
					return@setOnClickListener
				}
			}
		}

		scaleDetector = ScaleGestureDetector(
			context,
			object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
				override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
					xScaleOld = xScale
					yScaleOld = yScale
					offsetXOld = offsetX - detector.focusX + width / 2
					offsetYOld = offsetY - detector.focusY + height / 2
					return super.onScaleBegin(detector)
				}

				override fun onScale(detector: ScaleGestureDetector): Boolean {
					detector.let {
						xScale = xScaleOld * it.scaleFactor
						yScale = yScaleOld * it.scaleFactor
						offsetX = offsetXOld * it.scaleFactor + it.focusX - width / 2
						offsetY = offsetYOld * it.scaleFactor + it.focusY - height / 2

						invalidate()
					}
					return super.onScale(detector)
				}
			})


		(context as MainActivity).handler.postDelayed(this::updatePositions, simulationDelay)
	}

	private var alpha = 1F
	private val alphaDecay = 0.01F
	private val alphaMin = 0.001F
	private val alphaTarget = 0F
	private val velocityDecay = 0.08F
	private val warmupTicks = 30

	private val linkDistance = 400F
	private val centerStrength = 0.2F
	private val chargeDistanceMax = 1000F
	private var chargeStrength = 0F

	private var counter: Int = 0
	private var velocities: MutableMap<Note, Position> = mutableMapOf()
	private var attached: Boolean = true

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		attached = false
	}

	private val count: MutableMap<NoteId, Int> = mutableMapOf()
	private val bias: MutableMap<Pair<Note, Note>, Float> = mutableMapOf()

	private fun updatePositions() {
		val start = System.currentTimeMillis()
		if (g.nodes.isEmpty()) {
			(context as MainActivity).handler.postDelayed(this::updatePositions, simulationDelay)
			return
		}
		if (counter == 0) {
			val nodeLinkRatio = g.nodes.size.toFloat() / g.edges.size.toFloat()
			val magnifiedRatio = nodeLinkRatio.pow(1.5F)
			val charge = -20F / magnifiedRatio
			val boundedCharge = min(-3F, charge)

			chargeStrength = boundedCharge

			for (node in g.nodes) {
				if (count[node.id] == null) {
					count[node.id] = 0
				}
				val rels = node.getRelationsBypassCache()
				count[node.id] = count[node.id]!! + rels.size
				for (rel in rels) {
					if (rel.target == null) {
						continue
					}
					if (count[rel.target.id] == null) {
						count[rel.target.id] = 1
					} else {
						count[rel.target.id] = count[rel.target.id]!! + 1
					}
				}
			}

			for (node in g.nodes) {
				for (rel in node.getRelationsBypassCache()) {
					if (rel.target == null) {
						continue
					}
					bias[Pair(node, rel.target)] =
						count[node.id]!!.toFloat() / (count[node.id]!! + count[rel.target.id]!!)
				}
			}
		}
		if (!attached || counter > 200) {
			return
		}
		alpha += (alphaTarget - alpha) * alphaDecay
		if (alpha < alphaMin) {
			return
		}
		counter++
		var sx = 0F
		var sy = 0F
		for (pos in g.vertexPositions.values) {
			sx += pos.x
			sy += pos.y
		}
		sx = (sx / g.vertexPositions.size) * centerStrength
		sy = (sy / g.vertexPositions.size) * centerStrength
		for (node in g.nodes) {
			var pos = g.vertexPositions[node]!!
			if (pos.x == 0F && pos.y == 0F) {
				pos = Position(100F * Random.nextFloat() - 50F, 100F * Random.nextFloat() - 50F)
			}
			g.vertexPositions[node] = pos.subtract(Position(sx, sy))
			if (velocities[node] == null) {
				velocities[node] = Position(0F, 0F)
			}
		}
		for (link in bias) {
			val source = link.key.first
			val target = link.key.second
			// TODO: figure out why these are sometimes null?
			val sourcePos = g.vertexPositions[source] ?: continue
			val targetPos = g.vertexPositions[target] ?: continue
			val sourceV = velocities[source]!!
			val targetV = velocities[target]!!
			var x = targetPos.x + targetV.x - sourcePos.x - sourceV.x
			var y = targetPos.y + targetV.y - sourcePos.y - sourceV.y
			var l = sqrt(x * x + y * y)
			val linkStrength = 1F / min(count[source.id]!!.toFloat(), count[target.id]!!.toFloat())
			l = (l - linkDistance) / l * alpha * linkStrength
			x *= l
			y *= l
			velocities[target] = targetV.subtract(Position(x * link.value, y * link.value))
			velocities[source] = targetV.add(Position(x * (1F - link.value), y * (1F - link.value)))
		}
		// crappy charge simulation
		for (nodeA in g.nodes) {
			for (nodeB in g.nodes) {
				if (nodeA.id.rawId() >= nodeB.id.rawId()) {
					continue
				}
				val posA = g.vertexPositions[nodeA]!!
				val posB = g.vertexPositions[nodeB]!!
				val dist = posA.distance(posB)
				val minDist = 400F
				if (dist < minDist * minDist) {
					val power = min(10F, minDist / sqrt(dist))
					val scale = 0.18F
					val delta = posB.subtract(posA)
					velocities[nodeA] = velocities[nodeA]!!.add(delta.scale(-scale * power))
					velocities[nodeB] = velocities[nodeB]!!.add(delta.scale(scale * power))
				}
			}
		}

		for (node in g.nodes) {
			val pos = g.vertexPositions[node]!!
			var v = velocities[node]!!
			v = v.scale(velocityDecay)
			g.vertexPositions[node] = pos.add(v)
			velocities[node] = v
		}
		val end = System.currentTimeMillis()
		if (end - start > 16) {
			Log.w(TAG, "update of ${g.nodes.size} nodes took ${end - start} ms")
		}
		invalidate()
		(context as MainActivity).handler.postDelayed(this::updatePositions, simulationDelay)
	}

	public override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)

		if (g.nodes.isEmpty()) {
			return
		}
		// offset and scale to fit
		var minX = Float.POSITIVE_INFINITY
		var maxX = Float.NEGATIVE_INFINITY
		var minY = Float.POSITIVE_INFINITY
		var maxY = Float.NEGATIVE_INFINITY
		for (pos in g.vertexPositions.values) {
			minX = min(minX, pos.x)
			maxX = max(maxX, pos.x)
			minY = min(minY, pos.y)
			maxY = max(maxY, pos.y)
		}
		if (counter < 20) {
			xScale = width / (maxX - minX)
			yScale = height / (maxY - minY)
			if (yScale < xScale) {
				xScale = yScale
			} else {
				yScale = xScale
			}
		}

		canvas.withTranslation(xOffset(), yOffset()) {
			canvas.withScale(xScale, yScale) {

				if (backgroundColor and 0xffffff == 0) {
					canvas.drawColor(Color.BLACK)
				} else {
					canvas.drawColor(Color.WHITE)
				}

				val height = 20
				val offset = 7

				for (i in 0 until g.edges.size) {
					val a = g.edges[i]

					val sourcePos = g.vertexPositions[a.source]!!
					val targetPos = g.vertexPositions[a.target]!!

					edgePath.reset()
					edgePath.moveTo(sourcePos.x, sourcePos.y)
					edgePath.lineTo(targetPos.x, targetPos.y)
					drawTextOnPath(a.data.name, edgePath, 0f, 30f, fontPaintEdgeLabel)
					val dx = targetPos.x - sourcePos.x
					val dy = targetPos.y - sourcePos.y
					var angle = atan2(dy, dx) - PI.toFloat() / 2F
					if (angle < 0F) {
						angle += 2F * PI.toFloat()
					}
					var x2 = sin(angle - 0.5F) * 20
					var y2 = cos(angle - 0.5F) * 20
					val yf = if (angle >= PI / 2 && angle <= 3 * PI / 2) {
						1
					} else {
						-1
					}
					edgePath.lineTo(targetPos.x, targetPos.y + yf * height - offset)
					edgePath.lineTo(targetPos.x + x2, targetPos.y - y2 + yf * height - offset)
					edgePath.lineTo(targetPos.x, targetPos.y + yf * height - offset)
					x2 = sin(angle + 0.5F) * 20
					y2 = cos(angle + 0.5F) * 20
					edgePath.lineTo(targetPos.x + x2, targetPos.y - y2 + yf * height - offset)
					drawPath(edgePath, paint)
				}

				for (ns in g.nodes) {
					val pos = g.vertexPositions[ns]!!
					val title = ns.title()
					val width = title.length * 15
					drawOval(
						pos.x - width / 2F,
						pos.y - height - offset,
						pos.x + width / 2F,
						pos.y + height - offset,
						backgroundPaint
					)
					drawOval(
						pos.x - width / 2F,
						pos.y - height - offset,
						pos.x + width / 2F,
						pos.y + height - offset,
						paint
					)
					drawText(title, pos.x, pos.y, fontPaint)
				}
			}

		}

	}

	private fun yOffset() = (height.toFloat() / 2F + offsetY)

	private fun xOffset() = width.toFloat() / 2F + offsetX

	private var x: Float? = null
	private var y: Float? = null
	private var dist: Float? = null

	@SuppressLint("ClickableViewAccessibility")
	override fun onTouchEvent(event: MotionEvent?): Boolean {
		if (event == null) {
			return false
		}
		scaleDetector.onTouchEvent(event)
		if (event.action == KeyEvent.ACTION_UP) {
			if (dist != null && dist!! < 50) {
				performClick()
			}
			x = null
			y = null
			dist = null
			return true
		}
		if (x == null && y == null) {
			x = event.x
			y = event.y
			dist = 0F
			return true
		} else {
			val dx = event.x - x!!
			val dy = event.y - y!!
			x = event.x
			y = event.y
			offsetX += dx
			offsetY += dy
			dist = dist!! + dx + dy
			invalidate()
			return true
		}
	}
}
