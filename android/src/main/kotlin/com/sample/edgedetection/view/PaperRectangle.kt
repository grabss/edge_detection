package com.sample.edgedetection.view

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.sample.edgedetection.SourceManager
import com.sample.edgedetection.processor.Corners
import com.sample.edgedetection.processor.TAG
import com.sample.edgedetection.processor.convertDpToPx
import org.opencv.core.Point
import org.opencv.core.Size

class PaperRectangle : View {
    constructor(context: Context) : super(context)
    constructor(context: Context, attributes: AttributeSet) : super(context, attributes)
    constructor(context: Context, attributes: AttributeSet, defTheme: Int) : super(context, attributes, defTheme)

    private val rectPaint = Paint()
    private val rectPaintOnCamera = Paint()
    private val fillRectPaint = Paint()
    private val clearPaint = Paint()
    private val circlePaint = Paint()
    private var ratioX: Double = 1.0
    private var ratioY: Double = 1.0
    private var tl: Point = Point()
    private var tr: Point = Point()
    private var br: Point = Point()
    private var bl: Point = Point()
    private val path: Path = Path()
    private var point2Move = Point()
    private var cropMode = false
    private var latestDownX = 0.0F
    private var latestDownY = 0.0F
    private val offset = 1F

    init {

        println("init")

        clearPaint.apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }

        rectPaint.apply {
            color = Color.WHITE
            isAntiAlias = true
            isDither = true
            strokeWidth = 2F
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND    // set the join to round you want
            strokeCap = Paint.Cap.ROUND      // set the paint cap to round too
            pathEffect = CornerPathEffect(0f)
        }

        rectPaintOnCamera.apply {
            color = Color.parseColor("#4284E4")
            isAntiAlias = true
            isDither = true
            strokeWidth = 6F
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND    // set the join to round you want
            strokeCap = Paint.Cap.ROUND      // set the paint cap to round too
            pathEffect = CornerPathEffect(0f)
        }

