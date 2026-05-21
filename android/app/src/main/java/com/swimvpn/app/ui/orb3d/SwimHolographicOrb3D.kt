package com.swimvpn.app.ui.orb3d

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.view.Choreographer
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.swimvpn.app.R

@Composable
fun SwimHolographicOrb3D(
    state: SwimParticleOrbState,
    modifier: Modifier = Modifier,
    isReducedMotionEnabled: Boolean = false,
    quality: OrbRenderQuality = OrbRenderQuality.Auto,
    interactionEnabled: Boolean = true,
    renderBehindCompose: Boolean = false,
    onClick: () -> Unit = {},
) {
    val currentOnClick = rememberUpdatedState(onClick)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            OrbGLSurfaceView(context).apply {
                contentDescription = context.getString(R.string.content_desc_swim_orb)
                setRenderBehindCompose(renderBehindCompose)
                update(state, isReducedMotionEnabled, quality)
            }
        },
        update = { view ->
            view.setRenderBehindCompose(renderBehindCompose)
            view.update(state, isReducedMotionEnabled, quality)
            view.isClickable = interactionEnabled
            view.isInteractionEnabled = interactionEnabled
            view.setOnClickListener(
                if (interactionEnabled) {
                    { currentOnClick.value() }
                } else {
                    null
                },
            )
        },
    )
}

private class OrbGLSurfaceView(context: Context) : GLSurfaceView(context) {
    private val orbRenderer = Orb3DRenderer()
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRenderLoopRunning) return
            if (frameTimeNanos - lastRenderRequestNanos >= TargetFrameIntervalNanos) {
                lastRenderRequestNanos = frameTimeNanos
                requestRender()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
    private var isRenderLoopRunning: Boolean = false
    private var lastRenderRequestNanos: Long = 0L
    private var renderBehindCompose: Boolean = false
    var isInteractionEnabled: Boolean = true

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true)
        preserveEGLContextOnPause = true
        setRenderer(orbRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    fun setRenderBehindCompose(value: Boolean) {
        if (renderBehindCompose == value) return
        renderBehindCompose = value
        setZOrderOnTop(!value)
    }

    fun update(
        state: SwimParticleOrbState,
        reducedMotion: Boolean,
        quality: OrbRenderQuality,
    ) {
        orbRenderer.update(state, reducedMotion, quality)
        startRenderLoop()
        requestRender()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isInteractionEnabled) {
            orbRenderer.updateTouch(0.5f, 0.5f, isDown = false)
            return false
        }
        val x = if (width > 0) event.x / width.toFloat() else 0.5f
        val y = if (height > 0) 1f - event.y / height.toFloat() else 0.5f
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE,
            -> orbRenderer.updateTouch(x, y, isDown = true)
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            -> orbRenderer.updateTouch(x, y, isDown = false)
        }
        return super.onTouchEvent(event)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startRenderLoop()
    }

    override fun onResume() {
        super.onResume()
        startRenderLoop()
    }

    override fun onPause() {
        stopRenderLoop()
        super.onPause()
    }

    override fun onDetachedFromWindow() {
        stopRenderLoop()
        onPause()
        super.onDetachedFromWindow()
    }

    private fun startRenderLoop() {
        if (isRenderLoopRunning) return
        isRenderLoopRunning = true
        lastRenderRequestNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopRenderLoop() {
        if (!isRenderLoopRunning) return
        isRenderLoopRunning = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private companion object {
        const val TargetFrameIntervalNanos: Long = 16_666_667L
    }
}
