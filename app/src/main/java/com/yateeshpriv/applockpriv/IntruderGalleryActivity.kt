package com.yateeshpriv.applockpriv

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.yateeshpriv.applockpriv.ui.theme.ApplockprivTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class IntruderGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settingsPrefs = PrefsHelper.getSettingsPrefs(this)
            val themeMode = ThemeMode.fromValue(
                settingsPrefs.getString("theme_mode", ThemeMode.SYSTEM.value)
            )
            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            ApplockprivTheme(darkTheme = isDark) {
                IntruderGalleryScreen(onBack = { finish() })
            }
        }
    }
}

data class IntruderPhoto(
    val file: File,
    val capturedAt: Date,
    val targetPackage: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntruderGalleryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var photos by remember { mutableStateOf(loadIntruderPhotos(context)) }
    var selectedPhoto by remember { mutableStateOf<IntruderPhoto?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Intruder Photos", fontWeight = FontWeight.Bold)
                        if (photos.isNotEmpty()) {
                            Text(
                                "${photos.size} attempt${if (photos.size != 1) "s" else ""} captured",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (photos.isNotEmpty()) {
                        IconButton(onClick = { showDeleteAllDialog = true }) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Delete all",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (photos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.size(90.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "No Intruders Detected",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "When someone enters an incorrect passcode or pattern 5 times, a front-camera selfie will automatically be saved here along with the target app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Header log summary card
                item(span = { GridItemSpan(2) }) {
                    val latest = photos.first()
                    val targetAppName = remember(latest.targetPackage) {
                        getAppLabel(context, latest.targetPackage)
                    }
                    ElevatedCard(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Intruder Detection Log",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (targetAppName != null) "Target: $targetAppName • ${formatDate(latest.capturedAt)}"
                                           else "Latest: ${formatDate(latest.capturedAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Photo grid cards
                items(photos, key = { it.file.absolutePath }) { photo ->
                    PhotoCard(
                        photo = photo,
                        onClick = { selectedPhoto = photo },
                        onDelete = {
                            photo.file.delete()
                            photos = loadIntruderPhotos(context)
                        }
                    )
                }
            }
        }
    }

    // Full photo modal view with target app info
    selectedPhoto?.let { photo ->
        val targetLabel = remember(photo.targetPackage) { getAppLabel(context, photo.targetPackage) }
        val targetIcon = remember(photo.targetPackage) { getAppIcon(context, photo.targetPackage) }

        Dialog(onDismissRequest = { selectedPhoto = null }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp)),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    val bitmap = remember(photo.file) {
                        try { BitmapFactory.decodeFile(photo.file.absolutePath)?.asImageBitmap() }
                        catch (_: Exception) { null }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Intruder photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp)
                                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                        )
                    }

                    // Target app row
                    if (targetLabel != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (targetIcon != null) {
                                Image(
                                    painter = rememberDrawablePainter(drawable = targetIcon),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Column {
                                Text(
                                    text = "Targeted App: $targetLabel",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                photo.targetPackage?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Failed Attempt",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                formatDate(photo.capturedAt),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        TextButton(
                            onClick = {
                                photo.file.delete()
                                photos = loadIntruderPhotos(context)
                                selectedPhoto = null
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete All Photos?") },
            text = { Text("This will permanently remove all ${photos.size} intruder records.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        photos.forEach { it.file.delete() }
                        photos = emptyList()
                        showDeleteAllDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete All") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PhotoCard(
    photo: IntruderPhoto,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val targetLabel = remember(photo.targetPackage) { getAppLabel(context, photo.targetPackage) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box {
            val bitmap = remember(photo.file) {
                try { BitmapFactory.decodeFile(photo.file.absolutePath)?.asImageBitmap() }
                catch (_: Exception) { null }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Intruder photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Target app pill in top-left corner
            if (targetLabel != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f),
                    modifier = Modifier.padding(6.dp).align(Alignment.TopStart)
                ) {
                    Text(
                        text = targetLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Date pill overlay at bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    formatDate(photo.capturedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }

            // Delete icon
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(36.dp)
                    .padding(4.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

fun parseIntruderFileName(fileName: String, dateFormat: SimpleDateFormat): Pair<Date?, String?> {
    val clean = fileName.removePrefix("intruder_").removeSuffix(".jpg")
    val parts = clean.split("_")
    val timestampStr = if (parts.size >= 2) "${parts[0]}_${parts[1]}" else parts[0]
    val date = try { dateFormat.parse(timestampStr) } catch (_: Exception) { null }
    val pkg = if (parts.size >= 3) parts.drop(2).joinToString("_") else null
    return Pair(date, pkg)
}

private fun loadIntruderPhotos(context: Context): List<IntruderPhoto> {
    val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    return context.filesDir
        .listFiles { f -> f.name.startsWith("intruder_") && f.name.endsWith(".jpg") }
        ?.mapNotNull { file ->
            try {
                val (date, targetPackage) = parseIntruderFileName(file.name, dateFormat)
                IntruderPhoto(
                    file = file,
                    capturedAt = date ?: Date(file.lastModified()),
                    targetPackage = targetPackage
                )
            } catch (_: Exception) {
                IntruderPhoto(file, Date(file.lastModified()))
            }
        }
        ?.sortedByDescending { it.capturedAt }
        ?: emptyList()
}

fun getAppLabel(context: Context, packageName: String?): String? {
    if (packageName == null) return null
    return try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    } catch (_: Exception) {
        packageName
    }
}

fun getAppIcon(context: Context, packageName: String?): Drawable? {
    if (packageName == null) return null
    return try {
        context.packageManager.getApplicationIcon(packageName)
    } catch (_: Exception) {
        null
    }
}

private fun formatDate(date: Date): String =
    SimpleDateFormat("MMM dd, yyyy  hh:mm a", Locale.getDefault()).format(date)
