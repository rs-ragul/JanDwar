package com.thozhilthunai.app.ui.screens.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thozhilthunai.app.ui.AppViewModel
import com.thozhilthunai.app.ui.components.PageIndicator
import com.thozhilthunai.app.ui.components.brandGradient
import com.thozhilthunai.app.ui.theme.BrandSaffron
import com.thozhilthunai.app.ui.theme.BrandTeal

data class OnboardingPage(
    val icon: ImageVector,
    val titleKey: String,
    val bodyKey: String,
    val iconTint: Color
)

@Composable
fun OnboardingScreen(
    appViewModel: AppViewModel,
    onDone: () -> Unit
) {
    val pages = listOf(
        OnboardingPage(
            Icons.Filled.School,
            "onboard_title_1", "onboard_body_1",
            BrandTeal
        ),
        OnboardingPage(
            Icons.Filled.Search,
            "onboard_title_2", "onboard_body_2",
            BrandSaffron
        ),
        OnboardingPage(
            Icons.Filled.Verified,
            "onboard_title_3", "onboard_body_3",
            Color(0xFF4CAF50)
        )
    )

    // Fallback strings (keys not in i18n.json for onboarding — use embedded English)
    val pageTitles = listOf(
        "Discover Government Courses",
        "Answer 3 Quick Questions",
        "Find Courses + Training Centres"
    )
    val pageBodies = listOf(
        "516 real NSQF-certified job roles from Tamil Nadu — verified, offline, free to browse.",
        "Tell us your education level, district, and what work interests you.",
        "Get a ranked list of roles you qualify for, with the nearest verified training centres."
    )

    var currentPage by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = brandGradient)
    ) {
        // Skip button
        TextButton(
            onClick = onDone,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Text("Skip", color = Color.White.copy(alpha = 0.8f))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(300)
                    ) togetherWith slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = tween(300)
                    )
                }, label = "onboardingContent"
            ) { page ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = RoundedCornerShape(32.dp),
                        color = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.size(120.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = pages[page].icon,
                                contentDescription = null,
                                tint = pages[page].iconTint,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = pageTitles[page],
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = pageBodies[page],
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = Color.White.copy(alpha = 0.85f)
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            PageIndicator(count = pages.size, current = currentPage)

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (currentPage < pages.size - 1) currentPage++
                    else onDone()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandSaffron,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (currentPage < pages.size - 1) "Next →" else "Get Started!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
