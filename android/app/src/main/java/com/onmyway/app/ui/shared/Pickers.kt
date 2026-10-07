package com.onmyway.app.ui.shared

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.onmyway.app.model.AppModel
import com.onmyway.app.model.ApiException
import com.onmyway.app.model.Building
import com.onmyway.app.model.Trip
import com.onmyway.app.model.shortTime
import com.onmyway.app.ui.theme.OmwColors
import com.onmyway.app.ui.theme.OmwType
import com.onmyway.app.ui.theme.medium
import com.onmyway.app.ui.theme.semibold
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Where orders are delivered: a campus building, room, and a note for the runner. */
@Composable
fun DestinationPicker(model: AppModel, onDismiss: () -> Unit) {
    var buildingId by remember { mutableStateOf(model.destination?.buildingId.orEmpty()) }
    var room by remember { mutableStateOf(model.destination?.room.orEmpty()) }
    var note by remember { mutableStateOf(model.destination?.note.orEmpty()) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { model.loadBuildings() }

    FormSheet(
        title = "Deliver to",
        canSave = buildingId.isNotEmpty() && !isSaving,
        onDismiss = onDismiss,
        onSave = {
            scope.launch {
                isSaving = true
                try {
                    model.setDestination(buildingId, room, note)
                    onDismiss()
                } catch (e: ApiException) {
                    errorMessage = e.message
                } finally {
                    isSaving = false
                }
            }
        },
    ) {
        BuildingField("Building", model.buildings, buildingId) { buildingId = it }
        FormTextField("Room", room, placeholder = "Room 174") { room = it }
        FormTextField("Note for your runner", note, placeholder = "Text me when you arrive.", singleLine = false) { note = it }
        errorMessage?.let { Text(it, style = OmwType.Footnote, color = OmwColors.InkSecondary) }
    }
}

/** Add or edit a walk the runner is already taking. */
@Composable
fun TripEditor(model: AppModel, trip: Trip?, onDismiss: () -> Unit) {
    var from by remember { mutableStateOf(trip?.from?.id.orEmpty()) }
    var to by remember { mutableStateOf(trip?.to?.id.orEmpty()) }
    var leaveAt by remember { mutableStateOf(trip?.leaveAt ?: Instant.now().plus(Duration.ofMinutes(10))) }
    var note by remember { mutableStateOf(trip?.note.orEmpty()) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { model.loadBuildings() }

    FormSheet(
        title = if (trip == null) "Add a trip" else "Edit route",
        canSave = from.isNotEmpty() && to.isNotEmpty() && from != to && !isSaving,
        onDismiss = onDismiss,
        onSave = {
            scope.launch {
                isSaving = true
                try {
                    model.saveTrip(trip?.id, from, to, leaveAt, note)
                    onDismiss()
                } catch (e: ApiException) {
                    errorMessage = e.message
                } finally {
                    isSaving = false
                }
            }
        },
    ) {
        BuildingField("From", model.buildings, from) { from = it }
        BuildingField("To", model.buildings, to) { to = it }
        DateTimeField("Leaving", leaveAt, earliest = Instant.now().minus(Duration.ofHours(1))) { leaveAt = it }
        FormTextField("What’s it for? (optional)", note, placeholder = "a 10:00 lecture") { note = it }
        Text(
            errorMessage ?: "Shown to students: “Already heading to … for a 10:00 lecture.”",
            style = OmwType.Footnote,
            color = OmwColors.InkSecondary,
        )
        if (trip != null) {
            TextButton(
                onClick = {
                    scope.launch {
                        runCatching { model.deleteTrip(trip) }
                        onDismiss()
                    }
                },
            ) { Text("Remove trip", color = Color(0xFFC62828)) }
        }
    }
}

/** Bottom sheet with Cancel / title / Save, like an iOS form sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormSheet(
    title: String,
    canSave: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = OmwColors.Canvas,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = onDismiss) { Text("Cancel", color = OmwColors.Brand) }
            Text(title, style = OmwType.Headline, color = OmwColors.Ink, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            TextButton(onClick = onSave, enabled = canSave) {
                Text("Save", style = OmwType.Body.semibold, color = if (canSave) OmwColors.Brand else OmwColors.Hairline)
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            content = content,
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = OmwColors.Brand,
    unfocusedBorderColor = OmwColors.Hairline,
    focusedLabelColor = OmwColors.Brand,
    cursorColor = OmwColors.Brand,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
)

@Composable
private fun FormTextField(
    label: String,
    value: String,
    placeholder: String,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = OmwColors.InkSecondary) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuildingField(label: String, buildings: List<Building>, selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = buildings.firstOrNull { it.id == selectedId }?.name ?: "Choose…",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = fieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = Color.White) {
            buildings.forEach { building ->
                DropdownMenuItem(
                    text = { Text(building.name) },
                    onClick = {
                        onSelect(building.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/** Date then time, picked with the platform dialogs. */
@Composable
private fun DateTimeField(label: String, value: Instant, earliest: Instant, onChange: (Instant) -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val current = value.atZone(zone)
    val shape = RoundedCornerShape(12.dp)

    fun pickTime(date: LocalDate) {
        TimePickerDialog(
            context,
            { _, hour, minute -> onChange(ZonedDateTime.of(date, java.time.LocalTime.of(hour, minute), zone).toInstant()) },
            current.hour,
            current.minute,
            DateFormat.is24HourFormat(context),
        ).show()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, shape)
            .clickable(role = Role.Button) {
                DatePickerDialog(
                    context,
                    { _, year, month, day -> pickTime(LocalDate.of(year, month + 1, day)) },
                    current.year,
                    current.monthValue - 1,
                    current.dayOfMonth,
                ).apply { datePicker.minDate = earliest.toEpochMilli() }.show()
            }
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(label, style = OmwType.Body, color = OmwColors.Ink, modifier = Modifier.weight(1f))
        Text("${current.format(dateFormat)}  ${value.shortTime}", style = OmwType.Body.medium, color = OmwColors.Brand)
    }
}

/** Time of day on the same date, picked with the platform dialog. */
fun showTimePicker(context: android.content.Context, value: Instant, onChange: (Instant) -> Unit) {
    val zone = ZoneId.systemDefault()
    val current = value.atZone(zone)
    TimePickerDialog(
        context,
        { _, hour, minute ->
            onChange(current.withHour(hour).withMinute(minute).withSecond(0).withNano(0).toInstant())
        },
        current.hour,
        current.minute,
        DateFormat.is24HourFormat(context),
    ).show()
}
