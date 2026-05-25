package com.bztzr.jetfitness

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import androidx.compose.ui.platform.LocalLocale
import kotlinx.coroutines.launch

sealed class Screen(val title: String, val icon: ImageVector) {
    object Home : Screen("Трекер", Icons.Default.DirectionsRun)
    object History : Screen("История", Icons.Default.DateRange)
    object Settings : Screen("Настройки", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainApp()
                }
            }
        }
    }
}

@Composable
fun MainApp() {
    val context = LocalContext.current
    val dataManager = remember { DataManager(context) }
    val scope = rememberCoroutineScope()

    val isConfigured by dataManager.isConfiguredFlow.collectAsState(initial = false)

    if (!isConfigured) {
        OnboardingScreen(
            onSave = { weight, height ->
                scope.launch {
                    dataManager.saveUserSettings(weight, height)
                }
            }
        )
        return
    }

    MainApplicationContent(dataManager)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApplicationContent(dataManager: DataManager) {
    val context = LocalContext.current
    val trackerViewModel: TrackerViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return TrackerViewModel(dataManager) as T
            }
        }
    )

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    val scope = rememberCoroutineScope()

    val totalDailySteps by dataManager.totalStepsFlow.collectAsState(initial = 0)
    val todaySessions by dataManager.todaySessionsFlow.collectAsState(initial = emptyList())
    val historyList by dataManager.historyFlow.collectAsState(initial = emptyList())

    val userWeight by dataManager.userWeightFlow.collectAsState(initial = 70f)
    val userHeight by dataManager.userHeightFlow.collectAsState(initial = 175f)

    LaunchedEffect(Unit) {
        dataManager.initDay()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    SensorHandler(
        context = context,
        isTracking = trackerViewModel.isTracking,
        onStepsUpdate = { value ->
            trackerViewModel.updateSessionSteps(value)
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = currentScreen.title) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        },
        bottomBar = {
            NavigationBar {
                listOf(Screen.Home, Screen.History, Screen.Settings).forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.title) },
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen }
                    )
                }
            }
        }
    ) { paddingValues ->
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "ScreenTransition"
        ) { targetScreen ->
            Box(modifier = Modifier.padding(paddingValues)) {
                when (targetScreen) {
                    is Screen.Home -> FitnessTrackerScreen(
                        totalDailySteps = totalDailySteps,
                        viewModel = trackerViewModel,
                        userWeight = userWeight,
                        userHeight = userHeight
                    )
                    is Screen.History -> HistoryScreen(todaySessions = todaySessions, historyList = historyList)
                    is Screen.Settings -> SettingsScreen(
                        initialWeight = userWeight,
                        initialHeight = userHeight,
                        onSave = { w, h ->
                            scope.launch {
                                dataManager.saveUserSettings(w, h)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun OnboardingScreen(onSave: (Float, Float) -> Unit) {
    var weight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Добро пожаловать!",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Для точного расчета калорий и дистанции,\nукажите ваши параметры:",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        OutlinedTextField(
            value = weight,
            onValueChange = { weight = it; isError = false },
            label = { Text("Вес (кг)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = isError && weight.isEmpty()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = height,
            onValueChange = { height = it; isError = false },
            label = { Text("Рост (см)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = isError && height.isEmpty()
        )

        if (isError) {
            Text("Пожалуйста, заполните все поля", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (weight.isNotEmpty() && height.isNotEmpty()) {
                    val w = weight.toFloatOrNull() ?: 70f
                    val h = height.toFloatOrNull() ?: 175f
                    onSave(w, h)
                } else {
                    isError = true
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text("Начать использование", fontSize = 18.sp)
        }
    }
}

@Composable
fun FitnessTrackerScreen(
    totalDailySteps: Int,
    viewModel: TrackerViewModel,
    userWeight: Float,
    userHeight: Float
) {
    val animatedSteps by animateIntAsState(targetValue = viewModel.sessionSteps, label = "StepsAnim")
    val animatedTotal by animateIntAsState(targetValue = totalDailySteps, label = "TotalAnim")

    val stepLengthMeters = (userHeight * 0.415) / 100.0
    val totalDistToday = (totalDailySteps * stepLengthMeters) / 1000.0
    val totalCalsToday = (0.5 * userWeight * totalDistToday).toInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Всего за сегодня", fontSize = 18.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$animatedTotal",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(text = "шагов", fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.LocationOn,
                value = String.format(LocalLocale.current.platformLocale, "%.2f", totalDistToday),
                label = "км сегодня",
                color = MaterialTheme.colorScheme.secondaryContainer
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.LocalFireDepartment,
                value = "$totalCalsToday",
                label = "ккал сегодня",
                color = MaterialTheme.colorScheme.tertiaryContainer
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Текущая прогулка", fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "$animatedSteps",
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(text = "шагов", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(16.dp))

        val sessionDist = (viewModel.sessionSteps * stepLengthMeters) / 1000.0
        val sessionCals = (0.5 * userWeight * sessionDist).toInt()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "${String.format(LocalLocale.current.platformLocale, "%.2f", sessionDist)} км", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = "•", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = "$sessionCals ккал", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (!viewModel.isTracking) viewModel.startTracking()
                else viewModel.stopTracking(userWeight, userHeight)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (viewModel.isTracking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            ),
            shape = MaterialTheme.shapes.large
        ) {
            Text(
                text = if (viewModel.isTracking) "ЗАКОНЧИТЬ И СОХРАНИТЬ" else "НАЧАТЬ ПРОГУЛКУ",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (viewModel.isTracking) {
            Text(text = "● Идет запись...", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SettingsScreen(
    initialWeight: Float,
    initialHeight: Float,
    onSave: (Float, Float) -> Unit
) {
    var weight by remember { mutableStateOf(initialWeight.toString()) }
    var height by remember { mutableStateOf(initialHeight.toString()) }
    var isChanged by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(text = "Настройки профиля", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))

        OutlinedTextField(
            value = weight,
            onValueChange = { weight = it; isChanged = true },
            label = { Text("Вес (кг)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = height,
            onValueChange = { height = it; isChanged = true },
            label = { Text("Рост (см)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val w = weight.toFloatOrNull() ?: 70f
                val h = height.toFloatOrNull() ?: 175f
                onSave(w, h)
                isChanged = false
            },
            enabled = isChanged,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Сохранить")
        }

        Text(
            text = "Эти данные используются для более точного расчета калорий и дистанции.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
fun SensorHandler(
    context: Context,
    isTracking: Boolean,
    onStepsUpdate: (Int) -> Unit
) {
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    DisposableEffect(isTracking) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && isTracking) {
                    onStepsUpdate(event.values[0].toInt())
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (isTracking) sensorManager.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_UI)
        else sensorManager.unregisterListener(listener)

        onDispose { sensorManager.unregisterListener(listener) }
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, icon: ImageVector, value: String, label: String, color: androidx.compose.ui.graphics.Color) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(text = label, fontSize = 12.sp)
        }
    }
}

@Composable
fun HistoryScreen(todaySessions: List<String>, historyList: List<String>) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        if (todaySessions.isNotEmpty()) {
            item {
                Text(text = "Сегодняшние прогулки:", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            }
            items(todaySessions.reversed()) { session ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(text = session, modifier = Modifier.padding(16.dp), fontSize = 14.sp)
                }
            }
            item { Divider(modifier = Modifier.padding(vertical = 12.dp)) }
        }

        if (historyList.isNotEmpty()) {
            item {
                Text(text = "Архив дней:", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            }
            items(historyList.reversed()) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Text(text = item, modifier = Modifier.padding(16.dp), fontSize = 14.sp)
                }
            }
        }

        if (todaySessions.isEmpty() && historyList.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text(text = "История пуста", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}