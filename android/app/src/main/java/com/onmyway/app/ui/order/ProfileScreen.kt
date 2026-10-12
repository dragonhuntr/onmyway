package com.onmyway.app.ui.order

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onmyway.app.R
import com.onmyway.app.model.AppModel
import com.onmyway.app.ui.theme.OmwColors
import com.onmyway.app.ui.theme.OmwType
import com.onmyway.app.ui.theme.bold

@Composable
fun ProfileScreen(model: AppModel) {
    BackHandler { model.showingProfile = false }

    val session = model.session
    val userName = if (session != null) "${session.firstName} ${session.lastName}".ifBlank { "Wi Ya" } else "Wi Ya"
    val userEmail = session?.email ?: "wiya@example.com"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OmwColors.Canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // Header with Back Button and Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = { model.showingProfile = false }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = OmwColors.BrandDeep,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                text = "Profile",
                style = OmwType.LargeTitle.bold.copy(
                    fontSize = 38.sp,
                    lineHeight = 44.sp,
                ),
                color = OmwColors.BrandDeep,
                modifier = Modifier.semantics { heading() },
            )
        }

        Spacer(Modifier.height(8.dp))

        // User Avatar & Name
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.customer_avatar),
                contentDescription = "Profile picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = userName,
                style = OmwType.Title2.bold.copy(fontSize = 28.sp),
                color = OmwColors.Ink,
            )
        }

        Spacer(Modifier.height(20.dp))

        // Personal Details Section
        CardSection(
            title = "Personal Details",
            items = listOf(
                "Email" to userEmail,
                "Phone" to "+1 (555) 012-3456",
                "Location" to "San Francisco, CA",
            ),
        )

        Spacer(Modifier.height(16.dp))

        // Preferences Section
        CardSection(
            title = "Preferences",
            items = listOf(
                "Delivery Radius" to "5 miles",
                "Payment Method" to "Apple Pay",
                "Notifications" to "Enabled",
                "Dietary preferences" to "Not set",
                "Favorite cuisines" to "Not set",
            ),
        )

        Spacer(Modifier.height(28.dp))

        // Log Out Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .background(OmwColors.Brand, RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button) { model.signOut() },
            ) {
                Text(
                    text = "Log Out",
                    style = OmwType.Headline.bold,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun CardSection(
    title: String,
    items: List<Pair<String, String>>,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(Color.White, RoundedCornerShape(20.dp))
            .padding(20.dp),
    ) {
        Text(
            text = title,
            style = OmwType.Headline.bold,
            color = OmwColors.BrandDeep,
        )

        items.forEach { (label, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = OmwType.Subheadline,
                    color = OmwColors.InkSecondary,
                )
                Text(
                    text = value,
                    style = OmwType.Subheadline.bold,
                    color = OmwColors.Ink,
                )
            }
        }
    }
}
