package com.example.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val activeAcc by viewModel.activeAccount.collectAsStateWithLifecycle()

    var showCreateAccountDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "TRADING JOURNAL",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 2.sp,
                                    color = CrimsonAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Row(
                                modifier = Modifier.clickable { /* drop down */ },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = activeAcc?.name ?: "No Account",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Switch Account",
                                    tint = CrimsonAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Short Active Balance Highlight
                        activeAcc?.let { acc ->
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Balance",
                                    fontSize = 10.sp,
                                    color = TextSecondaryDark
                                )
                                Text(
                                    text = "${acc.currency} " + String.format("%,.2f", acc.balance),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextGreenHighlight
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfacePanelDark,
                    titleContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { showCreateAccountDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "New Account",
                            tint = Color.White
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SurfacePanelDark,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { viewModel.currentTab.value = 0 },
                    icon = { Icon(Icons.Default.Refresh, contentDescription = "Calculator") },
                    label = { Text("Calculator") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        unselectedIconColor = TextSecondaryDark,
                        selectedTextColor = Color.White,
                        unselectedTextColor = TextSecondaryDark,
                        indicatorColor = CrimsonPrimary
                    )
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { viewModel.currentTab.value = 1 },
                    icon = { Icon(Icons.Default.List, contentDescription = "Journal & Strategy") },
                    label = { Text("Journal & Risk Plan") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        unselectedIconColor = TextSecondaryDark,
                        selectedTextColor = Color.White,
                        unselectedTextColor = TextSecondaryDark,
                        indicatorColor = CrimsonPrimary
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BackgroundDark)
        ) {
            when (currentTab) {
                0 -> CalculatorTabContent(viewModel)
                1 -> RiskJournalTabContent(viewModel)
            }
        }
    }

    if (showCreateAccountDialog) {
        CreateAccountDialog(
            onDismiss = { showCreateAccountDialog = false },
            onCreate = { name, currency, balance, riskPlan, defaultRisk ->
                viewModel.createNewAccount(name, currency, balance, riskPlan, defaultRisk)
                showCreateAccountDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalculatorTabContent(viewModel: CalculatorViewModel) {
    val context = LocalContext.current
    val currentAsset by viewModel.selectedAsset.collectAsStateWithLifecycle()
    val balance by viewModel.accountBalance.collectAsStateWithLifecycle()
    val riskPercent by viewModel.riskPercentageInput.collectAsStateWithLifecycle()
    val riskAmountDirect by viewModel.riskAmountInput.collectAsStateWithLifecycle()
    val isDollarMode by viewModel.isDollarRiskMode.collectAsStateWithLifecycle()
    val stopLoss by viewModel.stopLossInput.collectAsStateWithLifecycle()
    val askPrice by viewModel.askPriceInput.collectAsStateWithLifecycle()
    val calcState by viewModel.calcState.collectAsStateWithLifecycle()

    val isAnalyzingImg by viewModel.isAnalyzingImage.collectAsStateWithLifecycle()
    val analysisReasoning by viewModel.imageAnalysisNotes.collectAsStateWithLifecycle()
    val uploadedBmp by viewModel.uploadedBitmap.collectAsStateWithLifecycle()

    var showAssetSearchDialog by remember { mutableStateOf(false) }
    var useAiMode by remember { mutableStateOf(false) }

    // Launcher for picking chart image screenshot from phone
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val bitmap = if (Build.VERSION.SDK_INT < 28) {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                } else {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                }
                viewModel.analyzeChartImageWithAI(bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Toggle Switcher between Manual Calc and AI Assistant Mode
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfacePanelDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!useAiMode) CrimsonPrimary else Color.Transparent)
                            .clickable { useAiMode = false }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Manual Calculator",
                            fontWeight = FontWeight.Bold,
                            color = if (!useAiMode) Color.White else TextSecondaryDark,
                            fontSize = 14.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (useAiMode) CrimsonPrimary else Color.Transparent)
                            .clickable { useAiMode = true }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "AI Mode",
                                tint = if (useAiMode) Color.White else TextSecondaryDark,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(end = 4.dp)
                            )
                            Text(
                                text = "AI Chart Assistant",
                                fontWeight = FontWeight.Bold,
                                color = if (useAiMode) Color.White else TextSecondaryDark,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        if (useAiMode) {
            // AI Screenshot Analysis Mode
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Analyze Chart Screen",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Upload a screenshot of your active trading chart (WTI, BTC, Gold, EUR/USD). Gemini will extract the pair, current price, entry target, and optimal stop loss automatically!",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryDark,
                            textAlign = TextAlign.Center
                        )

                        if (uploadedBmp != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, CrimsonAccent, RoundedCornerShape(8.dp))
                            ) {
                                Image(
                                    bitmap = uploadedBmp!!.asImageBitmap(),
                                    contentDescription = "Uploaded Chart",
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .background(Color.Black.copy(0.7f), RoundedCornerShape(4.dp))
                                        .clickable { imagePickerLauncher.launch("image/*") }
                                        .padding(6.dp)
                                ) {
                                    Text("Change Image", color = Color.White, fontSize = 11.sp)
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonAccent),
                                border = BorderStroke(1.dp, CrimsonAccent),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Upload")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Upload Chart Screenshot")
                            }
                        }

                        if (isAnalyzingImg) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(color = CrimsonAccent, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Gemini is reading chart geometry...", color = Color.White, fontSize = 14.sp)
                            }
                        }

                        if (analysisReasoning.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = BackgroundDark),
                                border = BorderStroke(1.dp, CrimsonDark)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, "OK", tint = TextGreenHighlight, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("AI Extracted Values", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = "Captured Asset: ${currentAsset.pairName} (${currentAsset.assetClass})", fontSize = 13.sp, color = TextPrimaryDark)
                                    Text(text = "Market Price: $askPrice | SL Target: $stopLoss", fontSize = 13.sp, color = TextPrimaryDark)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = "Rationale: $analysisReasoning", fontSize = 12.sp, color = TextSecondaryDark)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Searchable Asset Selector Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAssetSearchDialog = true },
                colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
                border = BorderStroke(1.dp, Color.DarkGray)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val icon = when (currentAsset.assetClass) {
                            "Crypto" -> Icons.Default.AddCircle
                            "Metals" -> Icons.Default.Star
                            "Oils" -> Icons.Default.Warning
                            "Stocks" -> Icons.Default.PlayArrow
                            else -> Icons.Default.Refresh
                        }
                        Icon(icon, contentDescription = "Asset", tint = CrimsonPrimary, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "Selected Trading Pair/Asset", fontSize = 12.sp, color = TextSecondaryDark)
                            Text(text = currentAsset.pairName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currentAsset.assetClass,
                            fontSize = 12.sp,
                            color = CrimsonAccent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(CrimsonDark.copy(0.3f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.KeyboardArrowRight, "More", tint = TextSecondaryDark)
                    }
                }
            }
        }

        // Inputs block: Account balance, Risk mode, Stop Loss
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCardDark)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Calculator Parameters",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondaryDark
                    )

                    // Balance Edit Field
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = balance,
                            onValueChange = { viewModel.accountBalance.value = it },
                            label = { Text("Account Balance ($/€)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CrimsonPrimary,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedLabelColor = CrimsonAccent,
                                unfocusedLabelColor = TextSecondaryDark
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        // Mode switcher for risk input (% or direct $)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(BackgroundDark, RoundedCornerShape(8.dp))
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (!isDollarMode) CrimsonPrimary else Color.Transparent)
                                    .clickable { viewModel.isDollarRiskMode.value = false }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("%", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isDollarMode) CrimsonPrimary else Color.Transparent)
                                    .clickable { viewModel.isDollarRiskMode.value = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("$", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Risk Value Field
                    if (!isDollarMode) {
                        OutlinedTextField(
                            value = riskPercent,
                            onValueChange = { viewModel.riskPercentageInput.value = it },
                            label = { Text("Risk Percentage (%)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            trailingIcon = { Text("%", color = CrimsonAccent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CrimsonPrimary,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedLabelColor = CrimsonAccent,
                                unfocusedLabelColor = TextSecondaryDark
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = riskAmountDirect,
                            onValueChange = { viewModel.riskAmountInput.value = it },
                            label = { Text("Direct Risk Amount ($)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            trailingIcon = { Text("$", color = CrimsonAccent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CrimsonPrimary,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedLabelColor = CrimsonAccent,
                                unfocusedLabelColor = TextSecondaryDark
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Dynamic Stop Loss Field
                    val isForex = currentAsset.assetClass == "Forex"
                    val labelText = if (isForex) "Stop Loss (pips)" else "Stop Loss Price Distance"
                    val unitLabel = if (isForex) "pips" else "points"

                    OutlinedTextField(
                        value = stopLoss,
                        onValueChange = { viewModel.stopLossInput.value = it },
                        label = { Text(labelText) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        trailingIcon = { Text(unitLabel, color = CrimsonAccent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CrimsonPrimary,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedLabelColor = CrimsonAccent,
                            unfocusedLabelColor = TextSecondaryDark
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quote Price Exchange Rate (Visible only when relevant for Forex Cross-Rates calculation)
                    if (isForex) {
                        OutlinedTextField(
                            value = askPrice,
                            onValueChange = { viewModel.askPriceInput.value = it },
                            label = { Text("Exchange/Asset Price (Standard Quote rate)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CrimsonPrimary,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedLabelColor = CrimsonAccent,
                                unfocusedLabelColor = TextSecondaryDark
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Live Risk Summary calculated locally in real-time
        item {
            val balVal = balance.toDoubleOrNull() ?: 10000.0
            val riskPercVal = riskPercent.toDoubleOrNull() ?: 1.0
            val riskAmtDirect = riskAmountDirect.toDoubleOrNull() ?: 0.0

            val currentLoggedRisk = if (isDollarMode) riskAmtDirect else balVal * (riskPercVal / 100.0)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfacePanelDark)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, "Risk Safety", tint = CrimsonAccent)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Expected Risk Allocation:", color = Color.White, fontSize = 14.sp)
                    }
                    Text(
                        text = "$ " + String.format("%,.2f", currentLoggedRisk),
                        color = CrimsonAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        // Large Premium Red Calculate button
        item {
            Button(
                onClick = { viewModel.calculatePositionSize() },
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "Calculate Position Size",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        // Results segment
        item {
            AnimatedVisibility(
                visible = calcState !is CalculatorUiState.Idle,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                when (val state = calcState) {
                    is CalculatorUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = CrimsonAccent)
                        }
                    }
                    is CalculatorUiState.Error -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CrimsonDark.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, CrimsonPrimary)
                        ) {
                            Text(
                                text = state.message,
                                color = Color.White,
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    is CalculatorUiState.Success -> {
                        CalculationResultsCard(results = state.results, onLogClicked = {
                            viewModel.logCalculatedTrade()
                        })
                    }
                    else -> {}
                }
            }
        }
    }

    if (showAssetSearchDialog) {
        AssetSearchDialog(
            onDismiss = { showAssetSearchDialog = false },
            onSelect = { asset ->
                viewModel.selectedAsset.value = asset
                showAssetSearchDialog = false
            }
        )
    }
}

@Composable
fun CalculationResultsCard(results: CalculationResults, onLogClicked: () -> Unit) {
    var hasLogged by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfacePanelDark),
        border = BorderStroke(1.dp, CrimsonPrimary.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Calculation Results",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Icon(Icons.Default.Check, "Success", tint = TextGreenHighlight)
            }

            Divider(color = Color.DarkGray)

            // Lot Breakdown Columns
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ResultItemRow(
                    label = "Amount at Risk",
                    value = "$ " + String.format("%,.2f", results.riskAmount),
                    highlightColor = CrimsonAccent
                )

                ResultItemRow(
                    label = "Total Position Size (${results.unitName})",
                    value = String.format("%,.4f", results.positionSizeUnits),
                    highlightColor = Color.White
                )

                ResultItemRow(
                    label = "Standard Lots (100k Units)",
                    value = String.format("%,.3f Lots", results.standardLots),
                    highlightColor = Color.White
                )

                ResultItemRow(
                    label = "Mini Lots (10k Units)",
                    value = String.format("%,.3f Lots", results.miniLots),
                    highlightColor = Color.White
                )

                ResultItemRow(
                    label = "Micro Lots (1k Units)",
                    value = String.format("%,.3f Lots", results.microLots),
                    highlightColor = Color.White
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action: Save to active account trade journal
            Button(
                onClick = {
                    onLogClicked()
                    hasLogged = true
                },
                enabled = !hasLogged,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hasLogged) Color.DarkGray else CrimsonAccent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.List, contentDescription = "Log Trade")
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (hasLogged) "Recorded in Journal!" else "Record & Log Trade")
            }
        }
    }
}

@Composable
fun ResultItemRow(label: String, value: String, highlightColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextSecondaryDark, fontSize = 13.sp)
        Text(text = value, color = highlightColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RiskJournalTabContent(viewModel: CalculatorViewModel) {
    val activeAcc by viewModel.activeAccount.collectAsStateWithLifecycle()
    val logs by viewModel.tradeLogs.collectAsStateWithLifecycle()
    val isAnalyzingPlan by viewModel.isAnalyzingRisk.collectAsStateWithLifecycle()
    val adviceText by viewModel.riskRecommendationText.collectAsStateWithLifecycle()
    val complianceRating by viewModel.complianceStatus.collectAsStateWithLifecycle()

    var editingPlanText by remember { mutableStateOf("") }
    var isEditingPlan by remember { mutableStateOf(false) }

    LaunchedEffect(activeAcc) {
        activeAcc?.let {
            editingPlanText = it.riskPlanDescription
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // AI Risk Coach / Advisor audit
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
                border = BorderStroke(1.dp, CrimsonAccent.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, "AI Assistant", tint = CrimsonAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AI Risk Plan Coach", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                        }

                        if (complianceRating.isNotEmpty()) {
                            Text(
                                text = complianceRating,
                                color = if (complianceRating == "Fully Compliant") TextGreenHighlight else CrimsonAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .background(Color.Black.copy(0.4f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = "Let Gemini analyze your local risk strategies and recent trades to ensure full performance compliance. It prevents traders from over-leveraging and emotional revenge trading!",
                        fontSize = 12.sp,
                        color = TextSecondaryDark
                    )

                    if (isAnalyzingPlan) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(color = CrimsonAccent, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analyzing trade logs...", color = Color.White, fontSize = 13.sp)
                        }
                    }

                    if (adviceText.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BackgroundDark, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(text = adviceText, fontSize = 13.sp, color = TextPrimaryDark)
                        }
                    }

                    Button(
                        onClick = { viewModel.auditRiskWithAI() },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Get Smart AI Risk suggestion")
                    }
                }
            }
        }

        // Account Strategy text Editor Segment
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCardDark)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("My Risk Strategy Plan", fontWeight = FontWeight.Bold, color = Color.White)

                        Text(
                            text = if (isEditingPlan) "Save Plan" else "Edit Rules",
                            color = CrimsonAccent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    if (isEditingPlan) {
                                        viewModel.updateRiskPlan(editingPlanText)
                                    }
                                    isEditingPlan = !isEditingPlan
                                }
                                .padding(4.dp)
                        )
                    }

                    if (isEditingPlan) {
                        OutlinedTextField(
                            value = editingPlanText,
                            onValueChange = { editingPlanText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CrimsonPrimary,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = TextPrimaryDark
                            )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BackgroundDark, RoundedCornerShape(6.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = activeAcc?.riskPlanDescription ?: "No strategic guidelines written yet. Press edit to define thresholds.",
                                fontSize = 13.sp,
                                color = TextPrimaryDark
                            )
                        }
                    }
                }
            }
        }

        // Historical Trade logs header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Historical Calculated Trades",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )

                Text(
                    text = "${logs.size} trades",
                    fontSize = 12.sp,
                    color = TextSecondaryDark
                )
            }
        }

        if (logs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Info, "Empty", tint = Color.DarkGray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No trades calculated/logged yet on this account.", color = TextSecondaryDark, fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(logs) { log ->
                TradeLogItemCard(log = log, onStatusChanged = { status ->
                    viewModel.updateTradeOutcome(log, status)
                }, onDelete = {
                    viewModel.deleteTradeLog(log)
                })
            }
        }
    }
}

