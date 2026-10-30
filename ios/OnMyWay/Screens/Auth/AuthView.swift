import SwiftUI

/// 01A_Login — checks the username and password against Cloudflare D1.
struct LoginView: View {
    @Environment(AppModel.self) private var model
    @State private var username = ""
    @State private var password = ""
    @State private var isSubmitting = false
    @State private var errorMessage: String?

    private var canSubmit: Bool {
        !username.trimmingCharacters(in: .whitespaces).isEmpty && !password.isEmpty
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    Text("Login")
                        .font(.system(size: 64, weight: .bold))
                        .foregroundStyle(Color.brand)
                        .frame(maxWidth: .infinity)
                        .minimumScaleFactor(0.5)
                        .padding(.top, 64)
                        .padding(.bottom, 28)

                    AuthField(prompt: "Username", text: $username, contentType: .username)
                    AuthField(prompt: "Password", text: $password, isSecure: true, contentType: .password)

                    HStack {
                        Spacer()
                        NavigationLink {
                            ForgotPasswordView()
                        } label: {
                            Text("Forgot Password?")
                                .font(.title3.bold())
                                .foregroundStyle(Color.ink)
                        }
                    }

                    if let errorMessage {
                        Text(errorMessage)
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(Color.inkSecondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    Button("Login") {
                        Task { await submitLogin() }
                    }
                    .buttonStyle(AuthButtonStyle())
                    .disabled(!canSubmit || isSubmitting)
                    .padding(.top, 8)

                    NavigationLink {
                        RegisterView()
                    } label: {
                        Text("Not Registered? Create An account")
                            .font(.title3.bold())
                            .foregroundStyle(Color.ink)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)
                    }
                    .padding(.top, 4)
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 24)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Color.canvas)
            .toolbarVisibility(.hidden, for: .navigationBar)
        }
    }

    private func submitLogin() async {
        isSubmitting = true
        errorMessage = nil
        defer { isSubmitting = false }
        do {
            try await model.logIn(username: username, password: password)
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

/// 01B_Register — creates the account in Cloudflare D1.
struct RegisterView: View {
    @Environment(AppModel.self) private var model
    @State private var email = ""
    @State private var firstName = ""
    @State private var lastName = ""
    @State private var username = ""
    @State private var password = ""
    @State private var confirmPassword = ""
    @State private var isSubmitting = false
    @State private var errorMessage: String?

    private var trimmedUsername: String {
        username.trimmingCharacters(in: .whitespaces)
    }

    private var canSubmit: Bool {
        email.contains("@")
            && !firstName.trimmingCharacters(in: .whitespaces).isEmpty
            && !lastName.trimmingCharacters(in: .whitespaces).isEmpty
            && !trimmedUsername.isEmpty
            && !password.isEmpty
            && password == confirmPassword
    }

    private var showMismatch: Bool {
        !confirmPassword.isEmpty && password != confirmPassword
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                Text("Register")
                    .font(.system(size: 64, weight: .bold))
                    .foregroundStyle(Color.brand)
                    .frame(maxWidth: .infinity)
                    .minimumScaleFactor(0.5)
                    .lineLimit(1)
                    .padding(.top, 48)
                    .padding(.bottom, 20)

                AuthField(prompt: "Email", text: $email, keyboard: .emailAddress, contentType: .emailAddress)
                AuthField(prompt: "First Name", text: $firstName, contentType: .givenName)
                AuthField(prompt: "Last Name", text: $lastName, contentType: .familyName)
                AuthField(prompt: "Username", text: $username, contentType: .username)
                AuthField(prompt: "Password", text: $password, isSecure: true, contentType: .newPassword)
                AuthField(prompt: "Confirm-Password", text: $confirmPassword, isSecure: true, contentType: .newPassword)

                if showMismatch {
                    Text("Passwords don’t match.")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(Color.inkSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                if let errorMessage {
                    Text(errorMessage)
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(Color.inkSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                Button("Register") {
                    Task { await submitRegistration() }
                }
                .buttonStyle(AuthButtonStyle())
                .disabled(!canSubmit || isSubmitting)
                .padding(.top, 12)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 24)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Color.canvas)
        .toolbarVisibility(.hidden, for: .navigationBar)
    }

    private func submitRegistration() async {
        isSubmitting = true
        errorMessage = nil
        defer { isSubmitting = false }
        do {
            try await model.register(
                email: email.trimmingCharacters(in: .whitespaces),
                firstName: firstName.trimmingCharacters(in: .whitespaces),
                lastName: lastName.trimmingCharacters(in: .whitespaces),
                username: trimmedUsername,
                password: password
            )
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

/// Forgot Password — emails a 6-digit code, then sets a new password with it and signs in.
struct ForgotPasswordView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var email = ""
    @State private var codeSent = false
    @State private var code = ""
    @State private var password = ""
    @State private var confirmPassword = ""
    @State private var isSubmitting = false
    @State private var errorMessage: String?

    private var trimmedEmail: String {
        email.trimmingCharacters(in: .whitespaces)
    }

    private var canSubmit: Bool {
        if !codeSent { return trimmedEmail.contains("@") }
        return code.count == 6 && !password.isEmpty && password == confirmPassword
    }

    private var showMismatch: Bool {
        !confirmPassword.isEmpty && password != confirmPassword
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                Text("Reset")
                    .font(.system(size: 64, weight: .bold))
                    .foregroundStyle(Color.brand)
                    .frame(maxWidth: .infinity)
                    .minimumScaleFactor(0.5)
                    .lineLimit(1)
                    .padding(.top, 64)
                    .padding(.bottom, 28)

                if codeSent {
                    Text("If \(trimmedEmail) has an account, we emailed it a 6-digit code. It works for 15 minutes.")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(Color.inkSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    AuthField(prompt: "6-Digit Code", text: $code, keyboard: .numberPad, contentType: .oneTimeCode)
                        .onChange(of: code) { _, newValue in
                            let digits = String(newValue.filter(\.isNumber).prefix(6))
                            if digits != newValue { code = digits }
                        }
                    AuthField(prompt: "New Password", text: $password, isSecure: true, contentType: .newPassword)
                    AuthField(prompt: "Confirm-Password", text: $confirmPassword, isSecure: true, contentType: .newPassword)

                    if showMismatch {
                        Text("Passwords don’t match.")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(Color.inkSecondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                } else {
                    AuthField(prompt: "Email", text: $email, keyboard: .emailAddress, contentType: .emailAddress)
                }

                if let errorMessage {
                    Text(errorMessage)
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(Color.inkSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                Button(codeSent ? "Reset Password" : "Send Code") {
                    Task { await submit() }
                }
                .buttonStyle(AuthButtonStyle())
                .disabled(!canSubmit || isSubmitting)
                .padding(.top, 8)

                Button(codeSent ? "Send a new code" : "Back to Login") {
                    if codeSent {
                        codeSent = false
                        code = ""
                        errorMessage = nil
                    } else {
                        dismiss()
                    }
                }
                .font(.title3.bold())
                .foregroundStyle(Color.ink)
                .frame(maxWidth: .infinity)
                .padding(.top, 4)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 24)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Color.canvas)
        .toolbarVisibility(.hidden, for: .navigationBar)
    }

    private func submit() async {
        isSubmitting = true
        errorMessage = nil
        defer { isSubmitting = false }
        do {
            if codeSent {
                try await model.resetPassword(email: trimmedEmail, code: code, password: password)
            } else {
                try await API.requestPasswordReset(email: trimmedEmail)
                codeSent = true
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

/// Shown after sign-up (and on launch) until the emailed 6-digit code is entered.
struct VerifyEmailView: View {
    @Environment(AppModel.self) private var model
    @State private var code = ""
    @State private var isSubmitting = false
    @State private var message: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                Text("Verify")
                    .font(.system(size: 64, weight: .bold))
                    .foregroundStyle(Color.brand)
                    .frame(maxWidth: .infinity)
                    .minimumScaleFactor(0.5)
                    .lineLimit(1)
                    .padding(.top, 64)
                    .padding(.bottom, 28)

                Text("We emailed a 6-digit code to \(model.session?.email ?? "your email"). Enter it to start ordering and running.")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(Color.inkSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)

                AuthField(prompt: "6-Digit Code", text: $code, keyboard: .numberPad, contentType: .oneTimeCode)
                    .onChange(of: code) { _, newValue in
                        let digits = String(newValue.filter(\.isNumber).prefix(6))
                        if digits != newValue { code = digits }
                    }

                if let message {
                    Text(message)
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(Color.inkSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                Button("Verify Email") {
                    Task { await verify() }
                }
                .buttonStyle(AuthButtonStyle())
                .disabled(code.count != 6 || isSubmitting)
                .padding(.top, 8)

                Button("Send a new code") {
                    Task { await resend() }
                }
                .font(.title3.bold())
                .foregroundStyle(Color.ink)
                .frame(maxWidth: .infinity)
                .disabled(isSubmitting)
                .padding(.top, 4)

                Button("Sign out") {
                    model.signOut()
                }
                .font(.body.weight(.semibold))
                .foregroundStyle(Color.inkSecondary)
                .frame(maxWidth: .infinity)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 24)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Color.canvas)
    }

    private func verify() async {
        isSubmitting = true
        message = nil
        defer { isSubmitting = false }
        do {
            try await model.verifyEmail(code: code)
        } catch {
            message = error.localizedDescription
        }
    }

    private func resend() async {
        isSubmitting = true
        defer { isSubmitting = false }
        do {
            try await model.resendVerification()
            code = ""
            message = "Sent. A new code can go out once a minute, and the newest one is the one that works."
        } catch {
            message = error.localizedDescription
        }
    }
}

private struct AuthField: View {
    let prompt: String
    @Binding var text: String
    var isSecure = false
    var keyboard: UIKeyboardType = .default
    var contentType: UITextContentType?

    var body: some View {
        Group {
            if isSecure {
                SecureField("", text: $text, prompt: promptLabel)
            } else {
                TextField("", text: $text, prompt: promptLabel)
            }
        }
        .font(.title3.weight(.semibold))
        .foregroundStyle(Color.ink)
        .textInputAutocapitalization(usesWords ? .words : .never)
        .autocorrectionDisabled()
        .keyboardType(keyboard)
        .textContentType(contentType)
        .padding(.horizontal, 16)
        .frame(minHeight: 56)
        .background(.white, in: .rect(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.hairline))
    }

    private var usesWords: Bool {
        contentType == .givenName || contentType == .familyName
    }

    private var promptLabel: Text {
        Text(prompt).foregroundStyle(Color.inkSecondary)
    }
}

/// Green full-width auth button. Taller and rounder than the in-app CTA, matching 01A and 01B.
private struct AuthButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.title3.bold())
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity, minHeight: 58)
            .background(Color.brand, in: .rect(cornerRadius: 18))
            .opacity(isEnabled ? (configuration.isPressed ? 0.85 : 1) : 0.5)
    }
}

#Preview("Login") {
    LoginView().environment(AppModel())
}

#Preview("Register") {
    NavigationStack { RegisterView() }.environment(AppModel())
}

#Preview("Forgot Password") {
    NavigationStack { ForgotPasswordView() }.environment(AppModel())
}

#Preview("Verify Email") {
    VerifyEmailView().environment(AppModel())
}
