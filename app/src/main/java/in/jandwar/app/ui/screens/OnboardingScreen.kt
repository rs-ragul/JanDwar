package `in`.jandwar.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.jandwar.app.ui.components.GradientHeader
import `in`.jandwar.app.ui.components.PremiumCard
import `in`.jandwar.app.ui.theme.*
import `in`.jandwar.app.ui.viewmodel.AppViewModel

@Composable
fun OnboardingScreen(
    viewModel: AppViewModel,
    onFinish: () -> Unit
) {
    val page = viewModel.onboardingPage

    val titles = listOf(
        viewModel.tr("voice_intro"),
        viewModel.tr("fundable_badge"),
        viewModel.tr("centre")
    )
    val bodies = listOf(
        viewModel.tr("on_0"),
        viewModel.tr("on_1"),
        viewModel.tr("on_2")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp)
    ) {
        GradientHeader(
            title = "JanDwar",
            subtitle = viewModel.tr("tagline"),
            withLogo = true
        )
        Spacer(Modifier.height(20.dp))

        PremiumCard {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (page == 1) BrandSaffron else BrandTeal,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        "0${page + 1}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                titles.getOrElse(page) { "Welcome" },
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                bodies.getOrElse(page) { "" },
                fontSize = 17.sp,
                color = Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp, start = 4.dp, end = 4.dp)
            )
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            for (i in 0..2) {
                Text(
                    if (i == page) "●" else "○",
                    fontSize = 26.sp,
                    color = if (i == page) BrandTeal else Color(0xFFB9C3D2),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Button(
            onClick = {
                if (page < 2) viewModel.nextOnboarding()
                else onFinish()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo)
        ) {
            Text(
                if (page == 2) viewModel.tr("start") else viewModel.tr("continue"),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
