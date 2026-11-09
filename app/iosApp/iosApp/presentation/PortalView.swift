import Shared
import SwiftUI

struct PortalView: View {
    let sharedHost: IosSharedHost
    let onOpenSettings: () -> Void

    @State private var bridge: PortalBridge
    @State private var account: PortalAccount?
    @State private var favoriteIds: [String]?
    @State private var query = ""
    @State private var editMode = EditMode.inactive
    // Pushed without a NavigationLink: the sign-in page and destinations opened from intents.
    @State private var pushedDestination: PortalDestination?
    @Environment(AppRouter.self) private var router
    @Environment(\.scenePhase) private var scenePhase

    init(sharedHost: IosSharedHost, onOpenSettings: @escaping () -> Void) {
        self.sharedHost = sharedHost
        self.onOpenSettings = onOpenSettings
        _bridge = State(initialValue: sharedHost.portalBridge())
    }

    private var isSearching: Bool {
        !query.trimmingCharacters(in: .whitespaces).isEmpty
    }

    var body: some View {
        let home = bridge.home(favoriteIds: favoriteIds ?? [], query: query)
        let favoriteSet = Set(favoriteIds ?? [])

        List {
            if let account {
                Section {
                    PortalAccountRow(account: account, onAction: perform)
                }
            }

            // Wait for the stored favorites so the defaults never flash before a customized list.
            if favoriteIds != nil, !isSearching || !home.favorites.isEmpty {
                Section {
                    ForEach(home.favorites, id: \.id) { item in
                        shortcutRow(item, isFavorite: true)
                    }
                    .onMove(perform: isSearching ? nil : { moveFavorites(home.favorites, from: $0, to: $1) })
                    .onDelete(perform: isSearching ? nil : { deleteFavorites(home.favorites, at: $0) })
                } header: {
                    Text("常用")
                } footer: {
                    if home.favorites.isEmpty {
                        Text("點「編輯」後按 ＋，即可把服務加入常用。")
                    }
                }
            }

            ForEach(home.sections, id: \.title) { section in
                Section(section.title) {
                    ForEach(section.items, id: \.id) { item in
                        if editMode.isEditing {
                            addFavoriteRow(item, isFavorite: favoriteSet.contains(item.id))
                        } else {
                            shortcutRow(item, isFavorite: favoriteSet.contains(item.id))
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .environment(\.editMode, $editMode)
        .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "搜尋服務")
        .overlay {
            if isSearching && home.isEmpty {
                ContentUnavailableView.search(text: query)
            }
        }
        .navigationTitle("Portal")
        .navigationDestination(for: PortalDestination.self) { destination in
            WebPageView(sharedHost: sharedHost, title: destination.title, url: destination.url)
        }
        .navigationDestination(item: $pushedDestination) { destination in
            WebPageView(sharedHost: sharedHost, title: destination.title, url: destination.url)
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(editMode.isEditing ? "完成" : "編輯") {
                    withAnimation { editMode = editMode.isEditing ? .inactive : .active }
                }
                .disabled(isSearching)
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button("設定", systemImage: "gear", action: onOpenSettings)
                    .labelStyle(.iconOnly)
            }
        }
        .task {
            for await ids in bridge.favoriteIds {
                favoriteIds = ids
            }
        }
        .task {
            for await value in bridge.account {
                account = value
            }
        }
        .task(id: router.pendingRoute) {
            if let destination = router.take(\.portal) {
                pushedDestination = destination
            }
        }
        .onAppear { bridge.refresh() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { bridge.refresh() }
        }
    }

    private func shortcutRow(_ item: PortalShortcutItem, isFavorite: Bool) -> some View {
        NavigationLink(value: PortalDestination(title: item.destination.title, url: item.destination.url)) {
            Label {
                Text(item.label)
            } icon: {
                PortalShortcutIconView(icon: item.icon, tint: item.tint)
            }
        }
        .contextMenu { favoriteButton(item, isFavorite: isFavorite) }
        .swipeActions(edge: .leading) {
            favoriteButton(item, isFavorite: isFavorite)
                .tint(.yellow)
        }
    }

    /// A catalog row while editing: a leading insert control, like the system's delete control on favorites.
    private func addFavoriteRow(_ item: PortalShortcutItem, isFavorite: Bool) -> some View {
        HStack(spacing: 12) {
            Button {
                withAnimation { bridge.addFavorite(id: item.id) }
            } label: {
                Image(systemName: isFavorite ? "checkmark.circle.fill" : "plus.circle.fill")
                    .font(.title3)
                    .symbolRenderingMode(.palette)
                    .foregroundStyle(.white, isFavorite ? Color(.systemGray3) : .green)
            }
            .buttonStyle(.borderless)
            .disabled(isFavorite)
            .accessibilityLabel(isFavorite ? "已在常用" : "加入常用")
            Label {
                Text(item.label)
            } icon: {
                PortalShortcutIconView(icon: item.icon, tint: item.tint)
            }
        }
    }

    @ViewBuilder
    private func favoriteButton(_ item: PortalShortcutItem, isFavorite: Bool) -> some View {
        if isFavorite {
            Button("從常用移除", systemImage: "star.slash") { bridge.removeFavorite(id: item.id) }
        } else {
            Button("加入常用", systemImage: "star") { bridge.addFavorite(id: item.id) }
        }
    }

    private func moveFavorites(_ favorites: [PortalShortcutItem], from source: IndexSet, to destination: Int) {
        var ids = favorites.map(\.id)
        ids.move(fromOffsets: source, toOffset: destination)
        saveFavorites(ids)
    }

    private func deleteFavorites(_ favorites: [PortalShortcutItem], at offsets: IndexSet) {
        var ids = favorites.map(\.id)
        ids.remove(atOffsets: offsets)
        saveFavorites(ids)
    }

    private func saveFavorites(_ ids: [String]) {
        // Show the change now; the store confirms it a moment later.
        favoriteIds = ids
        bridge.setFavoriteIds(ids: ids)
    }

    private func perform(_ action: PortalAccountAction) {
        switch action {
        case .signIn:
            Task {
                guard let url = try? await bridge.signInUrl() else { return }
                pushedDestination = PortalDestination(title: "Portal 登入", url: url)
            }
        case .signOut:
            bridge.signOut()
        case .retry:
            bridge.refresh()
        }
    }
}

private struct PortalAccountRow: View {
    let account: PortalAccount
    let onAction: (PortalAccountAction) -> Void

    var body: some View {
        HStack(spacing: 12) {
            statusIcon
                .font(.title2)
                .frame(width: 30)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(account.title)
                    .font(.headline)
                if let detail = account.detail {
                    Text(detail)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer(minLength: 8)
            ForEach(Array(account.actions.enumerated()), id: \.offset) { index, action in
                if index == 0 && action == .signIn {
                    Button(action.title) { onAction(action) }
                        .buttonStyle(.borderedProminent)
                } else {
                    Button(action.title) { onAction(action) }
                        .buttonStyle(.bordered)
                }
            }
        }
        .buttonBorderShape(.capsule)
        .padding(.vertical, 4)
    }

    @ViewBuilder
    private var statusIcon: some View {
        switch account.tone {
        case .checking:
            ProgressView()
        case .signedIn:
            Image(systemName: "checkmark.circle.fill")
                .foregroundStyle(.green)
        case .signedOut:
            Image(systemName: "person.crop.circle")
                .foregroundStyle(.secondary)
        case .attention:
            Image(systemName: "exclamationmark.circle.fill")
                .foregroundStyle(.orange)
        }
    }
}

private struct PortalShortcutIconView: View {
    let icon: PortalShortcutIcon
    let tint: PortalShortcutTint

    var body: some View {
        Image(systemName: icon.systemImage)
            .font(.system(size: 15, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: 30, height: 30)
            .background(tint.color.gradient, in: .rect(cornerRadius: 7, style: .continuous))
            .accessibilityHidden(true)
    }
}

private extension PortalAccountAction {
    var title: String {
        switch self {
        case .signIn: "登入"
        case .signOut: "登出"
        case .retry: "重試"
        }
    }
}

private extension PortalShortcutTint {
    var color: Color {
        switch self {
        case .blue: .blue
        case .green: .green
        case .orange: .orange
        case .purple: .purple
        case .teal: .teal
        }
    }
}

extension PortalShortcutIcon {
    var systemImage: String {
        switch self {
        case .accountBalance: "building.columns"
        case .apps: "square.grid.2x2"
        case .badge: "person.text.rectangle"
        case .book: "book"
        case .creditCardOff: "creditcard.trianglebadge.exclamationmark"
        case .event: "calendar"
        case .grade: "chart.bar.doc.horizontal"
        case .headset: "headphones"
        case .localLibrary: "books.vertical"
        case .mail: "envelope"
        case .requestQuote: "doc.text"
        case .savings: "banknote"
        case .school: "graduationcap"
        case .supportAgent: "questionmark.bubble"
        case .warning: "exclamationmark.triangle"
        case .wifi: "wifi"
        case .workspaces: "doc.on.doc"
        case .workspacePremium: "rosette"
        }
    }
}
