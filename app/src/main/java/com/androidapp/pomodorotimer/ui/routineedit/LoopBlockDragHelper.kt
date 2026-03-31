package com.androidapp.pomodorotimer.ui.routineedit

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

/**
 * LoopStart ドラッグ中に LoopStart〜LoopEnd の全行を
 * 「カードが縦に積み重なった」見た目で表示するヘルパー。
 *
 * 見た目:
 *   - 各カードを下に 3dp ずつずらして描画（X方向はずらさない）
 *   - 後ろカードはインデントが深い分だけ左マージンを付けて表示
 *   - 一番手前（LoopStart）だけ実際の View を描画
 *   - それ以降は背景色のみのダミーカードを積み重ねる
 */
class LoopBlockDragHelper(private val recyclerView: RecyclerView) {

    private var overlayView: ImageView? = null
    private var dimmedViews: List<View> = emptyList()
    private var draggedView: View? = null

    /**
     * [blockViewHolders] は表示順（LoopStart が先頭）の ViewHolder リスト。
     * [blockEntries]      は対応する RoutineListEntry リスト（depth 情報取得用）。
     */
    fun startOverlay(
        dragViewHolder: RecyclerView.ViewHolder,
        blockViewHolders: List<RecyclerView.ViewHolder>,
        blockEntries: List<RoutineListEntry>,
        @Suppress("UNUSED_PARAMETER") initialTouchY: Float
    ) {
        stopOverlay()
        if (blockViewHolders.isEmpty()) return

        val density      = recyclerView.context.resources.displayMetrics.density
        val stackOffsetY = (3 * density)              // 各カードを下に 3dp ずらす
        val shadowRadius = 8 * density
        val shadowDy     = 4 * density
        val cornerRadius = 16 * density
        val indentPerDepth = (20 * density)           // 1段あたりのインデント量 (Adapterと合わせる)
        val baseMarginStart = (12 * density)          // カード基本左マージン

        val maxStack  = 3                             // 後ろに見せる最大枚数
        val stackCount = (blockViewHolders.size - 1).coerceAtMost(maxStack)

        val frontView  = blockViewHolders.first().itemView
        val baseWidth  = recyclerView.width
        val baseHeight = frontView.height

        // Bitmap サイズ = 先頭カードサイズ + スタック分の縦オフセット + 影余白
        val shadowMargin = (shadowRadius + shadowDy).toInt() + 4
        val bitmapW = baseWidth + shadowMargin
        val bitmapH = baseHeight + (stackCount * stackOffsetY).toInt() + shadowMargin
        if (bitmapW <= 0 || bitmapH <= 0) return

        val bitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x66000000
            maskFilter = BlurMaskFilter(shadowRadius, BlurMaskFilter.Blur.NORMAL)
        }
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        // ---- 後ろカード（奥→手前の順に描画） ----
        for (stackIdx in stackCount downTo 1) {
            val oy = stackIdx * stackOffsetY

            // このスタック位置に対応する ViewHolder のインデント深さを取得
            // blockEntries は LoopStart が index=0、以降が内アイテム
            val entryDepth = blockEntries.getOrNull(stackIdx)?.let {
                when (it) {
                    is RoutineListEntry.Item      -> it.depth
                    is RoutineListEntry.AddButton -> it.depth
                }
            } ?: 1  // 情報がなければ depth=1（ループ内）とみなす

            // インデント量：depth 分だけ左をずらし、その分幅も縮める
            val indentLeft  = baseMarginStart + entryDepth * indentPerDepth
            val cardWidth   = (baseWidth - indentLeft - baseMarginStart).coerceAtLeast(40 * density)

            // 影
            canvas.drawRoundRect(
                RectF(
                    indentLeft,
                    oy + shadowDy,
                    indentLeft + cardWidth,
                    oy + baseHeight - 2
                ),
                cornerRadius, cornerRadius, shadowPaint
            )

            // カード本体（後ろほど少し暗め）
            cardPaint.color = blendDarker(CARD_COLOR, stackIdx * 12)
            canvas.drawRoundRect(
                RectF(indentLeft, oy, indentLeft + cardWidth, oy + baseHeight),
                cornerRadius, cornerRadius, cardPaint
            )
        }

