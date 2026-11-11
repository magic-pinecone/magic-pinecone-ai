import Shared
import SwiftUI

struct CourseCatalogView: View {
    let sharedHost: IosSharedHost
    let planBridge: CoursePlanBridge
    let selectedCourses: [CourseSummary]
    let canEditPlan: Bool
    let isCompact: Bool

    let semester: String
    @Binding var query: String
    let bridge: CourseSearchBridge
    let catalogSnapshot: CourseCatalogSnapshot?
    let onShowFilters: () -> Void

    private var selectedSerialNumbers: Set<String> {
        Set(selectedCourses.map(\.serialNumber))
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            CourseCatalogComposeView(
                sharedHost: sharedHost,
                bridge: bridge,
                planBridge: planBridge
            )
            .frame(width: 1, height: 1)
            .opacity(0)
            .accessibilityHidden(true)
            .allowsHitTesting(false)

            CourseCatalogResultsView(
                snapshot: catalogSnapshot,
                selectedSerialNumbers: selectedSerialNumbers,
                canEditPlan: canEditPlan,
                isCompact: isCompact,
                onShowFilters: onShowFilters,
                onRetry: submitSearch,
                onCourseDetails: { course in
                    planBridge.requestCourseDetails(semester: semester, course: course)
                },
                onToggleCourse: { course in
                    planBridge.requestToggleCourse(course: course)
                }
            )
        }
        .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea())
    }

    private func submitSearch() {
        bridge.submit(semester: semester, query: query)
    }
}

struct CourseCatalogResultsView: View {
    let snapshot: CourseCatalogSnapshot?
    let selectedSerialNumbers: Set<String>
    let canEditPlan: Bool
    let isCompact: Bool
    let onShowFilters: () -> Void
    let onRetry: () -> Void
    let onCourseDetails: (CourseSummary) -> Void
    let onToggleCourse: (CourseSummary) -> Void

    private var filterCount: Int {
        snapshot?.filters.count ?? 0
    }

    var body: some View {
        content.coursePlanningTopBar { header }
    }

    // The search field replaces the navigation toolbar on iPhone, so the filter button lives here.
    private var header: some View {
        HStack {
            if let snapshot, !snapshot.isLoading, snapshot.failureMessage == nil, !snapshot.courses.isEmpty {
                Text(MR.plurals().planning_search_result_count.localized(snapshot.courses.count))
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .coursePlanningGlassCapsule()
            }
            Spacer()
            Button(action: onShowFilters) {
                Label(
                    MR.strings().catalog_tab_filters.localized,
                    systemImage: filterCount > 0
                        ? "line.3.horizontal.decrease.circle.fill"
                        : "line.3.horizontal.decrease.circle"
                )
                .labelStyle(.iconOnly)
                .font(.title3)
                .foregroundStyle(filterCount > 0 ? Color.accentColor : Color.primary)
                .padding(5)
                .coursePlanningGlassCapsule()
            }
            .accessibilityValue("\(filterCount)")
            .accessibilityIdentifier("course-filters")
        }
        .padding(.horizontal, 16)
        .padding(.top, 4)
        .frame(minHeight: 44)
    }

    @ViewBuilder
    private var content: some View {
        if let snapshot {
            if snapshot.isLoading {
                CourseCatalogFeedbackView(
                    symbol: nil,
                    title: MR.strings().planning_search_loading.localized,
                    message: nil,
                    actionTitle: nil,
                    action: nil,
                    showsProgress: true
                )
            } else if let failureMessage = snapshot.failureMessage {
                CourseCatalogFeedbackView(
                    symbol: "exclamationmark.triangle",
                    title: MR.strings().planning_search_failed_title.localized,
                    message: failureMessage,
                    actionTitle: MR.strings().planning_search_reload.localized,
                    action: onRetry,
                    showsProgress: false
                )
            } else if snapshot.courses.isEmpty {
                CourseCatalogFeedbackView(
                    symbol: "magnifyingglass",
                    title: MR.strings().planning_search_empty_title.localized,
                    message: MR.strings().planning_search_empty_hint.localized,
                    actionTitle: nil,
                    action: nil,
                    showsProgress: false
                )
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: isCompact ? 8 : 12) {
                        ForEach(snapshot.courses, id: \.serialNumber) { course in
                            CourseCatalogCard(
                                course: course,
                                isSelected: selectedSerialNumbers.contains(course.serialNumber),
                                canEditPlan: canEditPlan,
                                isCompact: isCompact,
                                onOpenDetails: { onCourseDetails(course) },
                                onToggleSelection: { onToggleCourse(course) }
                            )
                            .courseEntityContext(
                                CourseIdentity(semester: snapshot.semester, serialNumber: course.serialNumber)
                            )
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 4)
                    .padding(.bottom, 24)
                }
                .coursePlanningSoftTopEdge()
            }
        } else {
            CourseCatalogFeedbackView(
                symbol: nil,
                title: MR.strings().planning_search_loading.localized,
                message: nil,
                actionTitle: nil,
                action: nil,
                showsProgress: true
            )
        }
    }
}

private struct CourseCatalogFeedbackView: View {
    let symbol: String?
    let title: String
    let message: String?
    let actionTitle: String?
    let action: (() -> Void)?
    let showsProgress: Bool

    var body: some View {
        VStack(spacing: 12) {
            if showsProgress {
                ProgressView()
                    .controlSize(.large)
                    .accessibilityLabel(MR.strings().planning_search_loading.localized)
            } else if let symbol {
                Image(systemName: symbol)
                    .font(.system(size: 30, weight: .regular))
                    .foregroundStyle(.secondary)
                    .accessibilityHidden(true)
            }

            Text(title)
                .font(.title3.weight(.semibold))
                .multilineTextAlignment(.center)

            if let message {
                Text(message)
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if let actionTitle, let action {
                Button(actionTitle, action: action)
                    .buttonStyle(.borderedProminent)
                    .padding(.top, 4)
            }
        }
        .padding(28)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
