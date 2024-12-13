package eu.fliegendewurst.triliumdroid.view


import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Align
import android.graphics.Path
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.withTranslation
import eu.fliegendewurst.triliumdroid.MainActivity
import eu.fliegendewurst.triliumdroid.R
import eu.fliegendewurst.triliumdroid.data.Note
import eu.fliegendewurst.triliumdroid.data.Relation
import eu.fliegendewurst.triliumdroid.util.Graph
import eu.fliegendewurst.triliumdroid.util.Position
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class GraphView(context: Context, attributes: AttributeSet?) : View(context, attributes) {
	var g: Graph<Note, Relation> = Graph()

	private var paint: Paint = Paint()
	private var backgroundPaint: Paint = Paint()
	private var fontPaint: Paint = Paint()

	private var edgePath: Path = Path()

	private var offsetX: Float = 0F
	private var offsetY: Float = 0F

	init {
		g = Graph()

		paint.strokeWidth = 4f
		paint.style = Paint.Style.STROKE
		paint.strokeJoin = Paint.Join.ROUND
		paint.isAntiAlias = true
		paint.color = resources.getColor(android.R.color.black, null)

		backgroundPaint.style = Paint.Style.FILL
		backgroundPaint.isAntiAlias = true
		backgroundPaint.color = resources.getColor(R.color.primary, null)

		fontPaint.textAlign = Align.CENTER
		fontPaint.textSize = 24f

		setOnClickListener {
			val point = Position(x!! - xOffset(), y!! - yOffset())
			for (node in g.nodes) {
				val dist = g.vertexPositions[node]!!.distance(point)
				if (dist < 100 * 100) {
					(context as MainActivity).navigateTo(node)
					return@setOnClickListener
				}
			}
		}
	}

	public override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)

		canvas.withTranslation(xOffset(), yOffset()) {

			canvas.drawColor(Color.WHITE)

			val height = 20
			val offset = 7

			for (i in 0 until g.edges.size) {
				val a = g.edges[i]

				val sourcePos = g.vertexPositions[a.source]!!
				val targetPos = g.vertexPositions[a.target]!!

				edgePath.reset()
				edgePath.moveTo(sourcePos.x, sourcePos.y)
				edgePath.lineTo(targetPos.x, targetPos.y)
				canvas.drawTextOnPath(a.data.name, edgePath, 0f, 30f, fontPaint)
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
				canvas.drawPath(edgePath, paint)
			}

			for (ns in g.nodes) {
				val pos = g.vertexPositions[ns]!!
				val width = ns.title.length * 15
				canvas.drawOval(
					pos.x - width / 2F,
					pos.y - height - offset,
					pos.x + width / 2F,
					pos.y + height - offset,
					backgroundPaint
				)
				canvas.drawOval(
					pos.x - width / 2F,
					pos.y - height - offset,
					pos.x + width / 2F,
					pos.y + height - offset,
					paint
				)
				canvas.drawText(ns.title, pos.x, pos.y, fontPaint)
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
