import Shared
import SwiftUI

struct CourseCatalogCard: View {
    let course: CourseSummary
    let isSelected: Bool
    let canEditPlan: Bool
    let isCompact: Bool
    let onOpenDetails: () -> Void
    let onToggleSelection: () -> Void
    @State private var selectionSize: CGSize = .zero

    var body: some View {
        Button(action: onOpenDetails) {
            Group {
                if isCompact {
                    CourseCatalogCompactContent(course: course)
                        .padding(.trailing, max(44, selectionSize.width) + 8)
                        .frame(minHeight: max(44, selectionSize.height))
                } else {
                    VStack(alignment: .leading, spacing: 10) {
                        CourseCatalogHeading(course: course, isSelected: isSelected)
                        CourseCatalogInfoRail(course: course)

                        HStack(spacing: 8) {
                            Label(course.scheduleDescription, systemImage: "calendar")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                                .lineLimit(2)
                                .multilineTextAlignment(.leading)
                                .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                                .accessibilityIdentifier("course-schedule-\(course.serialNumber)")
                            // Reserve the trailing area for the independent selection button.
                            Color.clear.frame(
                                width: max(44, selectionSize.width),
                                height: max(44, selectionSize.height)
                            )
                        }
                    }
                }
            }
            .padding(isCompact ? 10 : 14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                Color(uiColor: .secondarySystemGroupedBackground),
                in: RoundedRectangle(cornerRadius: 16, style: .continuous)
            )
            .overlay {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(
                        Color.primary.opacity(isSelected ? 0.14 : 0.08),
                        lineWidth: 1
                    )
            }
            .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(CourseCatalogDetailsButtonStyle())
        .accessibilityIdentifier("course-details-\(course.serialNumber)")
        .accessibilityValue(
            isSelected
                ? MR.strings().planning_course_in_timetable.localized
                : MR.strings().planning_course_not_in_timetable.localized
        )
        .accessibilityHint(MR.strings().planning_timetable_open_course.localized)
        .overlay(alignment: isCompact ? .trailing : .bottomTrailing) {
            CourseCatalogSelectionControl(
                course: course,
                isSelected: isSelected,
                canEditPlan: canEditPlan,
                isCompact: isCompact,
                action: onToggleSelection
            )
            .onGeometryChange(for: CGSize.self) { proxy in
                proxy.size
            } action: { size in
                selectionSize = size
            }
            .padding(isCompact ? 10 : 14)
        }
        .animation(.easeInOut(duration: 0.18), value: isSelected)
    }
}

// A card opens only after a completed tap; paging drags must cancel activation.
private struct CourseCatalogDetailsButtonStyle: PrimitiveButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .onTapGesture {
                if isEnabled { configuration.trigger() }
            }
            .accessibilityAction {
                if isEnabled { configuration.trigger() }
            }
    }
}

private struct CourseCatalogCompactContent: View {
    let course: CourseSummary
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        let metadataLayout = dynamicTypeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 4))
            : AnyLayout(HStackLayout(spacing: 8))

        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                CourseCatalogTypeBadge(courseType: course.courseType)
                Text(course.title)
                    .font(.subheadline.weight(.medium))
                    .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 1)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            metadataLayout {
                Text(course.scheduleDescription)
                    .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 1)
                    .layoutPriority(1)
                    .accessibilityIdentifier("course-schedule-\(course.serialNumber)")
                Text(
                    course.teachers.filter { !$0.isEmpty }
                        .joined(separator: MR.strings().planning_list_separator.localized)
                )
                    .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(MR.strings().planning_credits_value.localized(course.credit.creditString))
                    .fixedSize()
            }
            .font(.caption)
            .foregroundStyle(.secondary)
        }
        .frame(minHeight: 44)
    }
}

private struct CourseCatalogTypeBadge: View {
    let courseType: CourseType

    var body: some View {
        if courseType != .unknown {
            Text(courseType.localizedLabel)
                .font(.caption.weight(.medium))
                .foregroundStyle(.secondary)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Color.primary.opacity(0.06), in: Capsule())
                .fixedSize(horizontal: true, vertical: false)
        }
    }
}

private struct CourseCatalogHeading: View {
    let course: CourseSummary
    let isSelected: Bool
    private var teacherSuffix: String {
        let separator = MR.strings().planning_list_separator.localized
        let teachers = course.teachers.filter { !$0.isEmpty }.joined(separator: separator)
        return teachers.isEmpty ? "" : " · \(teachers)"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .top, spacing: 5) {
                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(Color.accentColor)
                        .accessibilityHidden(true)
                }
                Text(course.title)
                    .font(.headline)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                Spacer(minLength: 8)
                CourseCatalogTypeBadge(courseType: course.courseType)
            }
            Text(
                """
                \(course.classNo) · \
                \(MR.strings().planning_credits_value.localized(course.credit.creditString))\(teacherSuffix)
                """
            )
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.leading)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct CourseCatalogInfoRail: View {
    let course: CourseSummary

    var body: some View {
        ScrollView(.horizontal) {
            HStack(spacing: 12) {
                CourseCatalogInfoBadge(symbol: "building.2", text: "\(course.collegeName)／\(course.departmentName)")
                CourseCatalogInfoBadge(
                    symbol: "person.3",
                    text: MR.strings().planning_course_admit_badge.localized(course.admitCnt, course.limitCnt)
                )
                CourseCatalogInfoBadge(symbol: "key", text: course.passwordCard.localizedLabel())
                if course.waitCnt > 0 {
                    CourseCatalogInfoBadge(
                        symbol: "clock",
                        text: MR.strings().planning_course_waitlist_badge.localized(course.waitCnt)
                    )
                }
            }
        }
        .scrollIndicators(.hidden)
        .accessibilityIdentifier("course-info-\(course.serialNumber)")
        .accessibilityHint(MR.strings().planning_course_info_swipe_hint.localized)
    }
}

private struct CourseCatalogInfoBadge: View {
    let symbol: String
    let text: String

    var body: some View {
        if !text.isEmpty {
            Label(text, systemImage: symbol)
                .font(.caption)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: true, vertical: false)
        }
    }
}

private struct CourseCatalogSelectionControl: View {
    let course: CourseSummary
    let isSelected: Bool
    let canEditPlan: Bool
    let isCompact: Bool
    let action: () -> Void

    var body: some View {
        Button(role: isSelected ? .destructive : nil, action: action) {
            Label(
                isSelected
                    ? MR.strings().planning_course_remove.localized
                    : MR.strings().planning_course_add.localized,
                systemImage: isSelected ? "minus" : "plus"
            )
                .labelStyle(isCompact ? CourseSelectionLabelStyle.iconOnly : .titleOnly)
                .frame(minWidth: isCompact ? 28 : 44, minHeight: 28)
        }
        .buttonStyle(CourseSelectionButtonStyle(isSelected: isSelected))
        .disabled(!canEditPlan)
        .accessibilityIdentifier("course-toggle-\(course.serialNumber)")
        .accessibilityLabel(
            isSelected
                ? MR.strings().planning_course_remove_from_timetable_labeled.localized(course.title, course.classNo)
                : MR.strings().planning_course_add_to_timetable_labeled.localized(course.title, course.classNo)
        )
    }
}

private enum CourseSelectionLabelStyle: LabelStyle {
    case iconOnly
    case titleOnly

    @ViewBuilder
    func makeBody(configuration: Configuration) -> some View {
        if self == .iconOnly {
            configuration.icon
        } else {
            configuration.title
        }
    }
}
