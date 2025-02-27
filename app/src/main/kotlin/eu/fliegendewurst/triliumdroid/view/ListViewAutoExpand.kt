package eu.fliegendewurst.triliumdroid.view

import android.content.Context
import android.util.AttributeSet
import android.widget.ListView


/**
 * ListView hacked to request space for all its children.
 * [Source](https://stackoverflow.com/a/4536955/5837178)
 *
 * @author Neil Traft
 */
class ListViewAutoExpand : ListView {
	var isExpanded: Boolean = true

	constructor(context: Context?) : super(context)

	constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

	constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
		context,
		attrs,
		defStyle
	)

	public override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		// HACK! TAKE THAT ANDROID!
		if (isExpanded) {
			// Calculate entire height by providing a very large height hint.
			// View.MEASURED_SIZE_MASK represents the largest height possible.
			val expandSpec = MeasureSpec.makeMeasureSpec(MEASURED_SIZE_MASK, MeasureSpec.AT_MOST)
			super.onMeasure(widthMeasureSpec, expandSpec)

			val params = layoutParams
			params.height = measuredHeight
		} else {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec)
		}
	}
}
