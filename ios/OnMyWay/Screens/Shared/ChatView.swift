import SwiftUI

/// In-app chat between a customer and their runner. Polls for new messages while open.
struct ChatView: View {
    let orderID: String
    let title: String
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var messages: [ChatMessage] = []
    @State private var draft = ""
    @State private var isSending = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 8) {
                        if messages.isEmpty {
                            Text("Messages stay in the app. Say hi!")
                                .font(.footnote).foregroundStyle(Color.inkSecondary)
                                .padding(.top, 40)
                        }
                        ForEach(messages) { message in
                            bubble(message).id(message.id)
                        }
                    }
                    .padding(16)
                }
                .onChange(of: messages.last?.id) { _, last in
                    if let last { withAnimation { proxy.scrollTo(last, anchor: .bottom) } }
                }
            }
            .background(Color.canvas)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) { composer }
            .task { await poll() }
        }
        .tint(.brand)
    }

    private func bubble(_ message: ChatMessage) -> some View {
        HStack {
            if message.fromMe { Spacer(minLength: 48) }
            VStack(alignment: message.fromMe ? .trailing : .leading, spacing: 2) {
                Text(message.body)
                    .font(.subheadline)
                    .foregroundStyle(message.fromMe ? .white : Color.ink)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(message.fromMe ? Color.brand : .white, in: .rect(cornerRadius: 16))
                    .overlay {
                        if !message.fromMe { RoundedRectangle(cornerRadius: 16).strokeBorder(Color.hairline) }
                    }
                Text(message.createdAt.shortTime).font(.caption2).foregroundStyle(Color.inkSecondary)
            }
            if !message.fromMe { Spacer(minLength: 48) }
        }
        .accessibilityElement(children: .combine)
    }

    private var composer: some View {
        VStack(spacing: 6) {
            if let errorMessage {
                Text(errorMessage).font(.caption).foregroundStyle(Color.inkSecondary)
            }
            HStack(spacing: 8) {
                TextField("Message", text: $draft, axis: .vertical)
                    .lineLimit(1...4)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(Color.canvas, in: .rect(cornerRadius: 20))
                Button {
                    Task { await send() }
                } label: {
                    Image(systemName: "arrow.up")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(.white)
                        .frame(width: 40, height: 40)
                        .background(Color.brand, in: .circle)
                }
                .buttonStyle(.plain)
                .disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isSending)
                .accessibilityLabel("Send")
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(.white)
        .overlay(alignment: .top) { Rectangle().fill(Color.hairline).frame(height: 1) }
    }

    private func poll() async {
        while !Task.isCancelled {
            if let new = try? await model.messages(for: orderID, after: messages.last?.createdAt) {
                append(new)
            }
            try? await Task.sleep(for: .seconds(3))
        }
    }

    private func send() async {
        let body = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty else { return }
        isSending = true
        defer { isSending = false }
        do {
            append([try await model.send(body, to: orderID)])
            draft = ""
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func append(_ new: [ChatMessage]) {
        let known = Set(messages.map(\.id))
        messages += new.filter { !known.contains($0.id) }
    }
}
