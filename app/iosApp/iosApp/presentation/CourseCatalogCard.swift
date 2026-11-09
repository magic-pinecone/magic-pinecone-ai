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
        .accessibilityValue(isSelected ? "已加入課表" : "尚未加入課表")
        .accessibilityHint("查看課程詳細資料")
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
                Text(course.teachers.filter { !$0.isEmpty }.joined(separator: "、"))
                    .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text("\(course.credit, format: .number.precision(.fractionLength(0...2))) 學分")
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
            Text(courseType.description_)
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
        let teachers = course.teachers.filter { !$0.isEmpty }.joined(separator: "、")
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
                \(course.credit, format: .number.precision(.fractionLength(0...2))) 學分\(teacherSuffix)
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
                CourseCatalogInfoBadge(symbol: "person.3", text: "\(course.admitCnt)／\(course.limitCnt) 人")
                CourseCatalogInfoBadge(symbol: "key", text: course.passwordCard.description_)
                if course.waitCnt > 0 {
                    CourseCatalogInfoBadge(symbol: "clock", text: "候補 \(course.waitCnt) 人")
                }
            }
        }
        .scrollIndicators(.hidden)
        .accessibilityIdentifier("course-info-\(course.serialNumber)")
        .accessibilityHint("左右滑動查看課程資訊")
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
            Label(isSelected ? "移除" : "加入", systemImage: isSelected ? "minus" : "plus")
                .labelStyle(isCompact ? CourseSelectionLabelStyle.iconOnly : .titleOnly)
                .frame(minWidth: isCompact ? 28 : 44, minHeight: 28)
        }
        .buttonStyle(CourseSelectionButtonStyle(isSelected: isSelected))
        .disabled(!canEditPlan)
        .accessibilityIdentifier("course-toggle-\(course.serialNumber)")
        .accessibilityLabel(
            isSelected
                ? "從課表移除 \(course.title)，\(course.classNo)"
                : "加入課表 \(course.title)，\(course.classNo)"
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
