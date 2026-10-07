package com.onmyway.app.ui.order

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onmyway.app.R
import com.onmyway.app.model.AppModel
import com.onmyway.app.model.Restaurant
import com.onmyway.app.ui.components.EmptyState
import com.onmyway.app.ui.components.RefreshableColumn
import com.onmyway.app.ui.shared.DestinationPicker
import com.onmyway.app.ui.theme.Icon
import com.onmyway.app.ui.theme.IconBadge
import com.onmyway.app.ui.theme.LinkButton
import com.onmyway.app.ui.theme.OmwColors
import com.onmyway.app.ui.theme.OmwType
import com.onmyway.app.ui.theme.bold
import com.onmyway.app.ui.theme.card
import com.onmyway.app.ui.theme.medium
import com.onmyway.app.ui.theme.semibold
import java.util.Locale

/** 01_Home — pick a dining hall; students already walking your way deliver it for $1. */
@Composable
fun HomeScreen(model: AppModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var choosingDestination by rememberSaveable { mutableStateOf(false) }
    val results = if (query.isEmpty()) model.restaurants else model.restaurants.filter {
        it.name.lowercase(Locale.ROOT).contains(query.trim().lowercase(Locale.ROOT))
    }
    val open = results.filter { it.hours.isOpen() }

    LaunchedEffect(Unit) { model.loadHome() }

    RefreshableColumn(
        onRefresh = model::loadHome,
        modifier = Modifier.fillMaxSize().background(OmwColors.Canvas).statusBarsPadding(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
        ) {
            Header(model, onChooseDestination = { choosingDestination = true })
            SearchField(query) { query = it }
            if (query.isEmpty()) RunnerBanner(model)
        }

        if (results.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Search,
                title = "No Results for “$query”",
                message = "Check the spelling or try a new search.",
                modifier = Modifier.padding(top = 40.dp),
            )
        } else {
            if (open.isNotEmpty()) OpenNow(open) { model.orderingFrom = it }
            AllDining(results) { model.orderingFrom = it }
        }
    }

    if (choosingDestination) DestinationPicker(model) { choosingDestination = false }
}

@Composable
private fun Header(model: AppModel, onChooseDestination: () -> Unit) {
    val destination = model.destination?.short
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .clickable(role = Role.Button, onClickLabel = "Change delivery location", onClick = onChooseDestination)
                .semantics(mergeDescendants = true) { contentDescription = "Deliver to ${destination ?: "no building chosen"}" },
        ) {
            Text("Deliver to", style = OmwType.Caption.medium, color = OmwColors.InkSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(R.drawable.pin_location, 18.dp)
                Text(destination ?: "Choose a building", style = OmwType.Callout.semibold, color = OmwColors.Ink)
                Icon(R.drawable.chevron_right, 16.dp)
            }
        }

        Spacer(Modifier.weight(1f))

        IconBadge(
            R.drawable.bell, iconSize = 22.dp, badgeSize = 44.dp, background = Color.White,
            border = BorderStroke(1.dp, OmwColors.Hairline),
            modifier = Modifier
                .clip(CircleShape)
                .clickable(role = Role.Button) { /* TODO: notifications */ }
                .semantics { contentDescription = "Notifications" },
        )

        ProfileMenu(model)
    }
}

