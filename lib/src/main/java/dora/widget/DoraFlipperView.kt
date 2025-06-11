package dora.widget

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.text.TextUtils
import android.util.AttributeSet
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
 * messages stored in a LinkedBlockingQueue. Supports custom XML attribute
 * `app:flipInterval` to specify display duration (in milliseconds). Uses
 * a HandlerThread and Handler for background scheduling. Each message
 * displays for the configured interval, and any newly added message will
 * interrupt and display immediately.
 */
class DoraFlipperView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextSwitcher(context, attrs) {

    companion object {
        private const val MSG_ADD = 1
        private const val MSG_NEXT = 2
        private const val DEFAULT_INTERVAL = 10_000L
    }

    // Queue of pending messages
    private val queue = LinkedBlockingQueue<String>()
    private val uiHandler = Handler(Looper.getMainLooper())

    // Display interval read from custom attribute or default
    private val flipInterval: Long

    // Background thread and handler
    private val flipperThread: HandlerThread = HandlerThread("DoraFlipperThread").apply { start() }
    private val workerHandler: Handler

    init {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.DoraFlipperView)
        flipInterval = ta.getInt(
            R.styleable.DoraFlipperView_dview_fv_flipInterval,
            DEFAULT_INTERVAL.toInt()
        ).toLong()
        ta.recycle()
        setFactory {
            TextView(context).apply {
                layoutParams = LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER_VERTICAL
                setSingleLine()
                ellipsize = TextUtils.TruncateAt.END
            }
        }

        // Initialize worker handler
        workerHandler = object : Handler(flipperThread.looper) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    MSG_ADD -> {
                        val text = msg.obj as? String ?: return
                        queue.offer(text)
                        // Display immediately
                        showText(text)
                        // Reset next flip timer
                        removeMessages(MSG_NEXT)
                        sendEmptyMessageDelayed(MSG_NEXT, flipInterval)
                    }
                    MSG_NEXT -> {
                        val next = queue.poll()
                        if (!next.isNullOrEmpty()) {
                            showText(next)
                        }
                        // Schedule next flip
                        sendEmptyMessageDelayed(MSG_NEXT, flipInterval)
                    }
                }
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Vertical slide animations based on view height
        val inAnim = TranslateAnimation(0f, 0f, h.toFloat(), 0f).apply { duration = 500 }
        val outAnim = TranslateAnimation(0f, 0f, 0f, -h.toFloat()).apply { duration = 500 }
        inAnimation = inAnim
        outAnimation = outAnim
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Start the flipping loop
        workerHandler.sendEmptyMessageDelayed(MSG_NEXT, flipInterval)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Clean up handlers and thread
        workerHandler.removeCallbacksAndMessages(null)
        flipperThread.quitSafely()
        uiHandler.removeCallbacksAndMessages(null)
    }

    /**
     * Add a new text to the queue and display it immediately.
     * @param text The announcement text
     */
    fun addText(text: String) {
        val msg = workerHandler.obtainMessage(MSG_ADD, text)
        workerHandler.sendMessage(msg)
    }

    /**
     * Post the text update to the UI thread via TextSwitcher.
     */
    private fun showText(text: String) {
        uiHandler.post {
            setText(text)
        }
    }
}
