package com.surafel.audio.checkers

import android.graphics.*
import android.graphics.drawable.Drawable
import android.content.Context
import android.view.View
import kotlin.math.*

/** Native wood grain, stable between redraws and independent of Audio's global theme. */
class CheckersWood : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(c: Canvas) {
        val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
        p.color = Color.WHITE; p.style = Paint.Style.FILL; p.shader = LinearGradient(0f,0f,w,h,intArrayOf(0xFF5B3827.toInt(),0xFF9B6749.toInt(),0xFF573522.toInt()),null,Shader.TileMode.CLAMP)
        c.drawRect(bounds,p); p.shader = null
        for (i in 0..360) {
            val x = w * i / 360; val wave = sin(i * 2.37).toFloat()
            p.color = if (i % 3 == 0) 0x070C0704 else 0x06EFD8B2; p.strokeWidth = max(1f,w/850)
            val path = Path().apply { moveTo(x,0f); cubicTo(x+wave*w*.006f,h*.3f,x-wave*w*.005f,h*.7f,x,h) }
            p.style = Paint.Style.STROKE; c.drawPath(path,p)
        }
        p.color = Color.WHITE; p.style = Paint.Style.FILL; p.shader = RadialGradient(w*.5f,h*.45f,max(w,h)*.8f,intArrayOf(Color.TRANSPARENT,0x99000000.toInt()),null,Shader.TileMode.CLAMP)
        c.drawRect(bounds,p); p.shader = null
    }
    override fun setAlpha(alpha: Int) { p.alpha = alpha }
    override fun setColorFilter(filter: ColorFilter?) { p.colorFilter = filter }
    @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.OPAQUE
}
/** Fine, non-repeating paper flecks for the reference-style cream panels. */
class CheckersPaper : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val rect = RectF(bounds); val radius = bounds.width() * .012f
        paint.color = Color.WHITE; paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, intArrayOf(0xFFE7DCBE.toInt(), 0xFFD8CAA7.toInt(), 0xFFE7DCBE.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, radius, radius, paint); paint.shader = null
        val points = FloatArray(2400)
        for (i in points.indices step 2) { points[i] = rect.left + ((i * 618.03398875) % 1000).toFloat() / 1000 * rect.width(); points[i+1] = rect.top + ((i * i * .754877666) % 1000).toFloat() / 1000 * rect.height() }
        paint.color = 0x18635436; paint.strokeWidth = max(1f, rect.width() / 600f); canvas.drawPoints(points, paint)
        paint.style = Paint.Style.STROKE; paint.color = 0x66FFF0CD; paint.strokeWidth = 1.5f; canvas.drawRoundRect(rect, radius, radius, paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter }
    @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
class CheckersPreview(context: Context, private val design: Int = 0, private val token: Int? = null) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        if (token != null) { CheckersTokens.draw(c,p,width/2f,height/2f,min(width,height)*.35f,1,token); return }
        val cell = min(width,height)*.105f; val left=(width-cell*8)/2; val top=(height-cell*8)/2
        p.color=0xFF6C442D.toInt(); c.drawRect(left-4,top-4,left+cell*8+4,top+cell*8+8,p)
        val colors=CheckersBoardView.palettes[design]
        for (r in 0..7) for (col in 0..7) { p.color=colors[(r+col)%2]; c.drawRect(left+col*cell,top+r*cell,left+(col+1)*cell,top+(r+1)*cell,p) }
    }
}
class CheckersIcon(private val kind: String, private val tint: Int = 0xFFD6C6A4.toInt()) : Drawable() {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(c: Canvas) {
        val saved=c.save(); c.translate(bounds.left.toFloat(),bounds.top.toFloat()); c.scale(bounds.width()/48f,bounds.height()/48f)
        p.color=tint; p.style=Paint.Style.FILL; p.strokeWidth=4f; p.strokeCap=Paint.Cap.ROUND
        fun path(vararg xy:Float) { val path=Path(); path.moveTo(xy[0],xy[1]); for(i in 2 until xy.size step 2) path.lineTo(xy[i],xy[i+1]); path.close(); c.drawPath(path,p) }
        when(kind) {
            "Home" -> { path(2f,22f,24f,3f,46f,22f,39f,22f,39f,44f,29f,44f,29f,30f,19f,30f,19f,44f,9f,44f,9f,22f); c.drawRect(32f,5f,39f,18f,p) }
            "New", "Undo" -> { p.style=Paint.Style.STROKE; c.drawArc(8f,9f,40f,41f,if(kind=="New") 15f else 180f,270f,false,p); p.style=Paint.Style.FILL; if(kind=="New")path(30f,3f,30f,20f,44f,17f) else path(2f,23f,19f,10f,19f,35f) }
            "Settings" -> { for(i in 0..7) { c.save(); c.rotate(i*45f,24f,24f); c.drawRect(19f,1f,29f,12f,p); c.restore() }; c.drawCircle(24f,24f,17f,p); p.color=0xFF68442E.toInt(); c.drawCircle(24f,24f,8f,p) }
            "Stats" -> { c.drawRect(4f,25f,13f,44f,p); c.drawRect(19f,14f,28f,44f,p); c.drawRect(34f,3f,43f,44f,p) }
            "Design" -> { p.style=Paint.Style.STROKE; c.drawRect(3f,3f,45f,45f,p); p.style=Paint.Style.FILL; for(r in 0..3) for(col in 0..3) if((r+col)%2==0)c.drawRect(4f+col*10,4f+r*10,14f+col*10,14f+r*10,p) }
            "Nearby" -> {
                p.style=Paint.Style.STROKE; p.strokeWidth=2.8f
                c.drawRoundRect(2f,15f,17f,44f,2f,2f,p); c.drawRoundRect(31f,15f,46f,44f,2f,2f,p)
                c.drawLine(8f,39f,11f,39f,p); c.drawLine(37f,39f,40f,39f,p)
                c.drawArc(13f,2f,35f,25f,210f,120f,false,p); c.drawArc(19f,9f,29f,19f,210f,120f,false,p)
                c.drawLine(20f,29f,28f,29f,p); c.drawLine(25f,26f,28f,29f,p); c.drawLine(20f,29f,23f,32f,p)
            }
            "Delete" -> { c.drawRect(10f,13f,38f,44f,p); c.drawRoundRect(6f,6f,42f,11f,2f,2f,p); c.drawRect(18f,2f,30f,6f,p) }
            "2 Players" -> {
                c.drawOval(7f,2f,21f,20f,p); c.drawOval(27f,2f,41f,20f,p)
                path(3f,28f,13f,21f,13f,16f,19f,16f,21f,22f,27f,22f,29f,16f,35f,16f,35f,21f,45f,28f,45f,46f,3f,46f)
                p.color=0xFF67472F.toInt(); p.typeface=Typeface.DEFAULT_BOLD; p.textSize=18f; p.textAlign=Paint.Align.CENTER; c.drawText("VS",24f,43f,p)
            }
            else -> { p.typeface=Typeface.DEFAULT_BOLD; p.textSize=44f; p.textAlign=Paint.Align.CENTER; c.drawText("?",24f,40f,p) }
        }; c.restoreToCount(saved)
    }
    override fun setAlpha(alpha:Int) { p.alpha=alpha }
    override fun setColorFilter(filter:ColorFilter?) { p.colorFilter=filter }
    @Deprecated("Deprecated in Android") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