        fillRectPaint.apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#4D4284E4")
        }

        circlePaint.apply {
            color = Color.WHITE
            isDither = true
            isAntiAlias = true
            strokeWidth = 2F
            style = Paint.Style.STROKE
        }
    }

    fun onCornersDetected(corners: Corners) {
        ratioX = corners.size.width.div(measuredWidth)
//        println("ratioX: $ratioX")
//        println("measuredWidth: $measuredWidth")
        ratioY = corners.size.height.div(measuredHeight)
//        println("ratioY: $ratioY")
//        println("measuredHeight: $measuredHeight")
        tl = corners.corners[0] ?: Point()
        tr = corners.corners[1] ?: Point()
        br = corners.corners[2] ?: Point()
        bl = corners.corners[3] ?: Point()

//        Log.i(TAG, "POINTS ------>  ${tl.toString()} corners")

        resize()
        path.reset()
        path.moveTo(tl.x.toFloat(), tl.y.toFloat())
        path.lineTo(tr.x.toFloat(), tr.y.toFloat())
        path.lineTo(br.x.toFloat(), br.y.toFloat())
        path.lineTo(bl.x.toFloat(), bl.y.toFloat())
        path.close()
        invalidate()
    }

    fun onCornersNotDetected() {
        path.reset()
        invalidate()
    }

    fun onCorners2Crop(corners: Corners?, size: Size?, picWidth: Int, picHeight: Int) {
        println("onCorners2Crop")
        println("corners: $corners")
        println("size: $size")
        cropMode = true
        tl = corners?.corners?.get(0) ?: Point(size?.width?.times(0.05) ?: 0.0, size?.height?.times(0.05) ?: 0.0)
        println("tl: $tl")
        tr = corners?.corners?.get(1) ?: Point(size?.width?.times(0.95) ?: 0.0, size?.height?.times(0.05) ?: 0.0)
        println("tr: $tr")
        br = corners?.corners?.get(2) ?: Point(size?.width?.times(0.95) ?: 0.0, size?.height?.times(0.95) ?: 0.0)
        println("br: $br")
        bl = corners?.corners?.get(3) ?: Point(size?.width?.times(0.05) ?: 0.0, size?.height?.times(0.95) ?: 0.0)
        println("bl: $bl")


        val displayMetrics = DisplayMetrics()
        (context as Activity).windowManager.defaultDisplay.getMetrics(displayMetrics)
        //exclude status bar height
        val statusBarHeight = getStatusBarHeight(context)
        println("statusBarHeight: $statusBarHeight")
//        val hoge = (context as Activity).findViewById<ImageView>(R.id.paper)
//        println("hoge: ${hoge.width}")
//        ratioX = size?.width?.div(displayMetrics.widthPixels) ?: 1.0
        ratioX = size?.width?.div(picWidth) ?: 1.0
        println("ratioX: $ratioX")
        val titleHeight = convertDpToPx(40f, context).toInt()
        val footerHeight = convertDpToPx(60f, context).toInt()
//        ratioY = size?.height?.div(displayMetrics.heightPixels - statusBarHeight - footerHeight - titleHeight) ?: 1.0
        ratioY = size?.height?.div(picHeight) ?: 1.0
        println("ratioY: $ratioY")
        println("picWidth: $picWidth")
        println("picHeight: $picHeight")
        println("displayMetrics.widthPixels: ${displayMetrics.widthPixels}")
        println("displayMetrics.heightPixels: ${displayMetrics.heightPixels}")
        println("displayMetrics.heightPixels2: ${displayMetrics.heightPixels - statusBarHeight - footerHeight - titleHeight}")
        resize()
        movePoints()
    }

    fun getCorners2Crop(): List<Point> {
        reverseSize()
        return listOf(tl, tr, br, bl)
    }



    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        println("onDraw")
        println("canvas height: ${canvas?.height}")
        println("canvas width: ${canvas?.width}")
        if (cropMode) {
            canvas?.drawColor(Color.argb(200, 0, 0,0))

            // 外枠
            canvas?.drawRect(offset, offset,canvas?.width.minus(offset),canvas?.height.minus(offset), rectPaint)
            println("path: $path")
            canvas?.drawPath(path, clearPaint)
            canvas?.drawPath(path, rectPaint)
            canvas?.drawCircle(tl.x.toFloat(), tl.y.toFloat(), 20F, circlePaint)
            canvas?.drawCircle(tr.x.toFloat(), tr.y.toFloat(), 20F, circlePaint)
            canvas?.drawCircle(bl.x.toFloat(), bl.y.toFloat(), 20F, circlePaint)
            canvas?.drawCircle(br.x.toFloat(), br.y.toFloat(), 20F, circlePaint)
        } else {
            canvas?.drawPath(path, rectPaintOnCamera)
            canvas?.drawPath(path, fillRectPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {

        if (!cropMode) {
            return false
        }

        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                latestDownX = event.x
                latestDownY = event.y
                calculatePoint2Move(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                point2Move.x = (event.x - latestDownX) + point2Move.x
                point2Move.y = (event.y - latestDownY) + point2Move.y
                movePoints()
                latestDownY = event.y
                latestDownX = event.x
            }
            MotionEvent.ACTION_UP -> {
                println("action up")
            }
            MotionEvent.ACTION_CANCEL -> {
                println("action cancel")
            }
            MotionEvent.ACTION_DOWN -> {
                println("action down")
            }
        }
        return true
    }

    private fun calculatePoint2Move(downX: Float, downY: Float) {
        val points = listOf(tl, tr, br, bl)
        point2Move = points.minBy { Math.abs((it.x - downX).times(it.y - downY)) } ?: tl
    }

    private fun movePoints() {
        path.reset()
        path.moveTo(tl.x.toFloat(), tl.y.toFloat())
        path.lineTo(tr.x.toFloat(), tr.y.toFloat())
        path.lineTo(br.x.toFloat(), br.y.toFloat())
        path.lineTo(bl.x.toFloat(), bl.y.toFloat())
        path.close()
        invalidate()
    }


    private fun resize() {
        tl.x = tl.x.div(ratioX)
        tl.y = tl.y.div(ratioY)
        tr.x = tr.x.div(ratioX)
        tr.y = tr.y.div(ratioY)
        br.x = br.x.div(ratioX)
        br.y = br.y.div(ratioY)
        bl.x = bl.x.div(ratioX)
        bl.y = bl.y.div(ratioY)
    }

    private fun reverseSize() {
        tl.x = tl.x.times(ratioX)
        tl.y = tl.y.times(ratioY)
        tr.x = tr.x.times(ratioX)
        tr.y = tr.y.times(ratioY)
        br.x = br.x.times(ratioX)
        br.y = br.y.times(ratioY)
        bl.x = bl.x.times(ratioX)
        bl.y = bl.y.times(ratioY)
    }

    private fun getNavigationBarHeight(pContext: Context): Int {
        val resources = pContext.resources
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else 0
    }

    private fun getStatusBarHeight(pContext: Context): Int {
        val resources = pContext.resources
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else 0
    }
}