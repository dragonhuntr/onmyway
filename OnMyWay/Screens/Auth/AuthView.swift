import SwiftUI

/// 01A_Login — checks the username and password against Cloudflare D1.
struct LoginView: View {
    @Environment(AppModel.self) private var model
    @State private var username = ""
    @State private var password = ""
    @State private var showForgotPassword = false
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
                        Button("Forgot Password?") { showForgotPassword = true }
                            .font(.title3.bold())
                            .foregroundStyle(Color.ink)
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
            .alert("Forgot Password?", isPresented: $showForgotPassword) {
                Button("OK", role: .cancel) {}
            } message: {
                Text("Password reset is not available yet. Create a new account if you do not remember this password.")
            }
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
