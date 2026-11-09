#if DEBUG
import Shared
import SwiftUI

struct CourseCatalogStaticPreview: View {
    var snapshot: CourseCatalogSnapshot = IosComposePreviewHost.shared.catalogSnapshot
    var isCompact = false

    var body: some View {
        CourseCatalogResultsView(
            snapshot: snapshot,
            selectedSerialNumbers: Set(snapshot.courses.prefix(1).map(\.serialNumber)),
            canEditPlan: true,
            isCompact: isCompact,
            onShowFilters: {},
            onRetry: {},
            onCourseDetails: { _ in },
            onToggleCourse: { _ in }
        )
        .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea())
    }
}

private extension CourseCatalogSnapshot {
    static func preview(isLoading: Bool, failureMessage: String?) -> CourseCatalogSnapshot {
        let populated = IosComposePreviewHost.shared.catalogSnapshot
        return populated.doCopy(
            semester: populated.semester,
            query: "",
            courses: [],
            filters: [],
            suggestions: [],
            filterOptions: populated.filterOptions,
            isLoading: isLoading,
            failureMessage: failureMessage
        )
    }
}

#Preview("Catalog results · populated") {
    CourseCatalogStaticPreview()
}

#Preview("Catalog results · empty") {
    CourseCatalogStaticPreview(
        snapshot: .preview(isLoading: false, failureMessage: nil)
    )
}

#Preview("Catalog results · loading") {
    CourseCatalogStaticPreview(
        snapshot: .preview(isLoading: true, failureMessage: nil)
    )
}

#Preview("Catalog results · failure") {
    CourseCatalogStaticPreview(
        snapshot: .preview(isLoading: false, failureMessage: "目前無法連線，請確認網路狀態後再試一次。")
    )
}

#Preview("Catalog results · populated · dark") {
    CourseCatalogStaticPreview()
        .preferredColorScheme(.dark)
}

#Preview("Catalog results · compact") {
    CourseCatalogStaticPreview(isCompact: true)
}
#endif
