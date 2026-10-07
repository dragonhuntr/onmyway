import SwiftUI

/// Where orders are delivered: a campus building, room, and a note for the runner.
struct DestinationPicker: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var buildingID = ""
    @State private var room = ""
    @State private var note = ""
    @State private var isSaving = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("Building") {
                    Picker("Building", selection: $buildingID) {
                        Text("Choose…").tag("")
                        ForEach(model.buildings) { Text($0.name).tag($0.id) }
                    }
                }
                Section("Room") {
                    TextField("Room 174", text: $room)
                }
                Section {
                    TextField("Text me when you arrive.", text: $note, axis: .vertical)
                } header: {
                    Text("Note for your runner")
                } footer: {
                    if let errorMessage { Text(errorMessage) }
                }
            }
            .navigationTitle("Deliver to")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { Task { await save() } }
                        .disabled(buildingID.isEmpty || isSaving)
                }
            }
            .task {
                await model.loadBuildings()
                if let destination = model.destination {
                    buildingID = destination.buildingId
                    room = destination.room
                    note = destination.note
                }
            }
        }
        .tint(.brand)
    }

    private func save() async {
        isSaving = true
        defer { isSaving = false }
        do {
            try await model.setDestination(buildingID: buildingID, room: room, note: note)
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

/// Add or edit a walk the runner is already taking.
struct TripEditor: View {
    let trip: Trip?
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var from = ""
    @State private var to = ""
    @State private var leaveAt = Date.now.addingTimeInterval(10 * 60)
    @State private var note = ""
    @State private var isSaving = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("From", selection: $from) {
                        Text("Choose…").tag("")
                        ForEach(model.buildings) { Text($0.name).tag($0.id) }
                    }
                    Picker("To", selection: $to) {
                        Text("Choose…").tag("")
                        ForEach(model.buildings) { Text($0.name).tag($0.id) }
                    }
                    DatePicker("Leaving", selection: $leaveAt, in: Date.now.addingTimeInterval(-3600)...)
                }
                Section {
                    TextField("a 10:00 lecture", text: $note)
                } header: {
                    Text("What’s it for? (optional)")
                } footer: {
                    Text(errorMessage ?? "Shown to students: “Already heading to … for a 10:00 lecture.”")
                }
                if let trip {
                    Section {
                        Button("Remove trip", role: .destructive) {
                            Task {
                                try? await model.deleteTrip(trip)
                                dismiss()
                            }
                        }
                    }
                }
            }
            .navigationTitle(trip == nil ? "Add a trip" : "Edit route")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { Task { await save() } }
                        .disabled(from.isEmpty || to.isEmpty || from == to || isSaving)
                }
            }
            .task {
                await model.loadBuildings()
                if let trip {
                    from = trip.from.id
                    to = trip.to.id
                    leaveAt = trip.leaveAt
                    note = trip.note
                }
            }
        }
        .tint(.brand)
    }

    private func save() async {
        isSaving = true
        defer { isSaving = false }
        do {
            try await model.saveTrip(id: trip?.id, from: from, to: to, leaveAt: leaveAt, note: note)
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
