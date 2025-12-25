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
import java.util.ArrayDeque

/**
 * DoraFlipperView
 *
 * 垂直滚动文字控件
 *
 * 特性：
 * 1. 支持无限条数据
 * 2. 支持 WebSocket 推送消息插队（最前显示）
 * 3. 历史消息顺序轮播
 * 4. 单线程队列调度，避免并发问题
 * 5. 生命周期安全，自动清理 Handler
 *
 * XML Attributes:
 *  - app:dview_fv_flipInterval  翻页间隔（毫秒）
 *  - app:dview_fv_textColor     文本颜色
 *  - app:dview_fv_textSize      文本大小
 *  - app:dview_fv_padding       内边距
 */
class DoraFlipperView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextSwitcher(context, attrs) {

    companion object {
        /** 如WebSocket推送：高优先级，插到最前 */
        private const val MSG_ADD_FIRST = 0

        /** 历史消息：普通追加 */
        private const val MSG_ADD_LAST = 1

        /** 自动翻页 */
        private const val MSG_NEXT = 2

        private const val DEFAULT_INTERVAL = 10_000L
        private val DEFAULT_TEXT_COLOR = Color.BLACK
        private const val DEFAULT_TEXT_SIZE_SP = 14f
        private const val DEFAULT_PADDING_DP = 10f
    }

    /** 双端队列：支持头插 / 尾插 */
    private val queue: ArrayDeque<String> = ArrayDeque()

    private val uiHandler = Handler(Looper.getMainLooper())
    private val flipperThread = HandlerThread("DoraFlipperThread").apply { start() }
    private val workerHandler: Handler

    private val flipInterval: Long
    private val textColor: Int
    private val textSizePx: Float
    private val paddingPx: Int

    private var currentText: String? = null
    private var hasStarted = false

    /**
     * 翻页监听器
     */
    interface FlipperListener {
        /** 点击当前文本 */
        fun onItemClick(text: String)

        /** 第一次开始播放 */
        fun onFlipStart()

        /** 队列播放完毕 */
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

        animateFirstView = false
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
                    currentText?.let { flipperListener?.onItemClick(it) }
                }
            }
        }

        workerHandler = object : Handler(flipperThread.looper) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {

                    /** 如WS推送：插队并立即显示 */
                    MSG_ADD_FIRST -> {
                        val text = msg.obj as? String ?: return
                        queue.addFirst(text)
                        showText(text)
                        scheduleNext()
                    }

                    /** 历史消息：顺序追加 */
                    MSG_ADD_LAST -> {
                        val text = msg.obj as? String ?: return
                        queue.addLast(text)
                        if (currentText == null) {
                            showText(text)
                            scheduleNext()
                        }
                    }

                    /** 自动翻页 */
                    MSG_NEXT -> {
                        val next = queue.pollFirst()
                        if (next != null) {
                            showText(next)
                            scheduleNext()
                        } else {
                            uiHandler.post {
                                flipperListener?.onFlipFinish()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        inAnimation = TranslateAnimation(0f, 0f, h.toFloat(), 0f).apply {
            duration = 500
        }
        outAnimation = TranslateAnimation(0f, 0f, 0f, -h.toFloat()).apply {
            duration = 500
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scheduleNext()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        workerHandler.removeCallbacksAndMessages(null)
        flipperThread.quitSafely()
        uiHandler.removeCallbacksAndMessages(null)
    }

    /**
     * 添加历史消息（尾插）。
     */
    fun addText(text: String) {
        workerHandler.obtainMessage(MSG_ADD_LAST, text).sendToTarget()
    }

    /**
     * 添加推送消息（头插，立即显示）。
     */
    fun addTextFirst(text: String) {
        workerHandler.obtainMessage(MSG_ADD_FIRST, text).sendToTarget()
    }

    /**
     * 清空所有消息并重置状态。
     */
    fun clear() {
        workerHandler.removeCallbacksAndMessages(null)
        queue.clear()
        currentText = null
        hasStarted = false
    }

    /**
     * 设置翻页监听。
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
            }
        }
    }

    private fun scheduleNext() {
        workerHandler.removeMessages(MSG_NEXT)
        workerHandler.sendEmptyMessageDelayed(MSG_NEXT, flipInterval)
    }
}
