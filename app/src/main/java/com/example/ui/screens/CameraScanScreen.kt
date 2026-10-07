package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.SampleFoodItem
import com.example.ui.components.SamplePickerSheet
import com.example.util.BitmapUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScanScreen(
    onPhotoCaptured: (Bitmap) -> Unit,
    onSampleSelected: (SampleFoodItem) -> Unit,
    onNavigateToHistory: () -> Unit,
    hasHistory: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    // Auto-prompt camera permission on first composition
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            CoroutineScope(Dispatchers.Main).launch {
                val bitmap = BitmapUtils.uriToBitmap(context, uri)
                if (bitmap != null) {
                    onPhotoCaptured(bitmap)
                }
            }
        }
    }

    var showSampleSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    if (showSampleSheet) {
        SamplePickerSheet(
            sheetState = sheetState,
            onDismiss = { showSampleSheet = false },
            onSelectSample = { sample ->
                showSampleSheet = false
                onSampleSelected(sample)
            }
        )
    }

    if (hasCameraPermission) {
        CameraViewfinderContent(
            onCapture = onPhotoCaptured,
            onOpenGallery = {
                photoPickerLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            },
            onOpenSamples = { showSampleSheet = true },
            onNavigateToHistory = onNavigateToHistory,
            hasHistory = hasHistory,
            modifier = modifier
        )
    } else {
        CameraPermissionFallbackContent(
            onRequestPermission = {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onOpenGallery = {
                photoPickerLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            },
            onOpenSamples = { showSampleSheet = true },
            onNavigateToHistory = onNavigateToHistory,
            hasHistory = hasHistory,
            modifier = modifier
        )
    }
}

@Composable
private fun CameraViewfinderContent(
    onCapture: (Bitmap) -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSamples: () -> Unit,
    onNavigateToHistory: () -> Unit,
    hasHistory: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var cameraInstance: Camera? by remember { mutableStateOf(null) }
    var isTorchEnabled by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    val laserPosition by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // CameraX Surface
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    imageCapture = capture

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraInstance = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            capture
                        )
                    } catch (exc: Exception) {
                        // Handled
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top bar with header and flash toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2E7D32))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NutriCheck AI",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row {
                if (hasHistory) {
                    IconButton(
                        onClick = onNavigateToHistory,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("history_button")
                    ) {
                        Icon(Icons.Default.History, contentDescription = "Historie")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                IconButton(
                    onClick = {
                        val newTorchState = !isTorchEnabled
                        cameraInstance?.cameraControl?.enableTorch(newTorchState)
                        isTorchEnabled = newTorchState
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.6f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("torch_button")
                ) {
                    Icon(
                        imageVector = if (isTorchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Blesk"
                    )
                }
            }
        }

        // Viewfinder targeting frame overlay
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val frameWidth = maxWidth * 0.85f
            val frameHeight = maxHeight * 0.45f

            Box(
                modifier = Modifier
                    .size(width = frameWidth, height = frameHeight)
                    .clip(RoundedCornerShape(20.dp))
                    .border(
                        width = 2.5.dp,
                        color = Color.White.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                // Animated laser scanner bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.TopCenter)
                        .padding(top = (frameHeight.value * laserPosition).dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color(0xFF4CAF50),
                                    Color(0xFF81C784),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            // Guide instruction label
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = frameHeight + 36.dp)
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = "Zamiřte rámeček na text se složením potraviny",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }

        // Bottom Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Quick sample pill
            FilledTonalButton(
                onClick = onOpenSamples,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.White.copy(alpha = 0.2f),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .testTag("sample_pill_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Nemáte potravinu? Vyzkoušet ukázku")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery picker button
                IconButton(
                    onClick = onOpenGallery,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.25f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("gallery_picker_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Vybrat z galerie",
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Shutter Capture button
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f))
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            if (isCapturing) return@IconButton
                            isCapturing = true
                            val capture = imageCapture ?: run {
                                isCapturing = false
                                return@IconButton
                            }

                            capture.takePicture(
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        val rawBitmap = image.toBitmap()
                                        val rotationDegrees = image.imageInfo.rotationDegrees.toFloat()
                                        val finalBitmap = BitmapUtils.rotateBitmap(rawBitmap, rotationDegrees)
                                        image.close()
                                        isCapturing = false
                                        onCapture(finalBitmap)
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        isCapturing = false
                                    }
                                }
                            )
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.White,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .size(72.dp)
                            .testTag("capture_shutter_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Vyfotit složení",
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Empty placeholder or second icon for layout balance
                IconButton(
                    onClick = onOpenSamples,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.25f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("samples_modal_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = "Ukázky",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionFallbackContent(
    onRequestPermission: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSamples: () -> Unit,
    onNavigateToHistory: () -> Unit,
    hasHistory: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "NutriCheck",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (hasHistory) {
                IconButton(onClick = onNavigateToHistory) {
                    Icon(Icons.Default.History, contentDescription = "Historie")
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_hero_food),
                contentDescription = "Vyfoťte složení potraviny",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Vyfoťte složení potraviny",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Pro okamžité zhodnocení zdravosti potraviny pomocí Gemini AI potřebuje aplikace přístup k fotoaparátu. Vyfotíte etiketu a AI vám shrne nutriční kvalitu i éčka.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onRequestPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("grant_permission_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Povolit fotoaparát a fotit", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onOpenGallery,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("open_gallery_fallback_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Nahrát fotku z galerie")
            }

            FilledTonalButton(
                onClick = onOpenSamples,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("open_samples_fallback_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Lightbulb, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Vyzkoušet na ukázce složení")
            }
        }
    }
}
