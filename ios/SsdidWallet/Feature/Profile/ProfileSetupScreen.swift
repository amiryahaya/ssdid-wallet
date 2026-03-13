import SwiftUI

struct ProfileSetupScreen: View {
    @Environment(AppRouter.self) private var router
    @EnvironmentObject private var services: ServiceContainer

    let isEditing: Bool

    @State private var name = ""
    @State private var email = ""
    @State private var originalEmail = ""
    @State private var nameError: String?
    @State private var emailError: String?
    @State private var saving = false
    @State private var loaded = false

    init(isEditing: Bool = false) {
        self.isEditing = isEditing
    }

    private var isValid: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty &&
        !email.trimmingCharacters(in: .whitespaces).isEmpty &&
        email.contains("@") && email.contains(".")
    }

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack(spacing: 4) {
                if isEditing {
                    Button { router.pop() } label: {
                        Image(systemName: "chevron.left")
                            .foregroundStyle(Color.textPrimary)
                            .font(.system(size: 20))
                    }
                    .padding(.leading, 8)
                } else {
                    Spacer().frame(width: 16)
                }

                Text(isEditing ? "Edit Profile" : "Set Up Your Profile")
                    .font(.ssdidHeadline)
                    .foregroundStyle(Color.textPrimary)

                Spacer()
            }
            .padding(.vertical, 12)
            .padding(.trailing, 20)

            // Form
            VStack(alignment: .leading, spacing: 0) {
                Text("This information can be shared when you sign in to services using your SSDID wallet.")
                    .font(.system(size: 14))
                    .foregroundStyle(Color.textSecondary)
                    .padding(.bottom, 24)

                // Name field
                Text("NAME *")
                    .font(.ssdidCaption)
                    .foregroundStyle(Color.textTertiary)

                Spacer().frame(height: 6)

                TextField("", text: $name, prompt: Text("Your full name").foregroundStyle(Color.textTertiary))
                    .textFieldStyle(.plain)
                    .font(.ssdidBody)
                    .foregroundStyle(Color.textPrimary)
                    .padding(14)
                    .background(Color.bgCard)
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(nameError != nil ? Color.danger : Color.ssdidBorder, lineWidth: 1)
                    )
                    .onChange(of: name) { _, newValue in
                        nameError = newValue.trimmingCharacters(in: .whitespaces).isEmpty ? "Name is required" : nil
                    }

                if let nameError {
                    Text(nameError)
                        .font(.ssdidCaption)
                        .foregroundStyle(Color.danger)
                        .padding(.top, 4)
                }

                Spacer().frame(height: 12)

                // Email field
                Text("EMAIL *")
                    .font(.ssdidCaption)
                    .foregroundStyle(Color.textTertiary)

                Spacer().frame(height: 6)

                TextField("", text: $email, prompt: Text("your@email.com").foregroundStyle(Color.textTertiary))
                    .textFieldStyle(.plain)
                    .font(.ssdidBody)
                    .foregroundStyle(Color.textPrimary)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .padding(14)
                    .background(Color.bgCard)
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(emailError != nil ? Color.danger : Color.ssdidBorder, lineWidth: 1)
                    )
                    .onChange(of: email) { _, newValue in
                        if newValue.trimmingCharacters(in: .whitespaces).isEmpty {
                            emailError = "Email is required"
                        } else if !newValue.contains("@") || !newValue.contains(".") {
                            emailError = "Invalid email format"
                        } else {
                            emailError = nil
                        }
                    }

                if let emailError {
                    Text(emailError)
                        .font(.ssdidCaption)
                        .foregroundStyle(Color.danger)
                        .padding(.top, 4)
                }

                Spacer().frame(height: 8)

                Text("* Required")
                    .font(.ssdidCaption)
                    .foregroundStyle(Color.textTertiary)

                Spacer()
            }
            .padding(.horizontal, 20)

            // Footer
            VStack(spacing: 4) {
                Button {
                    if isEditing {
                        saveAndContinue()
                    } else {
                        router.push(.emailVerification(email: email))
                    }
                } label: {
                    Text(isEditing ? "Save" : "Continue")
                }
                .buttonStyle(.ssdidPrimary(enabled: isValid && !saving))
                .disabled(!isValid || saving)

                if !isEditing {
                    Text("You can edit this later in Settings.")
                        .font(.ssdidCaption)
                        .foregroundStyle(Color.textTertiary)
                        .padding(.top, 4)
                        .padding(.bottom, 8)
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
        }
        .background(Color.bgPrimary)
        .task {
            guard isEditing, !loaded else { return }
            loaded = true
            let profileManager = ProfileManager(vault: services.vault)
            let claims = await profileManager.getProfileClaims()
            name = claims["name"] ?? ""
            email = claims["email"] ?? ""
            originalEmail = claims["email"] ?? ""
        }
    }

    private func saveAndContinue() {
        saving = true
        let trimmedEmail = email.trimmingCharacters(in: .whitespaces)
        let emailChanged = trimmedEmail.lowercased() != originalEmail.trimmingCharacters(in: .whitespaces).lowercased()

        Task {
            let profileManager = ProfileManager(vault: services.vault)
            try? await profileManager.saveProfile(name: name.trimmingCharacters(in: .whitespaces), email: trimmedEmail)
            saving = false

            if emailChanged {
                router.push(.emailVerification(email: trimmedEmail, isEditing: true))
            } else {
                router.pop()
            }
        }
    }
}
