package com.surafel.audio.pdf

import android.content.Context
import android.graphics.*
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/** Continuous, vertically recycled pages. Only attached pages own rendered bitmaps. */
class PdfScrollView(context: Context, private val model: PdfReaderModel) : RecyclerView(context) {
    private val manager = LinearLayoutManager(context)
    private var count = 0
    private var zoom = 1f
    private var pan = 0f
    private val ratios = mutableMapOf<Int, Float>()
    private var downX = 0f; private var downY = 0f; private var previousX = 0f
    private var zoomGesture = false; private var panGesture = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    var onPositionChanged: ((Int) -> Unit)? = null
    var onSettled: ((Int) -> Unit)? = null
    var night = false
        set(value) { field = value; for (i in 0 until childCount) getChildAt(i).invalidate() }
    var inputEnabled = true
    val currentPage: Int get() {
        val first = manager.findFirstVisibleItemPosition()
        if (first == NO_POSITION) return 0
        val view = manager.findViewByPosition(first) ?: return first
        return if (view.bottom < height / 3 && first + 1 < count) first + 1 else first
    }
    val loadedPages: Set<Int> get() = (0 until childCount).mapNotNull {
        (getChildAt(it) as? Sheet)?.takeIf { sheet -> sheet.image != null }?.index
    }.toSet()
    private val sheets = object : Adapter<Holder>() {
        override fun getItemCount() = count
        override fun onCreateViewHolder(parent: android.view.ViewGroup, type: Int) = Holder(Sheet())
        override fun onBindViewHolder(holder: Holder, index: Int) {
            val sheet = holder.sheet
            sheet.clear(); sheet.index = index
            sheet.contentDescription = "PDF page ${index + 1}"
            sheet.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, pageHeight(index))
            sheet.request = model.renderForScroll(index) { bitmap, error ->
                if (sheet.index != index) { bitmap?.recycle(); return@renderForScroll }
                sheet.image = bitmap; sheet.error = error
                if (bitmap != null) ratios[index] = bitmap.height.toFloat() / bitmap.width
                sheet.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, pageHeight(index))
                sheet.invalidate()
            }
        }
        override fun onViewRecycled(holder: Holder) { holder.sheet.clear() }
    }
    private inner class Holder(val sheet: Sheet) : ViewHolder(sheet)
    private inner class Sheet : View(context) {
        var index = -1
        var image: Bitmap? = null
        var error: String? = null
        var request: PdfReaderModel.PageRequest? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        fun clear() {
            request?.cancel(); request = null
            image?.recycle(); image = null; index = -1; error = null
        }
        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(if (night) Color.rgb(16, 18, 24) else Color.rgb(52, 57, 67))
            val gap = 8 * resources.displayMetrics.density
            val w = width * zoom
            val left = (width - w) / 2 + pan
            val rect = RectF(left, 0f, left + w, height - gap)
            paint.color = if (night) Color.rgb(24, 24, 24) else Color.WHITE
            paint.colorFilter = null; canvas.drawRect(rect, paint)
            val bitmap = image
            if (bitmap != null && !bitmap.isRecycled) {
                paint.colorFilter = if (night) ColorMatrixColorFilter(floatArrayOf(
                    -1f,0f,0f,0f,255f, 0f,-1f,0f,0f,255f, 0f,0f,-1f,0f,255f, 0f,0f,0f,1f,0f)) else null
                canvas.drawBitmap(bitmap, null, rect, paint); paint.colorFilter = null
            } else {
                paint.color = if (night) Color.LTGRAY else Color.DKGRAY
                paint.textSize = 15 * resources.displayMetrics.scaledDensity
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(error ?: "Loading page ${index + 1}…", width / 2f, minOf(height / 2f, 100f * resources.displayMetrics.density), paint)
            }
        }
    }
    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector) = true
        override fun onScale(detector: ScaleGestureDetector): Boolean { setZoom(zoom * detector.scaleFactor); return true }
    })
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onDoubleTap(e: MotionEvent): Boolean {
            cancelScrollTouch(e); zoomGesture = true
            setZoom(if (zoom > 1f) 1f else 2.5f); return true
        }
    })
    init {
        layoutManager = manager; adapter = sheets
        itemAnimator = null; setItemViewCacheSize(0); recycledViewPool.setMaxRecycledViews(0, 2)
        isVerticalScrollBarEnabled = true
        contentDescription = "PDF document. Swipe up or down to read; pinch to zoom; double tap to reset zoom."
        addOnScrollListener(object : OnScrollListener() {
            override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
                if (count > 0) { onPositionChanged?.invoke(currentPage); if (scrollState == SCROLL_STATE_IDLE) settle() }
            }
            override fun onScrollStateChanged(view: RecyclerView, state: Int) { if (state == SCROLL_STATE_IDLE) settle() }
        })
    }
    private fun pageHeight(index: Int) = (((if (width > 0) width else resources.displayMetrics.widthPixels) * zoom * (ratios[index] ?: 1.4142f))
        .toDouble().coerceIn(1.0, 1000000.0).toInt() + (8 * resources.displayMetrics.density).toInt())
    fun showDocument(pages: Int, initialPage: Int) {
        if (pages == count) return
        count = pages; sheets.notifyDataSetChanged()
        if (count > 0) scrollToPage(initialPage)
    }
    fun scrollToPage(index: Int) {
        if (index !in 0 until count) return
        stopScroll(); manager.scrollToPositionWithOffset(index, 0)
    }
    fun settle() { if (count > 0 && manager.findFirstVisibleItemPosition() != NO_POSITION) onSettled?.invoke(currentPage) }
    private fun setZoom(value: Float) {
        val next = value.coerceIn(1f, 5f)
        if (abs(next - zoom) < .001f) return
        val first = manager.findFirstVisibleItemPosition()
        val view = manager.findViewByPosition(first)
        val fraction = if (view != null && view.height > 0) -view.top.toFloat() / view.height else 0f
        zoom = next
        pan = pan.coerceIn(-width * (zoom - 1f) / 2, width * (zoom - 1f) / 2)
        for (i in 0 until childCount) {
            val sheet = getChildAt(i) as Sheet
            sheet.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, pageHeight(sheet.index))
            sheet.invalidate()
        }
        if (first != NO_POSITION) manager.scrollToPositionWithOffset(first, -(fraction * pageHeight(first)).toInt())
    }
    private fun cancelScrollTouch(event: MotionEvent) {
        stopScroll()
        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
        super.dispatchTouchEvent(cancel); cancel.recycle()
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!inputEnabled) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            zoomGesture = false; panGesture = false
            downX = event.x; previousX = event.x; downY = event.y
        }
        gestures.onTouchEvent(event); scale.onTouchEvent(event)
        if (event.pointerCount > 1 && !zoomGesture) { cancelScrollTouch(event); zoomGesture = true }
        if (!zoomGesture && zoom > 1f && event.actionMasked == MotionEvent.ACTION_MOVE) {
            if (!panGesture && abs(event.x - downX) > slop && abs(event.x - downX) > abs(event.y - downY)) {
                cancelScrollTouch(event); panGesture = true
            }
            if (panGesture) {
                val limit = width * (zoom - 1f) / 2
                pan = (pan + event.x - previousX).coerceIn(-limit, limit)
                for (i in 0 until childCount) getChildAt(i).invalidate()
            }
        }
        previousX = event.x
        if (zoomGesture || panGesture) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) { zoomGesture = false; panGesture = false; settle() }
            return true
        }
        return super.dispatchTouchEvent(event)
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw) post { if (adapter === sheets) sheets.notifyDataSetChanged() }
    }
    fun release() {
        stopScroll(); adapter = null
        recycledViewPool.clear()
    }
}
