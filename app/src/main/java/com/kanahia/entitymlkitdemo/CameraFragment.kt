package com.kanahia.entitymlkitdemo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.kanahia.entitymlkitdemo.databinding.FragmentCameraBinding
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractor
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment() {
    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var entityExtractor: EntityExtractor

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(),
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }

        binding.captureButton.setOnClickListener {
            captureImage()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize the entity extractor
        val options = EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH)
            .build()
        entityExtractor = EntityExtraction.getClient(options)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun captureImage() {
        val imageCapture = imageCapture ?: return

        binding.progressBar.visibility = View.VISIBLE

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    processImage(image)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(
                        requireContext(),
                        "Failed to capture image",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: return
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        // Use ML Kit's text recognition to extract text from the image
        val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        textRecognizer.process(image)
            .addOnSuccessListener { text ->
                processRecognizedText(text)
                imageProxy.close()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Text recognition failed: ${e.message}", e)
                imageProxy.close()
                binding.progressBar.visibility = View.GONE
                Toast.makeText(
                    requireContext(),
                    "Failed to recognize text",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun processRecognizedText(text: Text) {
        val extractedText = text.text
        Log.d(TAG, "Extracted text: $extractedText")

        entityExtractor.downloadModelIfNeeded()
            .addOnSuccessListener {
                entityExtractor.annotate(extractedText)
                    .addOnSuccessListener { entityAnnotations ->
                        val entityItems = mutableListOf<EntityItem>()

                        for (entityAnnotation in entityAnnotations) {
                            val annotatedText = extractedText.substring(
                                entityAnnotation.start,
                                entityAnnotation.end
                            )

                            for (entity in entityAnnotation.entities) {
                                entityItems.add(
                                    EntityItem(
                                        name = annotatedText,
                                        type = getEntityTypeString(entity.type),
                                        description = getEntityDescription(entity)
                                    )
                                )
                            }
                        }

                        binding.progressBar.visibility = View.GONE

                        val action = CameraFragmentDirections.actionCameraToEntities(
                            entityItems.toTypedArray()
                        )
                        findNavController().navigate(action)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Entity extraction failed: ${e.message}", e)
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(
                            requireContext(),
                            "Failed to extract entities",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Model download failed: ${e.message}", e)
                binding.progressBar.visibility = View.GONE
                Toast.makeText(
                    requireContext(),
                    "Failed to download entity extraction model",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun getEntityTypeString(type: Int): String {
        return when (type) {
            Entity.TYPE_ADDRESS -> "Address"
            Entity.TYPE_EMAIL -> "Email"
            Entity.TYPE_FLIGHT_NUMBER -> "Flight Number"
            Entity.TYPE_IBAN -> "IBAN"
            Entity.TYPE_ISBN -> "ISBN"
            Entity.TYPE_MONEY -> "Money"
            Entity.TYPE_PAYMENT_CARD -> "Payment Card"
            Entity.TYPE_PHONE -> "Phone"
            Entity.TYPE_TRACKING_NUMBER -> "Tracking Number"
            Entity.TYPE_URL -> "URL"
            else -> "Unknown"
        }
    }

    private fun getEntityDescription(entity: Entity): String {
        val description = StringBuilder()
        description.append("Type: ${getEntityTypeString(entity.type)}")
        return description.toString()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            requireContext(), it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                requireActivity().finish()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }

    companion object {
        private const val TAG = "CameraFragment"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}