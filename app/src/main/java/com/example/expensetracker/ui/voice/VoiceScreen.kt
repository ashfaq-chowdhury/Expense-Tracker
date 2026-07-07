package com.example.expensetracker.ui.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.expensetracker.data.Category
import com.example.expensetracker.data.MainViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

fun guessCategory(text: String): Category {
    val lower = text.lowercase()
    return when {
        lower.contains("food") || lower.contains("lunch") || lower.contains("dinner") ||
        lower.contains("breakfast") || lower.contains("coffee") || lower.contains("restaurant") ||
        lower.contains("groceries") || lower.contains("pizza") || lower.contains("burger") ||
        lower.contains("chipotle") || lower.contains("starbucks") || lower.contains("eat") -> Category.FOOD
        lower.contains("uber") || lower.contains("lyft") || lower.contains("taxi") ||
        lower.contains("bus") || lower.contains("train") || lower.contains("transport") ||
        lower.contains("gas") || lower.contains("fuel") || lower.contains("metro") -> Category.TRANSPORT
        lower.contains("netflix") || lower.contains("movie") || lower.contains("spotify") ||
        lower.contains("entertainment") || lower.contains("game") || lower.contains("cinema") -> Category.ENTERTAINMENT
        lower.contains("amazon") || lower.contains("shop") || lower.contains("bought") ||
        lower.contains("store") || lower.contains("mall") || lower.contains("clothes") -> Category.SHOPPING
        lower.contains("rent") || lower.contains("mortgage") || lower.contains("lease") -> Category.RENT
        lower.contains("electric") || lower.contains("water") || lower.contains("internet") ||
        lower.contains("bill") || lower.contains("utilities") || lower.contains("phone") -> Category.UTILITIES
        lower.contains("salary") || lower.contains("income") || lower.contains("freelance") ||
        lower.contains("received") || lower.contains("payment") || lower.contains("earned") -> Category.INCOME
        else -> Category.FOOD
    }
}

@Composable
fun VoiceScreen(viewModel: MainViewModel = viewModel(), contentBottomPadding: Dp = 120.dp) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Tap to log expense") }
    var resultText by remember { mutableStateOf("") }
    var resultIsSuccess by remember { mutableStateOf(false) }

    // Edit dialog state — shown after voice capture so user can correct
    var showEditDialog by remember { mutableStateOf(false) }
    var pendingDesc by remember { mutableStateOf("") }
    var pendingAmount by remember { mutableStateOf("") }
    var pendingCategory by remember { mutableStateOf(Category.FOOD) }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasMicPermission = granted }

    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    DisposableEffect(Unit) { onDispose { speechRecognizer.destroy() } }

    fun processHeard(text: String) {
        val amountRegex = Regex("""[\$£€]?\s*(\d+(?:\.\d{1,2})?)""")
        val amount = amountRegex.find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val category = guessCategory(text)
        pendingDesc = text.replaceFirstChar { it.uppercase() }
        pendingAmount = if (amount > 0) amount.toString() else ""
        pendingCategory = category
        showEditDialog = true
        statusText = "Tap to log expense"
        isRecording = false
    }

    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { statusText = "Listening..." }
            override fun onBeginningOfSpeech() { statusText = "Listening..." }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { statusText = "Processing..." }
            override fun onError(error: Int) {
                isRecording = false
                statusText = "Tap to log expense"
                resultText = "Error. Please try again."
                resultIsSuccess = false
            }
            override fun onResults(results: Bundle?) {
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                processHeard(heard)
            }
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(e: Int, p: Bundle?) {}
        })
        speechRecognizer.startListening(intent)
    }

    // Confirm dialog — lets user tweak desc/amount/category before saving
    if (showEditDialog) {
        var editDesc by remember(pendingDesc) { mutableStateOf(pendingDesc) }
        var editAmount by remember(pendingAmount) { mutableStateOf(pendingAmount) }
        var editCategory by remember(pendingCategory) { mutableStateOf(pendingCategory) }
        var isIncome by remember { mutableStateOf(pendingCategory == Category.INCOME) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Confirm Transaction") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editDesc,
                        onValueChange = { editDesc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editAmount,
                        onValueChange = { editAmount = it },
                        label = { Text("Amount") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Category", style = MaterialTheme.typography.labelMedium)
                    // Category chips
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            listOf(Category.FOOD, Category.TRANSPORT, Category.SHOPPING),
                            listOf(Category.ENTERTAINMENT, Category.RENT, Category.UTILITIES),
                            listOf(Category.INCOME)
                        ).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                row.forEach { cat ->
                                    FilterChip(
                                        selected = editCategory == cat,
                                        onClick = {
                                            editCategory = cat
                                            isIncome = cat == Category.INCOME
                                        },
                                        label = { Text(cat.label, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amt = editAmount.toDoubleOrNull() ?: 0.0
                    if (amt > 0) {
                        val finalAmt = if (isIncome) amt else -amt
                        val today = LocalDate.now().toString()
                        val now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                        viewModel.addTransaction(editDesc, editCategory, finalAmt, today, now)
                        resultText = "✓ Saved: ${if (isIncome) "+" else "-"}${"%,.2f".format(amt)} • ${editCategory.label}"
                        resultIsSuccess = true
                    }
                    showEditDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.5f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = contentBottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(statusText, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(12.dp))
        Text(
            "Try: \"Spent \$15 on lunch\"\nor \"Received \$500 salary\"",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))

        if (resultText.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (resultIsSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    resultText,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (resultIsSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(28.dp))
        }

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .scale(scale)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), CircleShape)
            )
            Button(
                onClick = {
                    if (!hasMicPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        isRecording = !isRecording
                        if (isRecording) startListening() else speechRecognizer.stopListening()
                    }
                },
                modifier = Modifier.size(110.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(if (isRecording) "STOP" else "MIC", style = MaterialTheme.typography.titleLarge)
            }
        }

        if (!hasMicPermission) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Tap MIC to grant microphone permission",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
    }
}
