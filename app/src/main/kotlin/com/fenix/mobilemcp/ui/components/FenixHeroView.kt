package com.fenix.mobilemcp.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun FenixHeroView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        FenixLogoWithFlames()
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "El poder MCP en tus manos",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        AnimatedTitle()
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TypewriterText(
            text = "¡Bienvenido! Aquí estoy para ayudarte a convertir tus ideas en realidad, tienes múltiples herramientas así como la capacidad de conectarte a otras aplicaciones de inteligencia artificial, hacer trabajos directamente en tu celular, controlar la pantalla, abrir aplicaciones, gestionar tus cuentas, etc. Puedes personalizarme y hacer un sinfín de cosas. ¿Por dónde empezamos?"
        )
    }
}

@Composable
fun FenixLogoWithFlames() {
    val infiniteTransition = rememberInfiniteTransition(label = "flames")
    
    // Pulsating glow
    val glowRadius by infiniteTransition.animateFloat(
        initialValue = 40f,
        targetValue = 65f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    // Fire particles
    val particleCount = 20
    val particles = remember {
        List(particleCount) {
            Particle(
                x = Random.nextFloat(),
                speed = Random.nextFloat() * 1.5f + 0.5f,
                size = Random.nextFloat() * 6f + 3f,
                alpha = Random.nextFloat() * 0.8f + 0.2f
            )
        }
    }
    
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particles"
    )

    Box(
        modifier = Modifier.size(130.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            
            // Draw glowing halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFF7043).copy(alpha = 0.4f),
                        Color(0xFFD84315).copy(alpha = 0.1f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = glowRadius * dp.toPx()
                ),
                radius = glowRadius * dp.toPx(),
                center = center,
                blendMode = BlendMode.Screen
            )
            
            // Draw particles (upward movement)
            particles.forEach { p ->
                val progress = (time * p.speed) % 100f
                val yOffset = size.height - (progress / 100f) * size.height
                val xOffset = p.x * size.width + kotlin.math.sin(progress * 0.1f + p.x * 10f) * 15f
                
                val particleColor = if (progress < 30f) Color(0xFFFFD54F) 
                                   else if (progress < 70f) Color(0xFFFF7043)
                                   else Color(0xFFD84315)
                
                drawCircle(
                    color = particleColor.copy(alpha = p.alpha * (1f - progress / 100f)),
                    radius = p.size,
                    center = Offset(xOffset, yOffset)
                )
            }
        }
        
        // Logo Core
        Canvas(modifier = Modifier.size(76.dp)) {
            val gradient = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFD54F),
                    Color(0xFFFF7043),
                    Color(0xFFD84315)
                )
            )
            drawCircle(
                brush = gradient,
                radius = size.width / 2
            )
        }
        
        Icon(
            imageVector = Icons.Default.LocalFireDepartment,
            contentDescription = "Fénix IA Logo",
            modifier = Modifier.size(42.dp),
            tint = Color.White
        )
    }
}

@Composable
fun AnimatedTitle() {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(300) // Small delay for effect
        visible = true
    }
    
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.5f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "titleScale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500),
        label = "titleAlpha"
    )
    
    Text(
        text = "Fénix IA MCP",
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .scale(scale)
            .alpha(alpha)
    )
}

@Composable
fun TypewriterText(text: String) {
    var displayedText by remember { mutableStateOf("") }
    
    LaunchedEffect(text) {
        delay(800) // Wait for title animation
        for (i in text.indices) {
            displayedText = text.substring(0, i + 1)
            delay(20) // Typewriter speed (20ms per character)
        }
    }
    
    Text(
        text = displayedText,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = 24.sp
    )
}

data class Particle(
    val x: Float,
    val speed: Float,
    val size: Float,
    val alpha: Float
)