@Composable
fun TradeLogItemCard(log: TradeLog, onStatusChanged: (String) -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCardDark)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = log.pairName,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                    Text(
                        text = log.assetClass,
                        fontSize = 11.sp,
                        color = TextSecondaryDark
                    )
                }

                // Outcome status tag
                val tagColor = when (log.outcome) {
                    "WIN" -> TextGreenHighlight
                    "LOSS" -> TextRedHighlight
                    else -> Color.DarkGray
                }

                Box(
                    modifier = Modifier
                        .background(tagColor.copy(0.15f), RoundedCornerShape(4.dp))
                        .border(1.dp, tagColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = log.outcome,
                        color = tagColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            Divider(color = Color.DarkGray.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Risk Amount", fontSize = 11.sp, color = TextSecondaryDark)
                    Text("$ " + String.format("%.2f", log.riskAmount), color = CrimsonAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("SL Target", fontSize = 11.sp, color = TextSecondaryDark)
                    Text("${log.stopLoss}", color = Color.White, fontSize = 13.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Size units", fontSize = 11.sp, color = TextSecondaryDark)
                    Text(String.format("%,.4f", log.calculatedSize), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Actions: Change outcome status
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Mark Win",
                        fontSize = 11.sp,
                        color = TextGreenHighlight,
                        modifier = Modifier
                            .background(Color.Black.copy(0.4f), RoundedCornerShape(4.dp))
                            .clickable { onStatusChanged("WIN") }
                            .padding(6.dp)
                    )
                    Text(
                        text = "Mark Loss",
                        fontSize = 11.sp,
                        color = TextRedHighlight,
                        modifier = Modifier
                            .background(Color.Black.copy(0.4f), RoundedCornerShape(4.dp))
                            .clickable { onStatusChanged("LOSS") }
                            .padding(6.dp)
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, "Delete", tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun AssetSearchDialog(onDismiss: () -> Unit, onSelect: (TradingAsset) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = listOf("All", "Forex", "Crypto", "Metals", "Oils", "Stocks")

    val filteredList = AssetCatalog.allAssets.filter {
        (selectedCategory == "All" || it.assetClass.equals(selectedCategory, ignoreCase = true)) &&
        it.pairName.contains(searchQuery, ignoreCase = true)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            colors = CardDefaults.cardColors(containerColor = SurfacePanelDark),
            border = BorderStroke(1.dp, CrimsonAccent)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Select Trading Asset",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search tickers (e.g. BTC, Gold, EUR)") },
                    leadingIcon = { Icon(Icons.Default.Search, "Search", tint = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonAccent,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = TextPrimaryDark
                    )
                )

                // Segment categories scrollable row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (selectedCategory == cat) CrimsonPrimary else BackgroundDark)
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = cat, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Divider(color = Color.DarkGray)

                // Asset items list
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (filteredList.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No matching assets found.", color = TextSecondaryDark)
                                }
                            }
                        } else {
                            items(filteredList) { asset ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelect(asset) }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(text = asset.pairName, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text(text = "Contract size: ${asset.contractSize}", fontSize = 11.sp, color = TextSecondaryDark)
                                    }
                                    Text(
                                        text = asset.assetClass,
                                        fontSize = 11.sp,
                                        color = CrimsonAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Divider(color = Color.DarkGray.copy(alpha = 0.3f))
                            }
                        }
                    }
                }

                // Close Button
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close", color = CrimsonAccent)
                }
            }
        }
    }
}

