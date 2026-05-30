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
import androidx.core.content.withStyledAttributes
import java.util.concurrent.LinkedBlockingQueue

class DoraFlipperView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextSwitcher(context, attrs) {

    private val queue = LinkedBlockingQueue<String>()
    private val displayList = ArrayDeque<String>()
    private val uiHandler = Handler(Looper.getMainLooper())
    private val flipperThread = HandlerThread(
        "DoraFlipperThread"
    ).apply {
        start()
    }
    private val workerHandler: Handler
    private var flipperInterval: Long = DEFAULT_CAROUSE_INTERVAL
    private var textColor: Int = DEFAULT_TEXT_COLOR
    private var textSizePx: Float = 10f
    private var paddingPx: Int = 0
    private var currentText: String? = null
    private var currentIndex: Int = 0
    private var hasStarted = false
    private var playMode = PlayMode.STOP_AT_LAST
    private var animationDuration = DEFAULT_ANIMATION_DURATION
    private val lock = Any()

    interface FlipperListener {

        fun onClickText(index: Int, text: String)
        fun onLoadText(index: Int, text: String)
        fun onFlipStart()
        fun onFlipFinish()
    }

    private var flipperListener: FlipperListener? = null

    init {
        context.withStyledAttributes(
            attrs,
            R.styleable.DoraFlipperView
        ) {
            flipperInterval = getInt(
                R.styleable.DoraFlipperView_dview_fv_flipInterval,
                flipperInterval.toInt()
            ).toLong()
            textColor = getColor(
                R.styleable.DoraFlipperView_dview_fv_textColor,
                textColor
            )
            val defaultTextPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                DEFAULT_TEXT_SIZE_SP,
                resources.displayMetrics
            )
            textSizePx = getDimension(
                R.styleable.DoraFlipperView_dview_fv_textSize,
                defaultTextPx
            )
            val defaultPaddingPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                DEFAULT_PADDING_DP,
                resources.displayMetrics
            ).toInt()
            paddingPx = getDimensionPixelSize(
                R.styleable.DoraFlipperView_dview_fv_padding,
                defaultPaddingPx
            )
        }
        animateFirstView = false
        setFactory {
            TextView(context).apply {
                layoutParams = LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER_VERTICAL
                setSingleLine()
                ellipsize = TextUtils.TruncateAt.MARQUEE
                marqueeRepeatLimit = -1
                isSelected = true
                isFocusable = true
                isFocusableInTouchMode = true
                setTextColor(textColor)
                setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    textSizePx
                )
                setPadding(
                    paddingPx,
                    paddingPx,
                    paddingPx,
                    paddingPx
                )
                setOnClickListener {
                    currentText?.let { text ->
                        flipperListener?.onClickText(
                            currentIndex,
                            text
                        )
                    }
                }
            }
        }

        workerHandler = object : Handler(
            flipperThread.looper
        ) {

            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    MSG_ADD -> {
                        val text = msg.obj as? String
                            ?: return
                        if (text.isBlank()) {
                            return
                        }
                        while (queue.size >= MAX_QUEUE_SIZE) {
                            queue.poll()
                        }
                        queue.offer(text)
                        if (!hasStarted) {
                            start()
                        }
                    }
                    MSG_NEXT -> {
                        if (displayList.size == 1 &&
                            playMode == PlayMode.STOP_AT_LAST) {
                            removeMessages(MSG_NEXT)
                            hasStarted = false
                            flipperListener?.onFlipFinish()
                            return
                        }
                        while (true) {
                            val text = queue.poll() ?: break
                            synchronized(lock) {
                                displayList.addLast(text)
                                if (displayList.size > MAX_CACHE_SIZE) {
                                    displayList.removeFirst()
                                    if (currentIndex > 0) {
                                        currentIndex--
                                    }
                                }
                            }
                        }
                        if (displayList.isEmpty()) {
                            hasStarted = false
                            flipperListener?.onFlipFinish()
                            removeMessages(MSG_NEXT)
                            return
                        }
                        if (displayList.size > 1) {
                            if (currentIndex >= displayList.size - 1) {
                                when (playMode) {
                                    PlayMode.LOOP -> {
                                        currentIndex = 0
                                    }
                                    PlayMode.STOP_AT_LAST -> {
                                        currentIndex = displayList.size - 1
                                        removeMessages(MSG_NEXT)
                                        hasStarted = false
                                        flipperListener?.onFlipFinish()
                                        return
                                    }
                                }
                            } else {
                                currentIndex++
                            }
                        } else {
                            currentIndex = 0
                        }
                        synchronized(lock) {
                            if (displayList.isEmpty()) return
                            val safeIndex = currentIndex.coerceIn(0, displayList.size - 1)
                            val text = displayList[safeIndex]
                            currentIndex = safeIndex
                            showText(
                                currentIndex,
                                text
                            )
                            flipperListener?.onLoadText(
                                currentIndex,
                                text
                            )
                        }
                        removeMessages(MSG_NEXT)
                        sendEmptyMessageDelayed(
                            MSG_NEXT,
                            flipperInterval
                        )
                    }
                }
            }
        }
    }

    fun setPlayMode(playMode: PlayMode) {
        this.playMode = playMode
    }

    fun setAnimationDuration(duration: Long) {
        if (duration < 0) {
            return
        }
        animationDuration = duration
        post {
            val h = height
            if (h <= 0) {
                return@post
            }
            val inAnim = TranslateAnimation(
                0f,
                0f,
                h.toFloat(),
                0f
            ).apply {
                this.duration = animationDuration
            }
            val outAnim = TranslateAnimation(
                0f,
                0f,
                0f,
                -h.toFloat()
            ).apply {
                this.duration = animationDuration
            }
            inAnimation = inAnim
            outAnimation = outAnim
        }
    }

    fun setCarouseInterval(timeMs: Long) {
        if (timeMs <= 0) return
        flipperInterval = if (timeMs <= animationDuration) {
            animationDuration + 100
        } else {
            period
        }
        if (hasStarted) {
            workerHandler.removeMessages(MSG_NEXT)
            workerHandler.sendEmptyMessageDelayed(
                MSG_NEXT,
                flipperInterval
            )
        }
    }

    private fun start() {
        val first = queue.poll()
            ?: return
        hasStarted = true
        synchronized(lock) {
            displayList.add(first)
            if (displayList.size > MAX_CACHE_SIZE) {
                displayList.removeFirst()
            }
        }
        currentIndex = 0
        showText(
            currentIndex,
            first
        )
        flipperListener?.onLoadText(
            currentIndex,
            first
        )
        flipperListener?.onFlipStart()
        workerHandler.removeMessages(MSG_NEXT)
        workerHandler.sendEmptyMessageDelayed(
            MSG_NEXT,
            flipperInterval
        )
    }

    fun stop() {
        hasStarted = false
        workerHandler.removeMessages(MSG_NEXT)
        workerHandler.removeMessages(MSG_ADD)
    }
    
    private fun showText(
        index: Int,
        text: String
    ) {
        uiHandler.post {
            currentIndex = index
            currentText = text
            setText(text)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val inAnim = TranslateAnimation(0f, 0f, h.toFloat(), 0f).apply {
            duration = animationDuration
        }
        val outAnim = TranslateAnimation(
            0f,
            0f,
            0f,
            -h.toFloat()
        ).apply {
            duration = animationDuration
        }
        inAnimation = inAnim
        outAnimation = outAnim
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (hasStarted) {
            workerHandler.removeMessages(MSG_NEXT)
            workerHandler.sendEmptyMessageDelayed(
                MSG_NEXT,
                flipperInterval
            )
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop()
        workerHandler.removeCallbacksAndMessages(null)
        if (flipperThread.isAlive) {
            flipperThread.quitSafely()
        }
        flipperListener?.onFlipFinish()
    }

    fun addText(text: String) {
        workerHandler.obtainMessage(
            MSG_ADD,
            text
        ).also {
            it.sendToTarget()
        }
    }

    fun clear() {
        workerHandler.post {
            workerHandler.removeMessages(MSG_ADD)
            workerHandler.removeMessages(MSG_NEXT)
            queue.clear()
            synchronized(lock) {
                displayList.clear()
            }
            currentText = null
            currentIndex = 0
            hasStarted = false
            flipperListener?.onFlipFinish()
            uiHandler.post {
                setText("")
            }
        }
    }

    fun getQueueSize(): Int {
        return displayList.size + queue.size
    }

    fun setFlipperListener(flipperListener: FlipperListener) {
        this.flipperListener = flipperListener
    }

    enum class PlayMode {
        LOOP,
        STOP_AT_LAST
    }
    
    companion object {

        private const val MSG_ADD = 1
        private const val MSG_NEXT = 2
        private const val MAX_QUEUE_SIZE = 300
        private const val MAX_CACHE_SIZE = 100
        private const val DEFAULT_CAROUSE_INTERVAL = 10_000L
        private const val DEFAULT_ANIMATION_DURATION = 500L
        private const val DEFAULT_TEXT_COLOR = Color.BLACK
        private const val DEFAULT_TEXT_SIZE_SP = 14f
        private const val DEFAULT_PADDING_DP = 10f
    }
}
