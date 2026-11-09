import Shared
import SwiftUI

private enum HomeSection: Hashable {
    case announcements
    case eeclass
}

private struct AnnouncementWebDestination: Hashable {
    let title: String
    let url: String
}

struct HomeView<CourseDestination: View>: View {
    var sharedHost: IosSharedHost?
    let loadCourses: () async throws -> EeclassCourses
    @ViewBuilder let courseDestination: (EeclassCourseLink) -> CourseDestination
    let onOpenSettings: () -> Void

    @State private var selectedSection: HomeSection = .announcements
    @State private var courses: EeclassCourses?
    @State private var reloadID = 0
    @State private var selectedWebDestination: AnnouncementWebDestination?
    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(spacing: 0) {
            if sharedHost != nil {
                Picker("首頁分頁", selection: $selectedSection) {
                    Text("校園與系所公告").tag(HomeSection.announcements)
                    Text("ee-class 課程").tag(HomeSection.eeclass)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal)
                .padding(.vertical, 8)
            }

            if let sharedHost, selectedSection == .announcements {
                AnnouncementComposeView(sharedHost: sharedHost) { title, url in
                    if url.hasPrefix("https://") {
                        selectedWebDestination = AnnouncementWebDestination(title: title, url: url)
                    } else if let parsedURL = URL(string: url) {
                        openURL(parsedURL)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                eeclassContent
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .navigationTitle("首頁")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("設定", systemImage: "gear", action: onOpenSettings)
                    .labelStyle(.iconOnly)
            }
        }
        // Value-based links only: mixing them with destination-based links in one stack re-pushes screens.
        .navigationDestination(for: EeclassCourseLink.self) { courseDestination($0) }
        .navigationDestination(item: $selectedWebDestination) { destination in
            if let sharedHost {
                WebPageView(sharedHost: sharedHost, title: destination.title, url: destination.url)
            }
        }
        .task(id: reloadID) { await load() }
    }

    @ViewBuilder
    private var eeclassContent: some View {
        if let courses {
            switch courses.status {
            case .loaded where courses.courses.isEmpty:
                ContentUnavailableView("沒有 ee-class 課程", systemImage: "books.vertical")
            case .loaded:
                List {
                    Section("ee-class 課程") {
                        ForEach(courses.courses, id: \.number) { course in
                            NavigationLink(
                                course.name,
                                value: EeclassCourseLink(number: course.number, name: course.name)
                            )
                        }
                    }
                }
                .refreshable { await load() }
            case .needsPortal:
                EeclassUnavailableView(
                    title: "需要登入 Portal",
                    systemImage: "person.crop.circle.badge.exclamationmark",
                    description: "在 Portal 分頁登入後再重新整理。"
                ) { reloadID += 1 }
            case .unavailable:
                EeclassUnavailableView(
                    title: "無法載入 ee-class 課程",
                    systemImage: "wifi.exclamationmark",
                    description: "請稍後再試。"
                ) { reloadID += 1 }
            }
        } else {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private func load() async {
        guard let result = try? await loadCourses() else { return }
        courses = result
    }
}

private struct AnnouncementComposeView: UIViewControllerRepresentable {
    let sharedHost: IosSharedHost
    let onOpenUrl: (String, String) -> Void

    func makeUIViewController(context: Context) -> some UIViewController {
        sharedHost.announcementScreenController(onOpenUrl: onOpenUrl)
    }

    func updateUIViewController(_ uiViewController: UIViewControllerType, context: Context) {
    }
}
