package dora.widget

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.animation.TranslateAnimation
import android.widget.TextSwitcher
import android.widget.TextView
import dora.widget.flipperview.R
import java.util.concurrent.LinkedBlockingQueue

/**
 * DoraFlipperView
 *
 * A vertical scrolling announcement marquee control that cycles through
 * messages stored in a LinkedBlockingQueue. Supports custom XML attributes:
 *  - app:dview_fv_flipInterval  (milliseconds)
 *  - app:dview_fv_textColor     (color)
 *  - app:dview_fv_textSize      (dimension, in sp or px)
 *  - app:dview_fv_padding       (dimension, padding in dp or px)
 *
 * Now with:
 * 1. Direct next-item display without preview of the following item.
 * 2. Unified listener for click, start, and finish events.
 * 3. Customizable text color, size, and padding.
 */
class DoraFlipperView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextSwitcher(context, attrs) {

    companion object {
        private const val MSG_ADD = 1
        private const val MSG_NEXT = 2
        private const val DEFAULT_INTERVAL = 10_000L
        private val DEFAULT_TEXT_COLOR = Color.BLACK
        private const val DEFAULT_TEXT_SIZE_SP = 14f
        private const val DEFAULT_PADDING_DP = 10f
    }

    private val queue = LinkedBlockingQueue<String>()
    private val uiHandler = Handler(Looper.getMainLooper())
    private val flipperThread = HandlerThread("DoraFlipperThread").apply { start() }
    private val workerHandler: Handler

    private val flipInterval: Long
    private val textColor: Int
    private val textSizePx: Float
    private val paddingPx: Int

    private var currentText: String? = null
    private var hasStarted = false

    interface FlipperListener {
        /** Called when the current item is clicked */
        fun onItemClick(text: String)
        /** Called when the flipper shows the very first item */
        fun onFlipStart()
        /** Called after the flipper has shown the last queued item */
        fun onFlipFinish()
    }
    private var flipperListener: FlipperListener? = null

    init {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.DoraFlipperView)
        flipInterval = ta.getInt(
            R.styleable.DoraFlipperView_dview_fv_flipInterval,
            DEFAULT_INTERVAL.toInt()
        ).toLong()
        textColor = ta.getColor(
            R.styleable.DoraFlipperView_dview_fv_textColor,
            DEFAULT_TEXT_COLOR
        )
        val defaultTextPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            DEFAULT_TEXT_SIZE_SP,
            resources.displayMetrics
        )
        textSizePx = ta.getDimension(
            R.styleable.DoraFlipperView_dview_fv_textSize,
            defaultTextPx
        )
        val defaultPaddingPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            DEFAULT_PADDING_DP,
            resources.displayMetrics
        ).toInt()
        paddingPx = ta.getDimensionPixelSize(
            R.styleable.DoraFlipperView_dview_fv_padding,
            defaultPaddingPx
        )
        ta.recycle()

        setAnimateFirstView(false)
        setFactory {
            TextView(context).apply {
                layoutParams = LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER_VERTICAL
                setSingleLine()
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
                setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
                setOnClickListener {
                    currentText?.let { text ->
                        flipperListener?.onItemClick(text)
                    }
                }
            }
        }

        workerHandler = object : Handler(flipperThread.looper) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    MSG_ADD -> {
                        val text = msg.obj as? String ?: return
                        queue.offer(text)
                        showText(text)
                        removeMessages(MSG_NEXT)
                        sendEmptyMessageDelayed(MSG_NEXT, flipInterval)
                    }
                    MSG_NEXT -> {
                        val next = queue.poll()
                        if (!next.isNullOrEmpty()) showText(next)
                        removeMessages(MSG_NEXT)
                        sendEmptyMessageDelayed(MSG_NEXT, flipInterval)
                    }
                }
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val inAnim = TranslateAnimation(0f, 0f, h.toFloat(), 0f).apply { duration = 500 }
        val outAnim = TranslateAnimation(0f, 0f, 0f, -h.toFloat()).apply { duration = 500 }
        inAnimation = inAnim
        outAnimation = outAnim
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        workerHandler.sendEmptyMessageDelayed(MSG_NEXT, flipInterval)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        workerHandler.removeCallbacksAndMessages(null)
        flipperThread.quitSafely()
        uiHandler.removeCallbacksAndMessages(null)
    }

    /**
     * Add a new text announcement and display immediately.
     */
    fun addText(text: String) {
        workerHandler.obtainMessage(MSG_ADD, text).also { it.sendToTarget() }
    }

    /**
     * Set the listener for flipper events.
     */
    fun setFlipperListener(listener: FlipperListener) {
        flipperListener = listener
    }

    private fun showText(text: String) {
        uiHandler.post {
            currentText = text
            setText(text)
            if (!hasStarted) {
                hasStarted = true
                flipperListener?.onFlipStart()
            } else if (queue.isEmpty()) {
                flipperListener?.onFlipFinish()
            }
        }
    }
}