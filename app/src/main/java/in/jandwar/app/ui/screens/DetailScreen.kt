package `in`.jandwar.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.jandwar.app.ui.components.GradientHeader
import `in`.jandwar.app.ui.components.PremiumCard
import `in`.jandwar.app.ui.theme.*
import `in`.jandwar.app.ui.viewmodel.AppViewModel

@Composable
fun DetailScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val rec = viewModel.selectedRole
    val context = LocalContext.current

    if (rec == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("No role selected")
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        GradientHeader(
            title = viewModel.tr("details"),
            subtitle = "${viewModel.profile.district} • ${viewModel.profile.familyOccupation}",
            actionText = viewModel.tr("back"),
            onAction = onBack
        )
        Spacer(Modifier.height(16.dp))

        Text(rec.role.job_role, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text("${rec.role.qp_code} · ${rec.role.ssc} · ${rec.role.sector}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(10.dp))

        PremiumCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoLine(viewModel.tr("course_type"), if (rec.role.isLongTerm()) viewModel.tr("long_term") else viewModel.tr("short_term"))
                InfoLine(viewModel.tr("outcome"), if (viewModel.profile.preference?.key == "pref_self") viewModel.tr("pref_self") else viewModel.tr("pref_wage"))
                InfoLine(viewModel.tr("fundable"), if (rec.role.isFundable()) viewModel.tr("fundable_badge") else viewModel.tr("not_fundable"))
                InfoLine(viewModel.tr("asset"), viewModel.tr("asset_rule"))
                InfoLine(viewModel.tr("why"), rec.reason)
                if (rec.familyFitNote.isNotBlank()) {
                    InfoLine("Family Fit (PM-AJAY)", rec.familyFitNote)
                }
                InfoLine(viewModel.tr("skill_gap"), rec.skillGapNote)
                if (rec.regionOpportunity.isNotBlank()) {
                    InfoLine("Local Opportunity", rec.regionOpportunity)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Profile context per problem statement
        PremiumCard {
            Text("Your Profile Summary (PM-AJAY Mapping)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            InfoLine("Education", viewModel.profile.education?.name ?: "Not set")
            if (viewModel.profile.familyOccupation.isNotBlank()) InfoLine("Family Occupation", viewModel.profile.familyOccupation)
            if (viewModel.profile.currentLivelihood.isNotBlank()) InfoLine("Current Livelihood", viewModel.profile.currentLivelihood)
            InfoLine("Interests", viewModel.profile.interests.joinToString(", "))
            if (viewModel.profile.skills.isNotEmpty()) InfoLine("Skills", viewModel.profile.skills.joinToString(", "))
            InfoLine("Preference", viewModel.profile.preference?.name ?: "Not set")
            InfoLine("Mobility", viewModel.profile.mobility?.name ?: "Not set")
            if (viewModel.profile.physicalConstraints.isNotBlank()) InfoLine("Physical Constraints", viewModel.profile.physicalConstraints)
            if (viewModel.profile.localOpportunity.isNotBlank()) InfoLine("Local Economic Note", viewModel.profile.localOpportunity)
            InfoLine("District", viewModel.profile.district)
        }

        Spacer(Modifier.height(12.dp))

        if (rec.centre != null) {
            PremiumCard {
                Text(viewModel.tr("centre"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(6.dp))
                Text(rec.centre.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(rec.centre.address, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(rec.centre.trades, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                Text(rec.centre.confidence, fontSize = 12.sp, color = BrandSaffron, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))

                val phone = rec.centre.phone
                if (!phone.isNullOrBlank() && phone.length > 3 && phone != "null") {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandTeal)
                    ) {
                        Text(viewModel.tr("call"))
                    }
                }
            }
        } else {
            PremiumCard {
                Text(viewModel.tr("no_centre"), fontSize = 15.sp, color = BrandSaffron, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(12.dp))

        PremiumCard {
            Text(viewModel.tr("not_claim_text"), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(12.dp))
        PremiumCard {
            Text("PM-AJAY GIA - Honest Assessment", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("This recommendation is based on verified NSQF QP codes (516), 20 centres, 38 districts, and PM-AJAY fundable rules. We do not invent courses, centres, or fees. Match score: ${rec.score}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun InfoLine(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
        Text(value, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 2.dp))
    }
}
