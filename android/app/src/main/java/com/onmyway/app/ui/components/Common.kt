package com.onmyway.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onmyway.app.ui.theme.OmwColors
import com.onmyway.app.ui.theme.OmwType
import com.onmyway.app.ui.theme.bold
import com.onmyway.app.ui.theme.semibold
import kotlinx.coroutines.launch

/** Centered icon, title and message for empty lists, like iOS `ContentUnavailableView`. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = OmwColors.InkSecondary, modifier = Modifier.size(44.dp))
        Text(
            title,
            style = OmwType.Title3.bold,
            color = OmwColors.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp).semantics { heading() },
        )
        Text(message, style = OmwType.Subheadline, color = OmwColors.InkSecondary, textAlign = TextAlign.Center)
        if (action != null) Box(Modifier.padding(top = 8.dp)) { action() }
    }
}

/** Inline navigation bar: leading button, centered title, optional trailing content. */
@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    background: Color = OmwColors.Canvas,
    navigationIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    navigationLabel: String = "Back",
    titleContent: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(background)
            .statusBarsPadding()
            .height(52.dp),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) {
                Icon(navigationIcon, contentDescription = navigationLabel, tint = OmwColors.Ink)
            }
        }
        Row(Modifier.align(Alignment.Center).padding(horizontal = 56.dp)) {
            if (titleContent != null) {
                titleContent()
            } else {
                Text(
                    title,
                    style = OmwType.Headline,
                    color = OmwColors.Ink,
                    maxLines = 1,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
    }
}

/** Large leading screen title, like an iOS large navigation title. */
@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = OmwType.LargeTitle.bold,
        color = OmwColors.Ink,
        modifier = modifier.semantics { heading() },
    )
}

/** Small tinted label used for "Matching…" style status. */
@Composable
fun StatusCapsule(text: String, leading: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(OmwColors.BrandSoft, CircleShape)
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
    ) {
        leading()
        Text(text, style = OmwType.Footnote.semibold, color = OmwColors.BrandDeep)
    }
}

/** Opens a link in the app that handles it (browser, Maps, Stripe onboarding). */
fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        // Nothing installed to open it.
    }
}

/** Vertically scrolling screen with pull-to-refresh, like iOS `.refreshable`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshableColumn(
    onRefresh: suspend () -> Unit,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                onRefresh()
                isRefreshing = false
            }
        },
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = verticalArrangement,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            content = content,
        )
    }
}
