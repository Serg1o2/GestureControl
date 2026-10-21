package com.gesturecontrol.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Binds the front camera (analysis + optional preview) to a lifecycle owner.
 * All public methods must be called from the main thread.
 */
class CameraController(private val context: Context) {
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var boundUseCases: Array<UseCase> = emptyArray()

    val isBound: Boolean get() = analysis != null

    suspend fun bind(
        owner: LifecycleOwner,
        analyzer: ImageAnalysis.Analyzer,
        targetRotation: Int,
        surfaceProvider: Preview.SurfaceProvider? = null,
    ) {
        val cameraProvider = provider ?: awaitCameraProvider().also { provider = it }
        unbind()

        val resolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(ANALYSIS_SIZE, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
            )
            .build()
        val imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolution)
            .setTargetRotation(targetRotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { it.setAnalyzer(analysisExecutor, analyzer) }

        val useCases = buildList {
            add(imageAnalysis)
            if (surfaceProvider != null) {
                add(
                    Preview.Builder()
                        .setResolutionSelector(
                            ResolutionSelector.Builder()
                                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                                .build(),
                        )
                        .setTargetRotation(targetRotation)
                        .build()
                        .also { it.surfaceProvider = surfaceProvider },
                )
            }
        }
        boundUseCases = useCases.toTypedArray()
        cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, *boundUseCases)
        analysis = imageAnalysis
    }

    fun setTargetRotation(rotation: Int) {
        analysis?.targetRotation = rotation
    }

    fun unbind() {
        analysis?.clearAnalyzer()
        analysis = null
        // Only our own use cases: the camera provider is shared with other controllers.
        if (boundUseCases.isNotEmpty()) provider?.unbind(*boundUseCases)
        boundUseCases = emptyArray()
    }

    /** Runs [block] on the analysis thread after any in-flight frame, e.g. to release the landmarker. */
    fun runOnAnalysisThread(block: () -> Unit) = analysisExecutor.execute(block)

    fun shutdown() {
        unbind()
        analysisExecutor.shutdown()
    }

    private suspend fun awaitCameraProvider(): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                try {
                    cont.resume(future.get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private companion object {
        val ANALYSIS_SIZE = Size(640, 480)
    }
}
