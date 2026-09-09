/*
 * Loq In
 * Copyright (C) 2026 Loq In Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.oliver.loqin.feature.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.oliver.loqin.R
import com.oliver.loqin.data.prefs.AutomationModeStore
import com.oliver.loqin.data.prefs.BarcodeScanCountStore
import com.oliver.loqin.data.prefs.QrScanCountStore
import com.oliver.loqin.data.prefs.ScanCodeStore
import com.oliver.loqin.nfc.InternalScanDispatchGuard
import com.oliver.loqin.nfc.NfcEntryActivity
import com.oliver.loqin.util.ScanFeedback
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared CameraX/ML Kit scanner for LoqIn QR codes and managed barcodes.
 * AUTO scans both families in one camera session. QR_ONLY and BARCODE_ONLY keep existing dedicated shortcut/widget semantics while sharing the same scanner implementation.
 */
class UnifiedScanActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private val handled = AtomicBoolean(false)

    private val scanMode: ScanMode by lazy {
        ScanMode.fromRaw(intent?.getStringExtra(EXTRA_SCAN_MODE))
    }

    private val scanner by lazy {
        val formats = formatsFor(scanMode)
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(formats.first(), *formats.drop(1).toIntArray())
                .build()
        )
    }

    private val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            ScanFeedback.error(
                this,
                "Scanner",
                "permission_missing",
                getString(R.string.scan_error_camera_permission_code),
                long = true,
            )
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!canOpenScanner()) {
            finish()
            return
        }

        previewView = PreviewView(this)
        setContentView(previewView)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }

    private fun allowDirectOpen(): Boolean =
        intent?.getBooleanExtra(EXTRA_ALLOW_DIRECT_OPEN, false) == true

    private fun canOpenScanner(): Boolean {
        if (allowDirectOpen()) {
            return true
        }

        val qrAllowed = AutomationModeStore.isQrAllowed(this)
        val barcodeAllowed = AutomationModeStore.isBarcodeAllowed(this)
        return when (scanMode) {
            ScanMode.AUTO -> qrAllowed || barcodeAllowed
            ScanMode.QR_ONLY -> qrAllowed
            ScanMode.BARCODE_ONLY -> barcodeAllowed
        }.also { allowed ->
            if (!allowed) {
                val message = when (scanMode) {
                    ScanMode.BARCODE_ONLY -> getString(R.string.mode_blocked_barcode_action)
                    else -> getString(R.string.mode_blocked_qr_action)
                }
                ScanFeedback.error(this, "Scanner", "control_mode_blocked", message)
            }
        }
    }

    private fun startCamera() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            ScanFeedback.error(
                this,
                "Scanner",
                "camera_unavailable",
                getString(R.string.scan_error_camera_unavailable),
                long = true,
            )
            finish()
            return
        }

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = runCatching { providerFuture.get() }.getOrElse {
                ScanFeedback.error(
                    this,
                    "Scanner",
                    "camera_unavailable",
                    getString(R.string.scan_error_camera_unavailable),
                    long = true,
                )
                finish()
                return@addListener
            }

            val selector = when {
                runCatching { provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false) ->
                    CameraSelector.DEFAULT_BACK_CAMERA

                runCatching { provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false) ->
                    CameraSelector.DEFAULT_FRONT_CAMERA

                else -> {
                    ScanFeedback.error(
                        this,
                        "Scanner",
                        "camera_unavailable",
                        getString(R.string.scan_error_camera_unavailable),
                        long = true,
                    )
                    finish()
                    return@addListener
                }
            }

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                analyze(imageProxy)
            }

            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    selector,
                    preview,
                    analysis,
                )
            }.onFailure {
                ScanFeedback.error(
                    this,
                    "Scanner",
                    "camera_unavailable",
                    getString(R.string.scan_error_camera_unavailable),
                    long = true,
                )
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyze(imageProxy: ImageProxy) {
        if (handled.get()) {
            imageProxy.close()
            return
        }

        val image = imageProxy.image ?: run {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees)

        scanner.process(input)
            .addOnSuccessListener { results ->
                selectCandidate(results)?.let { candidate ->
                    handleCode(candidate.raw, candidate.kind)
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    /** Prefer a code that can actually trigger LoqIn when several codes are visible. */
    private fun selectCandidate(results: List<Barcode>): Candidate? {
        val candidates = results.mapNotNull { barcode ->
            val raw = barcode.rawValue?.trim().orEmpty()
            if (raw.isBlank()) {
                null
            } else {
                val kind = if (barcode.format == Barcode.FORMAT_QR_CODE) {
                    ScanCodeStore.Kind.QR
                } else {
                    ScanCodeStore.Kind.BARCODE
                }
                if (!kindAllowedByMode(kind)) null else Candidate(raw, kind)
            }
        }
        if (candidates.isEmpty()) {
            return null
        }

        return candidates.firstOrNull(::isActionable) ?: candidates.first()
    }

    private fun kindAllowedByMode(kind: ScanCodeStore.Kind): Boolean = when (scanMode) {
        ScanMode.AUTO -> true
        ScanMode.QR_ONLY -> kind == ScanCodeStore.Kind.QR
        ScanMode.BARCODE_ONLY -> kind == ScanCodeStore.Kind.BARCODE
    }

    private fun isActionable(candidate: Candidate): Boolean {
        if (ScanCodeStore.findEntry(this, candidate.kind, candidate.raw) != null) {
            return true
        }
        val uri = candidate.raw.toUri()
        if (!uri.scheme.equals("loqin", ignoreCase = true)) {
            return false
        }
        return candidate.kind == ScanCodeStore.Kind.QR || ScanCodeStore.isStrictLoqInUri(candidate.raw)
    }

    private fun handleCode(raw: String, kind: ScanCodeStore.Kind) {
        if (!handled.compareAndSet(false, true)) {
            return
        }

        if (!allowDirectOpen() && !kindAllowedByCurrentControlMode(kind)) {
            val message = if (kind == ScanCodeStore.Kind.QR) {
                getString(R.string.mode_blocked_qr_action)
            } else {
                getString(R.string.mode_blocked_barcode_action)
            }
            ScanFeedback.error(this, kind.raw, "control_mode_blocked", message)
            finish()
            return
        }

        val managed = ScanCodeStore.findEntry(this, kind, raw)
        if (managed != null) {
            incrementScanCount(kind)
            dispatchActionUri(managed.actionUri, kind, managedRawValue = raw)
            return
        }

        val uri = raw.toUri()
        val isLoqInUri = uri.scheme.equals("loqin", ignoreCase = true)
        val validDirectAction = when (kind) {
            ScanCodeStore.Kind.QR -> isLoqInUri
            ScanCodeStore.Kind.BARCODE -> isLoqInUri && ScanCodeStore.isStrictLoqInUri(raw)
        }
        if (validDirectAction) {
            incrementScanCount(kind)
            dispatchActionUri(raw, kind)
            return
        }

        val message = if (kind == ScanCodeStore.Kind.QR) {
            getString(R.string.scan_error_qr_not_linked)
        } else {
            getString(R.string.scan_error_barcode_not_linked)
        }
        ScanFeedback.error(this, kind.raw, "not_linked", message)
        finish()
    }

    private fun kindAllowedByCurrentControlMode(kind: ScanCodeStore.Kind): Boolean = when (kind) {
        ScanCodeStore.Kind.QR -> AutomationModeStore.isQrAllowed(this)
        ScanCodeStore.Kind.BARCODE -> AutomationModeStore.isBarcodeAllowed(this)
    }

    private fun incrementScanCount(kind: ScanCodeStore.Kind) {
        when (kind) {
            ScanCodeStore.Kind.QR -> QrScanCountStore.incrementToday(this)
            ScanCodeStore.Kind.BARCODE -> BarcodeScanCountStore.incrementToday(this)
        }
    }

    private fun dispatchActionUri(
        rawUri: String,
        kind: ScanCodeStore.Kind,
        managedRawValue: String? = null,
    ) {
        val source = kind.raw
        val token = InternalScanDispatchGuard.issue(this, source)
        startActivity(
            Intent(Intent.ACTION_VIEW, rawUri.toUri())
                .putExtra(EXTRA_SCAN_SOURCE, source)
                .putExtra(InternalScanDispatchGuard.EXTRA_TOKEN, token)
                .apply {
                    if (!managedRawValue.isNullOrBlank()) {
                        putExtra(NfcEntryActivity.EXTRA_MANAGED_SCAN_RAW_VALUE, managedRawValue)
                    }
                }
                .setClass(this, NfcEntryActivity::class.java)
        )
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { scanner.close() }
    }

    private data class Candidate(
        val raw: String,
        val kind: ScanCodeStore.Kind,
    )

    enum class ScanMode(val raw: String) {
        AUTO("auto"),
        QR_ONLY("qr"),
        BARCODE_ONLY("barcode");

        companion object {
            fun fromRaw(raw: String?): ScanMode = entries.firstOrNull { it.raw == raw } ?: AUTO
        }
    }

    companion object {
        const val EXTRA_ALLOW_DIRECT_OPEN = "allow_direct_open"
        const val EXTRA_SCAN_MODE = "scan_mode"
        const val EXTRA_SCAN_SOURCE = "extra_scan_source"

        private val BARCODE_FORMATS = intArrayOf(
            Barcode.FORMAT_AZTEC,
            Barcode.FORMAT_CODABAR,
            Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_CODE_93,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_DATA_MATRIX,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_ITF,
            Barcode.FORMAT_PDF417,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
        )

        private fun formatsFor(mode: ScanMode): IntArray = when (mode) {
            ScanMode.AUTO -> intArrayOf(Barcode.FORMAT_QR_CODE, *BARCODE_FORMATS)
            ScanMode.QR_ONLY -> intArrayOf(Barcode.FORMAT_QR_CODE)
            ScanMode.BARCODE_ONLY -> BARCODE_FORMATS
        }
    }
}
