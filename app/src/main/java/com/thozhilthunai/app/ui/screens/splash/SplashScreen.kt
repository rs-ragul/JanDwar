package com.thozhilthunai.app.ui.screens.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thozhilthunai.app.R
import com.thozhilthunai.app.ui.components.brandGradient
import com.thozhilthunai.app.ui.theme.ThozhilThunaiTheme
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onDone: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.7f) }

    LaunchedEffect(Unit) {
        launch {
            alpha.animateTo(1f, animationSpec = tween(800, easing = EaseOutCubic))
        }
        scale.animateTo(
            1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        )
        delay(1800)
        onDone()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = brandGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_premium),
                contentDescription = "ThozhilThunai Logo",
                modifier = Modifier
                    .size(140.dp)
                    .scale(scale.value)
                    .alpha(alpha.value)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "தொழில்துணை",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp
                ),
                modifier = Modifier.alpha(alpha.value)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "ThozhilThunai",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = Color.White.copy(alpha = 0.85f)
                ),
                modifier = Modifier.alpha(alpha.value)
            )
        }
    }
}

@Preview
@Composable
private fun SplashPreview() {
    ThozhilThunaiTheme { SplashScreen(onDone = {}) }
}