        // ---- 手前カード（LoopStart を実描画） ----

        // 影
        canvas.drawRoundRect(
            RectF(0f, shadowDy, baseWidth.toFloat(), baseHeight.toFloat() - 2),
            cornerRadius, cornerRadius, shadowPaint
        )

        // View を Bitmap に描画
        val frontBitmap = Bitmap.createBitmap(baseWidth, baseHeight, Bitmap.Config.ARGB_8888)
        val frontCanvas = Canvas(frontBitmap)
        frontView.draw(frontCanvas)

        // 角丸クリップして貼り付け
        val clipPath = Path().apply {
            addRoundRect(
                RectF(0f, 0f, baseWidth.toFloat(), baseHeight.toFloat()),
                cornerRadius, cornerRadius,
                Path.Direction.CW
            )
        }
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawBitmap(frontBitmap, 0f, 0f, null)
        canvas.restore()
        frontBitmap.recycle()

        // ---- オーバーレイ ImageView を親に追加 ----
        val parent = findSuitableParent(recyclerView) ?: return

        val rvLoc     = IntArray(2).also { recyclerView.getLocationInWindow(it) }
        val parentLoc = IntArray(2).also { (parent as View).getLocationInWindow(it) }

        val blockTopInWindow = IntArray(2).also { frontView.getLocationInWindow(it) }[1]
        val initialTop  = blockTopInWindow - parentLoc[1]
        val initialLeft = rvLoc[0] - parentLoc[0]

        val iv = ImageView(recyclerView.context).apply {
            setImageBitmap(bitmap)
            elevation = 24 * density
        }
        parent.addView(iv, ViewGroup.LayoutParams(bitmapW, bitmapH))
        iv.translationX = initialLeft.toFloat()
        iv.translationY = initialTop.toFloat()

        overlayView = iv

        // LoopStart 以外のブロック行を透明化
        dimmedViews = blockViewHolders.drop(1).map { it.itemView }
        dimmedViews.forEach { it.alpha = 0f }
        draggedView = frontView
        draggedView?.alpha = 0f
    }

    fun updateOverlayPosition(draggedItemTop: Float) {
        val iv     = overlayView ?: return
        val parent = iv.parent as? View ?: return

        val parentLoc = IntArray(2).also { parent.getLocationInWindow(it) }
        val rvLoc     = IntArray(2).also { recyclerView.getLocationInWindow(it) }

        iv.translationY = (rvLoc[1] - parentLoc[1]) + draggedItemTop
    }

    fun stopOverlay() {
        overlayView?.let { iv ->
            (iv.drawable as? android.graphics.drawable.BitmapDrawable)
                ?.bitmap?.recycle()
            (iv.parent as? ViewGroup)?.removeView(iv)
        }
        overlayView = null
        dimmedViews.forEach { it.alpha = 1f }
        dimmedViews = emptyList()
        draggedView?.alpha = 1f
        draggedView = null
    }

    val isActive: Boolean get() = overlayView != null

    private fun findSuitableParent(view: View): ViewGroup? {
        var v: View? = view
        while (v != null) {
            val parent = v.parent
            if (parent is ViewGroup) return parent
            v = parent as? View
        }
        return null
    }

    private fun blendDarker(color: Int, amount: Int): Int {
        val r = ((color shr 16 and 0xFF) - amount).coerceAtLeast(0)
        val g = ((color shr  8 and 0xFF) - amount).coerceAtLeast(0)
        val b = ((color        and 0xFF) - amount).coerceAtLeast(0)
        val a =  (color shr 24 and 0xFF)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    companion object {
        private val CARD_COLOR = 0xFF2C2C2E.toInt()
    }
}