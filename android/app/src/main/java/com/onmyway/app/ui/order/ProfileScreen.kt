package com.onmyway.app.ui.order

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onmyway.app.model.AppModel
import com.onmyway.app.ui.theme.OmwColors
import com.onmyway.app.ui.theme.OmwType
import com.onmyway.app.ui.shared.DestinationPicker
import com.onmyway.app.ui.theme.bold

@Composable
fun ProfileScreen(model: AppModel) {
    BackHandler { model.showingProfile = false }

    val session = model.session ?: return
    val userName = "${session.firstName} ${session.lastName}".trim()
    var choosingDestination by rememberSaveable { mutableStateOf(false) }

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
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .background(OmwColors.AmberSoft, CircleShape),
            ) {
                Text(
                    text = session.firstName.take(1).uppercase(),
                    style = OmwType.LargeTitle.bold,
                    color = OmwColors.BrandDeep,
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = userName,
                style = OmwType.Title2.bold.copy(fontSize = 28.sp),
                color = OmwColors.Ink,
            )
            Text(
                text = "@${session.username}",
                style = OmwType.Subheadline,
                color = OmwColors.InkSecondary,
            )
        }

        Spacer(Modifier.height(20.dp))

        CardSection(
            title = "Account",
            items = listOf(
                "Name" to userName,
                "Username" to "@${session.username}",
                "Email" to session.email,
            ),
        )

        Spacer(Modifier.height(16.dp))

        CardSection(
            title = "Delivery",
            items = listOf("Deliver to" to (model.destination?.full ?: "Not set")),
            onClick = { choosingDestination = true },
            onClickLabel = "Change delivery location",
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

    if (choosingDestination) DestinationPicker(model) { choosingDestination = false }
}

@Composable
private fun CardSection(
    title: String,
    items: List<Pair<String, String>>,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(Color.White, shape)
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick) else Modifier)
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
