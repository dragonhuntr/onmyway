package com.onmyway.app.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onmyway.app.model.AppModel
import com.onmyway.app.ui.theme.OmwColors
import com.onmyway.app.ui.theme.OmwType
import com.onmyway.app.ui.theme.bold
import com.onmyway.app.ui.theme.semibold
import kotlinx.coroutines.launch

/** Login, with Register pushed on top. */
@Composable
fun AuthFlow(model: AppModel) {
    var showRegister by rememberSaveable { mutableStateOf(false) }
    if (showRegister) {
        BackHandler { showRegister = false }
        RegisterScreen(model)
    } else {
        LoginScreen(model, onRegister = { showRegister = true })
    }
}

/** 01A_Login — checks the username and password against Cloudflare D1. */
@Composable
private fun LoginScreen(model: AppModel, onRegister: () -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showForgotPassword by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val canSubmit = username.isNotBlank() && password.isNotEmpty()

    AuthScaffold {
        Title("Login", Modifier.padding(top = 64.dp, bottom = 28.dp))

        AuthField("Username", username, { username = it }, contentType = ContentType.Username)
        AuthField("Password", password, { password = it }, isSecure = true, contentType = ContentType.Password)

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                "Forgot Password?",
                style = OmwType.Title3.bold,
                color = OmwColors.Ink,
                modifier = Modifier.clickable(role = Role.Button) { showForgotPassword = true },
            )
        }

        errorMessage?.let { ErrorText(it) }

        AuthButton("Login", enabled = canSubmit && !isSubmitting, modifier = Modifier.padding(top = 8.dp)) {
            scope.launch {
                isSubmitting = true
                errorMessage = null
                try {
                    model.logIn(username, password)
                } catch (e: Exception) {
                    errorMessage = e.message
                } finally {
                    isSubmitting = false
                }
            }
        }

        Text(
            "Not Registered? Create An account",
            style = OmwType.Title3.bold,
            color = OmwColors.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clickable(role = Role.Button, onClick = onRegister),
        )
    }

    if (showForgotPassword) {
        AlertDialog(
            onDismissRequest = { showForgotPassword = false },
            confirmButton = { TextButton({ showForgotPassword = false }) { Text("OK") } },
            title = { Text("Forgot Password?") },
            text = { Text("Password reset is not available yet. Create a new account if you do not remember this password.") },
            containerColor = Color.White,
        )
    }
}

/** 01B_Register — creates the account in Cloudflare D1. */
@Composable
private fun RegisterScreen(model: AppModel) {
    var email by rememberSaveable { mutableStateOf("") }
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val canSubmit = "@" in email &&
        firstName.isNotBlank() &&
        lastName.isNotBlank() &&
        username.isNotBlank() &&
        password.isNotEmpty() &&
        password == confirmPassword
    val showMismatch = confirmPassword.isNotEmpty() && password != confirmPassword

    AuthScaffold(spacing = 12) {
        Title("Register", Modifier.padding(top = 48.dp, bottom = 20.dp))

        AuthField("Email", email, { email = it }, keyboard = KeyboardType.Email, contentType = ContentType.EmailAddress)
        AuthField("First Name", firstName, { firstName = it }, contentType = ContentType.PersonFirstName, words = true)
        AuthField("Last Name", lastName, { lastName = it }, contentType = ContentType.PersonLastName, words = true)
        AuthField("Username", username, { username = it }, contentType = ContentType.NewUsername)
        AuthField("Password", password, { password = it }, isSecure = true, contentType = ContentType.NewPassword)
        AuthField("Confirm-Password", confirmPassword, { confirmPassword = it }, isSecure = true, contentType = ContentType.NewPassword)

        if (showMismatch) ErrorText("Passwords don’t match.")
        errorMessage?.let { ErrorText(it) }

        AuthButton("Register", enabled = canSubmit && !isSubmitting, modifier = Modifier.padding(top = 12.dp)) {
            scope.launch {
                isSubmitting = true
                errorMessage = null
                try {
                    model.register(email.trim(), firstName.trim(), lastName.trim(), username.trim(), password)
                } catch (e: Exception) {
                    errorMessage = e.message
                } finally {
                    isSubmitting = false
                }
            }
        }
    }
}

@Composable
private fun AuthScaffold(spacing: Int = 16, content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(OmwColors.Canvas)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
    ) { content() }
}

@Composable
private fun Title(text: String, modifier: Modifier) {
    Text(
        text,
        fontSize = 64.sp,
        fontWeight = FontWeight.Bold,
        color = OmwColors.Brand,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun ErrorText(text: String) {
    Text(text, style = OmwType.Footnote.semibold, color = OmwColors.InkSecondary, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun AuthField(
    prompt: String,
    value: String,
    onValueChange: (String) -> Unit,
    isSecure: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    contentType: ContentType? = null,
    words: Boolean = false,
) {
    val shape = RoundedCornerShape(16.dp)
    val style = OmwType.Title3.semibold.copy(color = OmwColors.Ink)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(OmwColors.Brand),
        visualTransformation = if (isSecure) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            capitalization = if (words) KeyboardCapitalization.Words else KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = if (isSecure) KeyboardType.Password else keyboard,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentType?.let { this.contentType = it } },
        decorationBox = { inner ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .background(Color.White, shape)
                    .border(1.dp, OmwColors.Hairline, shape)
                    .padding(horizontal = 16.dp),
            ) {
                if (value.isEmpty()) Text(prompt, style = style, color = OmwColors.InkSecondary)
                inner()
            }
        },
    )
}

/** Green full-width auth button. Taller and rounder than the in-app CTA, matching 01A and 01B. */
@Composable
private fun AuthButton(text: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .alpha(if (enabled) (if (pressed) 0.85f else 1f) else 0.5f)
            .background(OmwColors.Brand, RoundedCornerShape(18.dp))
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Text(text, style = OmwType.Title3.bold, color = Color.White)
    }
}
