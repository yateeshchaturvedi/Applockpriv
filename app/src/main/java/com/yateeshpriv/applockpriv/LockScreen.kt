package com.yateeshpriv.applockpriv

import android.content.Intent
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@Composable
fun LockScreen(
    packageName: String,
    authMethod: AuthMethod,
    onVerifySecret: (String) -> Boolean,
    biometricAvailable: Boolean,
    onRequestBiometric: () -> Unit,
    onUnlock: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }

    var failedAttempts by remember { mutableIntStateOf(0) }
    var isError by remember { mutableStateOf(false) }

    val imageCapture = remember { ImageCapture.Builder().build() }

    val appName: String = remember(packageName) {
        try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) { packageName }
    }
    val appIcon: Drawable? = remember(packageName) {
        try { context.packageManager.getApplicationIcon(packageName) } catch (_: Exception) { null }
    }

    fun takeIntruderSelfie() {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val cameraProvider = future.get()
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, imageCapture
                )
                val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
                val targetSuffix = if (packageName.isNotBlank()) "_$packageName" else ""
                val file = File(context.filesDir, "intruder_${name}${targetSuffix}.jpg")
                imageCapture.takePicture(
                    ImageCapture.OutputFileOptions.Builder(file).build(),
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            Log.d("AppLock", "Intruder selfie saved: ${file.absolutePath}")
                        }
                        override fun onError(exc: ImageCaptureException) {
                            Log.e("AppLock", "Intruder selfie failed", exc)
                        }
                    }
                )
            } catch (exc: Exception) {
                Log.e("AppLock", "Camera binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun triggerShake() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            offsetX.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 400
                    -24f at 50; 24f at 100; -20f at 150
                    20f at 200; -16f at 250; 16f at 300; -8f at 350
                }
            )
        }
    }

    fun handleWrongAttempt() {
        failedAttempts++
        isError = true
        triggerShake()
        if (failedAttempts >= 5) takeIntruderSelfie()
    }

    fun verify(input: String) {
        if (onVerifySecret(input)) {
            failedAttempts = 0
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onUnlock()
        } else {
            handleWrongAttempt()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Locked App Header ──────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (appIcon != null) {
                    Image(
                        painter = rememberDrawablePainter(drawable = appIcon),
                        contentDescription = null,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = appName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = "PROTECTED BY APP LOCK",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Auth Section ───────────────────────────────────────────────
            when (authMethod) {
                AuthMethod.PASSCODE -> PinKeypadSection(
                    isError = isError,
                    onClearError = { isError = false },
                    onVerify = { pin -> verify(pin) }
                )
                AuthMethod.ALPHANUMERIC -> AlphanumericSection(
                    isError = isError,
                    onClearError = { isError = false },
                    onVerify = { password -> verify(password) }
                )
                AuthMethod.PATTERN -> PatternVerifySection(
                    isError = isError,
                    onClearError = { isError = false },
                    onVerify = { pattern -> verify(pattern.joinToString(",")) }
                )
                AuthMethod.FINGERPRINT -> FingerprintSection(
                    biometricAvailable = biometricAvailable,
                    onRequestBiometric = onRequestBiometric
                )
            }
        }
    }
}

// ── Tactile PIN Keypad Section ──────────────────────────────────────────────

private data class KeypadKey(val number: String, val letters: String)

@Composable
private fun PinKeypadSection(
    isError: Boolean,
    onClearError: () -> Unit,
    onVerify: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var input by remember { mutableStateOf("") }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Enter Passcode",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 6-dot indicator with smooth scaling animations
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(6) { i ->
                val filled = i < input.length
                val dotScale by animateFloatAsState(
                    targetValue = if (filled) 1.2f else 0.85f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "dotScale"
                )
                val dotColor by animateColorAsState(
                    targetValue = if (filled) MaterialTheme.colorScheme.primary
                                  else MaterialTheme.colorScheme.surfaceVariant,
                    label = "dotColor"
                )

                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .scale(dotScale)
                        .clip(CircleShape)
                        .background(dotColor)
                        .border(
                            1.dp,
                            if (filled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape
                        )
                )
            }
        }

        if (isError) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "Incorrect passcode",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Tactile telephone-style keypad layout
        val keypad = listOf(
            listOf(KeypadKey("1", ""), KeypadKey("2", "ABC"), KeypadKey("3", "DEF")),
            listOf(KeypadKey("4", "GHI"), KeypadKey("5", "JKL"), KeypadKey("6", "MNO")),
            listOf(KeypadKey("7", "PQRS"), KeypadKey("8", "TUV"), KeypadKey("9", "WXYZ")),
            listOf(KeypadKey("", ""), KeypadKey("0", "+"), KeypadKey("DEL", ""))
        )

        keypad.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(0.85f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { item ->
                    when (item.number) {
                        "" -> Spacer(modifier = Modifier.size(72.dp))
                        "DEL" -> {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (input.isNotEmpty()) {
                                            input = input.dropLast(1)
                                            onClearError()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        else -> {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (input.length < 6) {
                                            input += item.number
                                            onClearError()
                                            if (input.length == 6) {
                                                val attempt = input
                                                input = ""
                                                onVerify(attempt)
                                            }
                                        }
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = item.number,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (item.letters.isNotEmpty()) {
                                        Text(
                                            text = item.letters,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            letterSpacing = 1.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

// ── Alphanumeric / Password Section ─────────────────────────────────────────

@Composable
private fun AlphanumericSection(
    isError: Boolean,
    onClearError: () -> Unit,
    onVerify: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(0.85f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Enter Password",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; onClearError() },
            label = { Text("Password") },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                if (password.isNotBlank()) { val a = password; password = ""; onVerify(a) }
            }),
            isError = isError,
            supportingText = if (isError) {
                { Text("Incorrect password", color = MaterialTheme.colorScheme.error) }
            } else null,
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = { if (password.isNotBlank()) { val a = password; password = ""; onVerify(a) } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            enabled = password.isNotBlank()
        ) {
            Text("Unlock", fontWeight = FontWeight.Bold)
        }
    }
}

// ── Pattern Verify Section ──────────────────────────────────────────────────

@Composable
private fun PatternVerifySection(
    isError: Boolean,
    onClearError: () -> Unit,
    onVerify: (List<Int>) -> Unit
) {
    var resetCounter by remember { mutableIntStateOf(0) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Draw Pattern",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Connect at least 4 dots to unlock",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (isError) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Incorrect pattern",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier.size(280.dp),
            contentAlignment = Alignment.Center
        ) {
            PatternLockView(
                isError = isError,
                resetKey = resetCounter,
                onPatternComplete = { pattern ->
                    onClearError()
                    onVerify(pattern)
                    resetCounter++
                }
            )
        }
    }
}

// ── Fingerprint Section ─────────────────────────────────────────────────────

@Composable
private fun FingerprintSection(
    biometricAvailable: Boolean,
    onRequestBiometric: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (biometricAvailable) onRequestBiometric()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Biometric Authentication",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (biometricAvailable) "Touch the fingerprint sensor" else "Biometrics not available on this device",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(36.dp))

        Box(contentAlignment = Alignment.Center) {
            // Pulsing background ring
            if (biometricAvailable) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                )
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .clickable(enabled = biometricAvailable) { onRequestBiometric() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Scan fingerprint",
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

// ── Fallback Screen when no credential configured ───────────────────────────

@Composable
fun NoCredentialScreen() {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "App Lock Not Configured",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No passcode or pattern has been set up yet. Open App Lock Priv to configure your credential.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        val intent = Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Open App Lock Priv")
                }
            }
        }
    }
}
