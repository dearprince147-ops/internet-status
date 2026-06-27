package com.example

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

enum class AppTheme { LIGHT, TRUE_BLACK }

@Composable
fun animateColorScheme(
    targetColorScheme: ColorScheme,
    animationSpec: AnimationSpec<Color> = tween(500)
): ColorScheme {
    val background by animateColorAsState(targetColorScheme.background, animationSpec, label = "bg")
    val surface by animateColorAsState(targetColorScheme.surface, animationSpec, label = "surface")
    val onBackground by animateColorAsState(targetColorScheme.onBackground, animationSpec, label = "onBg")
    val onSurface by animateColorAsState(targetColorScheme.onSurface, animationSpec, label = "onSurface")
    val primary by animateColorAsState(targetColorScheme.primary, animationSpec, label = "primary")
    val onPrimary by animateColorAsState(targetColorScheme.onPrimary, animationSpec, label = "onPrimary")
    val onSurfaceVariant by animateColorAsState(targetColorScheme.onSurfaceVariant, animationSpec, label = "onSurfaceVariant")
    
    return targetColorScheme.copy(
        background = background,
        surface = surface,
        onBackground = onBackground,
        onSurface = onSurface,
        primary = primary,
        onPrimary = onPrimary,
        onSurfaceVariant = onSurfaceVariant
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("ServicePrefs", Context.MODE_PRIVATE)

        setContent {
            var currentTheme by remember { 
                mutableStateOf(AppTheme.values()[prefs.getInt("current_theme", AppTheme.TRUE_BLACK.ordinal)]) 
            }
            var isServiceEnabled by remember { 
                mutableStateOf(prefs.getBoolean("service_enabled", false)) 
            }
            var isConnected by remember { mutableStateOf(prefs.getBoolean("last_known_status", false)) }

            // Broadcast Receiver for internet status
            DisposableEffect(Unit) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        isConnected = intent?.getBooleanExtra("status", false) ?: false
                    }
                }
                val filter = IntentFilter("com.example.INTERNET_STATUS_UPDATE")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(receiver, filter)
                }
                onDispose {
                    unregisterReceiver(receiver)
                }
            }

            val targetColorScheme = when (currentTheme) {
                AppTheme.LIGHT -> lightColorScheme(
                    background = Color(0xFFFEF7FF),
                    surface = Color(0xFFFEF7FF),
                    onBackground = Color(0xFF1D1B20),
                    onSurface = Color(0xFF1D1B20),
                    primary = Color(0xFF6750A4),
                    onPrimary = Color(0xFFFFFFFF),
                    onSurfaceVariant = Color(0xFF49454F)
                )
                AppTheme.TRUE_BLACK -> darkColorScheme(
                    background = Color(0xFF000000),
                    surface = Color(0xFF000000),
                    onBackground = Color(0xFFFFFFFF),
                    onSurface = Color(0xFFFFFFFF),
                    primary = Color(0xFFD0BCFF),
                    onPrimary = Color(0xFF381E72),
                    onSurfaceVariant = Color(0xFFAAAAAA)
                )
            }
            
            val animatedColorScheme = animateColorScheme(targetColorScheme)

            MaterialTheme(colorScheme = animatedColorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    InternetStatusScreen(
                        theme = currentTheme,
                        onThemeChange = {
                            val next = AppTheme.values()[(currentTheme.ordinal + 1) % 2]
                            prefs.edit().putInt("current_theme", next.ordinal).apply()
                            currentTheme = next
                        },
                        isServiceEnabled = isServiceEnabled,
                        onToggleService = {
                            isServiceEnabled = !isServiceEnabled
                            prefs.edit().putBoolean("service_enabled", isServiceEnabled).apply()
                            
                            if (isServiceEnabled) {
                                checkPermissionsAndStart()
                            } else {
                                stopService(Intent(this@MainActivity, InternetStatusService::class.java))
                                isConnected = false
                            }
                        },
                        isConnected = isConnected
                    )
                }
            }
        }
        
        if (prefs.getBoolean("service_enabled", false)) {
            checkPermissionsAndStart()
        }
    }

    private fun checkPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
                return
            }
        }
        startInternetService()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            val prefs = getSharedPreferences("ServicePrefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("service_enabled", false)) {
                startInternetService()
            }
        }
    }

    private fun startInternetService() {
        val intent = Intent(this, InternetStatusService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

@Composable
fun InternetStatusScreen(
    theme: AppTheme,
    onThemeChange: () -> Unit,
    isServiceEnabled: Boolean,
    onToggleService: () -> Unit,
    isConnected: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .windowInsetsPadding(WindowInsets.systemBars),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
            val themeInteractionSource = remember { MutableInteractionSource() }
            val isThemePressed by themeInteractionSource.collectIsPressedAsState()
            val themeScale by animateFloatAsState(targetValue = if (isThemePressed) 0.8f else 1f, label = "themeScale")
            
            IconButton(
                onClick = onThemeChange,
                interactionSource = themeInteractionSource,
                modifier = Modifier.graphicsLayer {
                    scaleX = themeScale
                    scaleY = themeScale
                }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_theme_toggle), 
                    contentDescription = "Toggle Theme",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Internet Status",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.weight(1f))

        // Status Dot
        val targetDotColor = if (!isServiceEnabled) Color.Gray else if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336)
        val animatedDotColor by animateColorAsState(
            targetValue = targetDotColor, 
            animationSpec = tween(500), 
            label = "dotColor"
        )
        
        // Pulse Animation
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = if (isServiceEnabled) 0.3f else 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )

        Box(
            modifier = Modifier
                .size(120.dp)
                .alpha(alpha)
                .background(color = animatedDotColor, shape = CircleShape)
        )

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedContent(
            targetState = if (!isServiceEnabled) "Service Inactive" else if (isConnected) "Internet: Connected ✓" else "Internet: No Access ✗",
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "statusTextAnimation"
        ) { text ->
            Text(
                text = text,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        val btnInteractionSource = remember { MutableInteractionSource() }
        val isBtnPressed by btnInteractionSource.collectIsPressedAsState()
        val btnScale by animateFloatAsState(targetValue = if (isBtnPressed) 0.95f else 1f, label = "btnScale")
        
        val targetBtnContainerColor = if (isServiceEnabled) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary
        val targetBtnContentColor = if (isServiceEnabled) Color.White else MaterialTheme.colorScheme.onPrimary
        
        val animatedBtnContainerColor by animateColorAsState(targetValue = targetBtnContainerColor, animationSpec = tween(300), label = "btnContainerColor")
        val animatedBtnContentColor by animateColorAsState(targetValue = targetBtnContentColor, animationSpec = tween(300), label = "btnContentColor")

        Button(
            onClick = onToggleService,
            interactionSource = btnInteractionSource,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .graphicsLayer {
                    scaleX = btnScale
                    scaleY = btnScale
                },
            colors = ButtonDefaults.buttonColors(
                containerColor = animatedBtnContainerColor,
                contentColor = animatedBtnContentColor
            )
        ) {
            AnimatedContent(
                targetState = if (isServiceEnabled) "Deactivate" else "Activate",
                transitionSpec = {
                    (slideInVertically { height -> height } + fadeIn(tween(300))).togetherWith(slideOutVertically { height -> -height } + fadeOut(tween(300)))
                },
                label = "btnTextAnimation"
            ) { text ->
                Text(
                    text = text,
                    fontSize = 18.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
    }
}
