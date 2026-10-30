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
import androidx.compose.material3.Text
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
import com.onmyway.app.model.Api
import com.onmyway.app.model.AppModel
import com.onmyway.app.ui.theme.OmwColors
import com.onmyway.app.ui.theme.OmwType
import com.onmyway.app.ui.theme.bold
import com.onmyway.app.ui.theme.semibold
import kotlinx.coroutines.launch

private enum class AuthRoute { Login, Register, ForgotPassword }

/** Login, with Register or Forgot Password pushed on top. */
@Composable
fun AuthFlow(model: AppModel) {
    var route by rememberSaveable { mutableStateOf(AuthRoute.Login) }
    when (route) {
        AuthRoute.Login -> LoginScreen(
            model,
            onRegister = { route = AuthRoute.Register },
            onForgotPassword = { route = AuthRoute.ForgotPassword },
        )
        AuthRoute.Register -> {
            BackHandler { route = AuthRoute.Login }
            RegisterScreen(model)
        }
        AuthRoute.ForgotPassword -> {
            BackHandler { route = AuthRoute.Login }
            ForgotPasswordScreen(model, onBack = { route = AuthRoute.Login })
        }
    }
}

/** 01A_Login — checks the username and password against Cloudflare D1. */
@Composable
private fun LoginScreen(model: AppModel, onRegister: () -> Unit, onForgotPassword: () -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
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
                modifier = Modifier.clickable(role = Role.Button, onClick = onForgotPassword),
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

/** Forgot Password — emails a 6-digit code, then sets a new password with it and signs in. */
@Composable
private fun ForgotPasswordScreen(model: AppModel, onBack: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var codeSent by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val canSubmit = if (codeSent) {
        code.length == 6 && password.isNotEmpty() && password == confirmPassword
    } else {
        "@" in email
    }
    val showMismatch = confirmPassword.isNotEmpty() && password != confirmPassword

    AuthScaffold {
        Title("Reset", Modifier.padding(top = 64.dp, bottom = 28.dp))

        if (codeSent) {
            Text(
                "If ${email.trim()} has an account, we emailed it a 6-digit code. It works for 15 minutes.",
                style = OmwType.Body.semibold,
                color = OmwColors.InkSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
            AuthField(
                "6-Digit Code",
                code,
                { code = it.filter(Char::isDigit).take(6) },
                keyboard = KeyboardType.Number,
                contentType = ContentType.SmsOtpCode,
            )
            AuthField("New Password", password, { password = it }, isSecure = true, contentType = ContentType.NewPassword)
            AuthField("Confirm-Password", confirmPassword, { confirmPassword = it }, isSecure = true, contentType = ContentType.NewPassword)
            if (showMismatch) ErrorText("Passwords don’t match.")
        } else {
            AuthField("Email", email, { email = it }, keyboard = KeyboardType.Email, contentType = ContentType.EmailAddress)
        }

        errorMessage?.let { ErrorText(it) }

        AuthButton(
            if (codeSent) "Reset Password" else "Send Code",
            enabled = canSubmit && !isSubmitting,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            scope.launch {
                isSubmitting = true
                errorMessage = null
                try {
                    if (codeSent) {
                        model.resetPassword(email.trim(), code, password)
                    } else {
                        Api.requestPasswordReset(email.trim())
                        codeSent = true
                    }
                } catch (e: Exception) {
                    errorMessage = e.message
                } finally {
                    isSubmitting = false
                }
            }
        }

        Text(
            if (codeSent) "Send a new code" else "Back to Login",
            style = OmwType.Title3.bold,
            color = OmwColors.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clickable(role = Role.Button) {
                    if (codeSent) {
                        codeSent = false
                        code = ""
                        errorMessage = null
                    } else {
                        onBack()
                    }
                },
        )
    }
}

/** Shown after sign-up (and on launch) until the emailed 6-digit code is entered. */
@Composable
fun VerifyEmailScreen(model: AppModel) {
    var code by rememberSaveable { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AuthScaffold {
        Title("Verify", Modifier.padding(top = 64.dp, bottom = 28.dp))
        Text(
            "We emailed a 6-digit code to ${model.session?.email ?: "your email"}. Enter it to start ordering and running.",
            style = OmwType.Body.semibold,
            color = OmwColors.InkSecondary,
            modifier = Modifier.fillMaxWidth(),
        )
        AuthField(
            "6-Digit Code",
            code,
            { code = it.filter(Char::isDigit).take(6) },
            keyboard = KeyboardType.Number,
            contentType = ContentType.SmsOtpCode,
        )

        message?.let { ErrorText(it) }

        AuthButton("Verify Email", enabled = code.length == 6 && !isSubmitting, modifier = Modifier.padding(top = 8.dp)) {
            scope.launch {
                isSubmitting = true
                message = null
                try {
                    model.verifyEmail(code)
                } catch (e: Exception) {
                    message = e.message
                } finally {
                    isSubmitting = false
                }
            }
        }

        Text(
            "Send a new code",
            style = OmwType.Title3.bold,
            color = OmwColors.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clickable(enabled = !isSubmitting, role = Role.Button) {
                    scope.launch {
                        isSubmitting = true
                        try {
                            model.resendVerification()
                            code = ""
                            message = "Sent. A new code can go out once a minute, and the newest one is the one that works."
                        } catch (e: Exception) {
                            message = e.message
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
        )

        Text(
            "Sign out",
            style = OmwType.Body.semibold,
            color = OmwColors.InkSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { model.signOut() },
        )
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