@Composable
private fun ProfileMenu(model: AppModel) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.padding(start = 8.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(OmwColors.AmberSoft)
                .clickable(role = Role.Button) { expanded = true }
                .clearAndSetSemantics { contentDescription = "Profile" },
        ) {
            Text(
                model.session?.firstName?.take(1)?.uppercase().orEmpty(),
                style = OmwType.Title3.semibold,
                color = OmwColors.BrandDeep,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = Color.White) {
            model.session?.let { session ->
                DropdownMenuItem(
                    text = { Text("${session.firstName} ${session.lastName} · @${session.username}") },
                    onClick = {},
                    enabled = false,
                )
            }
            DropdownMenuItem(
                text = { Text("Sign out", color = Color(0xFFC62828)) },
                onClick = {
                    expanded = false
                    model.signOut()
                },
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    BasicTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        textStyle = OmwType.Subheadline.copy(color = OmwColors.Ink),
        cursorBrush = SolidColor(OmwColors.Brand),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        decorationBox = { inner ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(Color.White, shape)
                    .border(1.dp, OmwColors.Hairline, shape)
                    .padding(horizontal = 16.dp),
            ) {
                Icon(R.drawable.search, 20.dp)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Search dining halls & cafés", style = OmwType.Subheadline, color = OmwColors.InkSecondary)
                    }
                    inner()
                }
            }
        },
    )
}

@Composable
private fun RunnerBanner(model: AppModel) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(OmwColors.Brand, RoundedCornerShape(18.dp))
            .padding(18.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text("${model.runnersHeadingYourWay} runners heading your way", style = OmwType.Title3.bold, color = Color.White)
            Text(
                "Students already walking to ${model.destination?.building ?: "your building"} can bring your order in ~10 min for a $1 fee.",
                style = OmwType.Footnote,
                color = OmwColors.OnBrand,
            )
        }
        IconBadge(R.drawable.footprints_white, iconSize = 28.dp, badgeSize = 56.dp, background = OmwColors.BrandMid)
    }
}

@Composable
private fun OpenNow(open: List<Restaurant>, onSelect: (Restaurant) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp)) {
            Text("Open now on campus", style = OmwType.Headline, color = OmwColors.Ink)
            Spacer(Modifier.weight(1f))
            LinkButton("See all") { /* TODO: all restaurants */ }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
        ) {
            items(open, key = { it.id }) { restaurant ->
                RestaurantCard(restaurant) { onSelect(restaurant) }
            }
        }
    }
}

@Composable
private fun AllDining(results: List<Restaurant>, onSelect: (Restaurant) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(20.dp)) {
        Text("All dining", style = OmwType.Headline, color = OmwColors.Ink)
        results.forEach { restaurant ->
            RestaurantRow(restaurant) { onSelect(restaurant) }
        }
    }
}

@Composable
fun FoodTile(restaurant: Restaurant, width: Dp, height: Dp) {
    val shape = RoundedCornerShape(12.dp)
    Image(
        painterResource(restaurant.logo),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .size(width, height)
            .clip(shape)
            .background(restaurant.tile)
            .border(1.dp, OmwColors.Hairline, shape),
    )
}

@Composable
private fun RestaurantCard(restaurant: Restaurant, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .width(168.dp)
            .clip(shape)
            .background(Color.White)
            .border(1.dp, OmwColors.Hairline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(10.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        FoodTile(restaurant, 148.dp, 104.dp)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(restaurant.name, style = OmwType.Subheadline.semibold, color = OmwColors.Ink)
            Text("$1 delivery · ${restaurant.eta}", style = OmwType.Caption, color = OmwColors.InkSecondary)
            restaurant.building?.let {
                Text(it.name, style = OmwType.Caption.medium, color = OmwColors.Ink)
            }
        }
    }
}

@Composable
private fun RestaurantRow(restaurant: Restaurant, onClick: () -> Unit) {
    val isOpen = restaurant.hours.isOpen()
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .card(padding = 12.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        FoodTile(restaurant, 60.dp, 60.dp)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            Text(restaurant.name, style = OmwType.Subheadline.semibold, color = OmwColors.Ink)
            Text("${restaurant.distance} · ${restaurant.eta}", style = OmwType.Caption, color = OmwColors.InkSecondary)
            Text(
                restaurant.hours.status(),
                style = OmwType.Caption2.medium,
                color = if (isOpen) OmwColors.BrandDeep else OmwColors.InkSecondary,
                modifier = Modifier
                    .background(if (isOpen) OmwColors.BrandSoft else OmwColors.Hairline, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Icon(R.drawable.chevron_right, 20.dp)
    }
}