@Composable
fun CreateAccountDialog(onDismiss: () -> Unit, onCreate: (String, String, Double, String, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("USD") }
    var balance by remember { mutableStateOf("10000") }
    var riskPlan by remember { mutableStateOf("") }
    var defaultRiskPercentage by remember { mutableStateOf("1.0") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfacePanelDark),
            border = BorderStroke(1.dp, CrimsonAccent)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Create New Portfolio Account",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account Portfolio Name") },
                    placeholder = { Text("e.g. My $50k Prop Challenge") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonPrimary,
                        unfocusedBorderColor = Color.DarkGray
                    )
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it },
                        label = { Text("Currency") },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CrimsonPrimary,
                            unfocusedBorderColor = Color.DarkGray
                        )
                    )

                    OutlinedTextField(
                        value = balance,
                        onValueChange = { balance = it },
                        label = { Text("Start Balance") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1.5f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CrimsonPrimary,
                            unfocusedBorderColor = Color.DarkGray
                        )
                    )
                }

                OutlinedTextField(
                    value = defaultRiskPercentage,
                    onValueChange = { defaultRiskPercentage = it },
                    label = { Text("Default Risk Percent (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonPrimary,
                        unfocusedBorderColor = Color.DarkGray
                    )
                )

                OutlinedTextField(
                    value = riskPlan,
                    onValueChange = { riskPlan = it },
                    label = { Text("Risk Plan Guidelines description") },
                    placeholder = { Text("Write your limits: e.g. Cut size if drawdown exceeds 3%. Max daily limit 1%.") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonPrimary,
                        unfocusedBorderColor = Color.DarkGray
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                val b = balance.toDoubleOrNull() ?: 10000.0
                                val r = defaultRiskPercentage.toDoubleOrNull() ?: 1.0
                                onCreate(name, currency, b, riskPlan, r)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                    ) {
                        Text("Create")
                    }
                }
            }
        }
    }
}
