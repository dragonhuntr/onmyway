package com.onmyway.app.ui.shared

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onmyway.app.model.AppModel
import com.onmyway.app.model.ApiException
import com.onmyway.app.model.ChatMessage
import com.onmyway.app.model.ChatTarget
import com.onmyway.app.model.shortTime
import com.onmyway.app.ui.components.TopBar
import com.onmyway.app.ui.theme.OmwColors
import com.onmyway.app.ui.theme.OmwType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** In-app chat between a customer and their runner. Polls for new messages while open. */
@Composable
fun ChatScreen(model: AppModel, target: ChatTarget) {
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var draft by rememberSaveable { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun append(new: List<ChatMessage>) {
        val known = messages.map { it.id }.toSet()
        messages += new.filterNot { it.id in known }
    }

    LaunchedEffect(target.orderId) {
        while (true) {
            try {
                append(model.messages(target.orderId, messages.lastOrNull()?.createdAt))
            } catch (e: ApiException) {
                // Try again on the next poll.
            }
            delay(3_000)
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    val close = { model.chat = null }
    BackHandler(onBack = close)

    Column(Modifier.fillMaxSize().background(OmwColors.Canvas).imePadding()) {
        TopBar(target.title, onBack = close, background = Color.White)
        HorizontalDivider(color = OmwColors.Hairline)
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        "Messages stay in the app. Say hi!",
                        style = OmwType.Footnote,
                        color = OmwColors.InkSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    )
                }
            }
            items(messages, key = { it.id }) { Bubble(it) }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.background(Color.White),
        ) {
            HorizontalDivider(color = OmwColors.Hairline)
            errorMessage?.let { Text(it, style = OmwType.Caption, color = OmwColors.InkSecondary) }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    textStyle = OmwType.Body.copy(color = OmwColors.Ink),
                    cursorBrush = SolidColor(OmwColors.Brand),
                    maxLines = 4,
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box(
                            Modifier
                                .background(OmwColors.Canvas, RoundedCornerShape(20.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            if (draft.isEmpty()) Text("Message", style = OmwType.Body, color = OmwColors.InkSecondary)
                            inner()
                        }
                    },
                )
                val canSend = draft.isNotBlank() && !isSending
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .alpha(if (canSend) 1f else 0.5f)
                        .background(OmwColors.Brand)
                        .clickable(enabled = canSend, role = Role.Button) {
                            scope.launch {
                                isSending = true
                                try {
                                    append(listOf(model.send(draft.trim(), target.orderId)))
                                    draft = ""
                                    errorMessage = null
                                } catch (e: ApiException) {
                                    errorMessage = e.message
                                } finally {
                                    isSending = false
                                }
                            }
                        }
                        .semantics { contentDescription = "Send" },
                ) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun Bubble(message: ChatMessage) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        if (message.fromMe) Spacer(Modifier.weight(1f).width(48.dp))
        Column(
            horizontalAlignment = if (message.fromMe) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(4f, fill = false),
        ) {
            val shape = RoundedCornerShape(16.dp)
            Text(
                message.body,
                style = OmwType.Subheadline,
                color = if (message.fromMe) Color.White else OmwColors.Ink,
                modifier = Modifier
                    .background(if (message.fromMe) OmwColors.Brand else Color.White, shape)
                    .then(if (message.fromMe) Modifier else Modifier.border(1.dp, OmwColors.Hairline, shape))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Text(message.createdAt.shortTime, style = OmwType.Caption2, color = OmwColors.InkSecondary)
        }
        if (!message.fromMe) Spacer(Modifier.weight(1f).width(48.dp))
    }
}

