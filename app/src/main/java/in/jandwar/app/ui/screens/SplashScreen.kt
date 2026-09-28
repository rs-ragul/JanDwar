package `in`.jandwar.app.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.jandwar.app.R
import `in`.jandwar.app.ui.components.brandBrush
import `in`.jandwar.app.ui.viewmodel.AppViewModel
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    viewModel: AppViewModel,
    onFinished: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.82f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(520),
        label = "alpha"
    )

    LaunchedEffect(Unit) {
        visible = true
        delay(1650)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brandBrush()),
        contentAlignment = Alignment.Center
    ) {
        // Soft light bloom behind the emblem.
        Box(
            modifier = Modifier
                .size(340.dp)
                .alpha(alpha * 0.55f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.22f), Color.Transparent)
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .scale(scale)
                    .alpha(alpha)
                    .clip(RoundedCornerShape(38.dp))
                    .background(Color.White.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_brand_emblem),
                    contentDescription = null,
                    modifier = Modifier.size(84.dp)
                )
            }

            Spacer(Modifier.height(26.dp))

            Text(
                text = viewModel.tr("name"),
                color = Color.White,
                fontSize = 40.sp,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.alpha(alpha)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = viewModel.tr("tagline"),
                color = Color.White.copy(alpha = 0.88f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(alpha)
            )

            Spacer(Modifier.height(44.dp))

            LinearProgressIndicator(
                modifier = Modifier
                    .width(130.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .alpha(alpha),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
                strokeCap = StrokeCap.Round
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp, start = 32.dp, end = 32.dp)
                .alpha(alpha),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "PM-AJAY · Grant-in-Aid Component",
                color = Color.White.copy(alpha = 0.80f),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "NSQF-aligned skilling for SC communities",
                color = Color.White.copy(alpha = 0.62f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )
        }
    }
}
