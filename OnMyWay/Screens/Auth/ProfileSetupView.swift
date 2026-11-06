import CoreTransferable
import PhotosUI
import SwiftUI
import UniformTypeIdentifiers

/// Shown once after registration, before the main tabs.
struct ProfileSetupView: View {
    @Environment(AppModel.self) private var model
    @State private var role: AppRole?
    @State private var dropoffBuilding = ""
    @State private var dropoffRoom = ""
    @State private var handoffNote = Campus.handoffNotes[0]
    @State private var walkingTo = ""
    @State private var photoItem: PhotosPickerItem?
    @State private var photoData: Data?

    private var canContinue: Bool {
        guard let role else { return false }
        if role.ordersFood {
            if dropoffBuilding.isEmpty || dropoffRoom.trimmingCharacters(in: .whitespaces).isEmpty {
                return false
            }
        }
        if role.delivers && walkingTo.isEmpty { return false }
        return true
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                header
                roleSection
                campusSection
                if role?.ordersFood == true { dropoffSection }
                if role?.delivers == true { routeSection }
            }
            .padding(.horizontal, 20)
            .padding(.top, 24)
            .padding(.bottom, 24)
            .animation(.snappy, value: role)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Color.canvas)
        .toolbarVisibility(.hidden, for: .navigationBar)
        .footer {
            Button("Continue") { save() }
                .buttonStyle(.primaryCTA)
                .disabled(!canContinue)
        }
        .onChange(of: photoItem) { _, item in
            guard let item else { return }
            Task {
                photoData = try? await item.loadTransferable(type: ProfileImage.self)?.data
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 16) {
            PhotosPicker(selection: $photoItem, matching: .images) {
                ZStack(alignment: .bottomTrailing) {
                    avatar
                    Image(systemName: "plus.circle.fill")
                        .font(.title2)
                        .symbolRenderingMode(.palette)
                        .foregroundStyle(.white, Color.brand)
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Add profile photo")

            VStack(alignment: .leading, spacing: 6) {
                Text("Set up your profile")
                    .font(.system(size: 34, weight: .bold))
                    .foregroundStyle(Color.brand)
                Text("\(firstName), choose how you’ll use On My Way. You can order, deliver, or both.")
                    .font(.subheadline)
                    .foregroundStyle(Color.inkSecondary)
            }
        }
    }

    private var avatar: some View {
        Group {
            if let photoData, let image = UIImage(data: photoData) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                Text(initial)
                    .font(.largeTitle.weight(.semibold))
                    .foregroundStyle(Color.brandDeep)
            }
        }
        .frame(width: 88, height: 88)
        .background(Color.amberSoft, in: .circle)
        .clipShape(.circle)
    }

    private var roleSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Eyebrow("I WANT TO")
            ForEach(AppRole.allCases) { option in
                RoleOption(role: option, isSelected: role == option) {
                    role = option
                }
            }
        }
    }

    private var campusSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Eyebrow("CAMPUS")
            Text(Campus.name)
                .font(.callout.weight(.semibold))
                .foregroundStyle(Color.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
                .card()
        }
    }

    private var dropoffSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Eyebrow("DELIVER TO")
            buildingGrid(selection: dropoffBuilding) { dropoffBuilding = $0 }
            TextField("Room, lobby, or meetup spot", text: $dropoffRoom)
                .font(.subheadline)
                .textInputAutocapitalization(.words)
                .padding(.horizontal, 16)
                .frame(minHeight: 48)
                .background(.white, in: .rect(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Color.hairline))
            VStack(alignment: .leading, spacing: 8) {
                Text("Handoff")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.inkSecondary)
                ForEach(Campus.handoffNotes, id: \.self) { note in
                    ChipToggle(title: note, isSelected: handoffNote == note, fillsWidth: true) {
                        handoffNote = note
                    }
                }
            }
        }
    }

    private var routeSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Eyebrow("I USUALLY WALK TO")
            Text("We’ll offer you orders heading to this building.")
                .font(.footnote)
                .foregroundStyle(Color.inkSecondary)
            buildingGrid(selection: walkingTo) { walkingTo = $0 }
        }
    }

    private func buildingGrid(selection: String, choose: @escaping (String) -> Void) -> some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
            ForEach(Campus.buildings, id: \.self) { building in
                ChipToggle(title: building, isSelected: selection == building, fillsWidth: true) {
                    choose(building)
                }
            }
        }
    }

    private var firstName: String {
        let name = model.session?.firstName.trimmingCharacters(in: .whitespaces) ?? ""
        return name.isEmpty ? "Hey" : name
    }

    private var initial: String {
        firstName.first.map { String($0).uppercased() } ?? "M"
    }

    private func save() {
        guard let role else { return }
        model.finishProfileSetup(
            UserProfile(
                role: role,
                campus: Campus.name,
                dropoffBuilding: role.ordersFood ? dropoffBuilding : "",
                dropoffRoom: role.ordersFood ? dropoffRoom.trimmingCharacters(in: .whitespaces) : "",
                handoffNote: role.ordersFood ? handoffNote : "",
                walkingTo: role.delivers ? walkingTo : ""
            ),
            photo: photoData
        )
    }
}

private struct RoleOption: View {
    let role: AppRole
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 4) {
                Text(role.title)
                    .font(.callout.weight(.semibold))
                    .foregroundStyle(Color.ink)
                Text(role.detail)
                    .font(.footnote)
                    .foregroundStyle(Color.inkSecondary)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(isSelected ? Color.brandSoft : .white, in: .rect(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(isSelected ? Color.brand : Color.hairline, lineWidth: isSelected ? 2 : 1)
            )
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

/// PhotosPicker hands back image bytes through this type.
private struct ProfileImage: Transferable {
    let data: Data

    static var transferRepresentation: some TransferRepresentation {
        DataRepresentation(importedContentType: .image) { data in
            ProfileImage(data: data)
        }
    }
}

#Preview {
    let model = AppModel()
    model.session = SessionUser(
        id: "preview", username: "maya", email: "maya@psu.edu", firstName: "Maya", lastName: "Chen"
    )
    model.needsProfileSetup = true
    return ProfileSetupView().environment(model)
}
