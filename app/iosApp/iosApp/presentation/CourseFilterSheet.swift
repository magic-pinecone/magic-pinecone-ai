import Shared
import SwiftUI

struct CourseFilterSheet: View {
    let sharedHost: IosSharedHost
    let bridge: CourseSearchBridge
    let snapshot: CourseCatalogSnapshot?

    enum Tab {
        case filters
        case schedule
    }

    @State private var tab: Tab = .filters
    @State private var detent: PresentationDetent = .medium
    @Environment(\.dismiss) private var dismiss
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    private var active: ActiveCourseFilters {
        ActiveCourseFilters(snapshot?.filters ?? [])
    }

    var body: some View {
        NavigationStack {
            Group {
                if horizontalSizeClass == .regular {
                    HStack(spacing: 0) {
                        CourseFilterForm(bridge: bridge, options: snapshot?.filterOptions, active: active)
                        Divider()
                        CourseSchedulePane(sharedHost: sharedHost, bridge: bridge)
                    }
                } else {
                    VStack(spacing: 0) {
                        Picker("篩選方式", selection: $tab) {
                            Text("篩選").tag(Tab.filters)
                            Text("時段").tag(Tab.schedule)
                        }
                        .pickerStyle(.segmented)
                        .padding(.horizontal)
                        .padding(.vertical, 8)

                        switch tab {
                        case .filters:
                            CourseFilterForm(bridge: bridge, options: snapshot?.filterOptions, active: active)
                        case .schedule:
                            CourseSchedulePane(sharedHost: sharedHost, bridge: bridge)
                        }
                    }
                }
            }
            // The slot grid is drawn by Compose on the grouped background, so the Form and the sheet match it.
            .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea())
            .navigationTitle("篩選")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("清除") { bridge.clearFilters() }
                        .disabled(active.count == 0)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large], selection: $detent)
        .presentationDragIndicator(.visible)
        .onChange(of: tab) { _, newTab in
            // The slot grid needs the full height; leave the detent alone when returning to the filters.
            if newTab == .schedule { detent = .large }
        }
    }
}

private struct CourseSchedulePane: View {
    let sharedHost: IosSharedHost
    let bridge: CourseSearchBridge

    var body: some View {
        VStack(spacing: 0) {
            CourseSlotPickerComposeView(sharedHost: sharedHost, bridge: bridge)
            Text("只顯示上課時間完全落在所選時段內的課程。")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding([.horizontal, .bottom])
                .padding(.top, 8)
        }
    }
}

private struct CourseFilterForm: View {
    let bridge: CourseSearchBridge
    let options: CourseFilterOptions?
    let active: ActiveCourseFilters

    // Suggestions can add both values of a kind; `.both` then matches no segment, so none shows as selected.
    private enum TypeChoice: Hashable {
        case all, required, elective, both
    }

    private enum SeatsChoice: Hashable {
        case all, open, full, both
    }

    private var typeChoice: Binding<TypeChoice> {
        Binding {
            switch active.types {
            case []: .all
            case [.required]: .required
            case [.elective]: .elective
            default: .both
            }
        } set: { choice in
            for type in active.types {
                bridge.removeFilter(filter: CourseFilterType(type: type))
            }
            switch choice {
            case .all, .both: break
            case .required: bridge.addFilter(filter: CourseFilterType(type: .required))
            case .elective: bridge.addFilter(filter: CourseFilterType(type: .elective))
            }
        }
    }

    private var seatsChoice: Binding<SeatsChoice> {
        Binding {
            switch active.seats {
            case []: .all
            case [.open]: .open
            case [.full]: .full
            default: .both
            }
        } set: { choice in
            for availability in active.seats {
                bridge.removeFilter(filter: CourseFilterSeats(availability: availability))
            }
            switch choice {
            case .all, .both: break
            case .open: bridge.addFilter(filter: CourseFilterSeats(availability: .open))
            case .full: bridge.addFilter(filter: CourseFilterSeats(availability: .full))
            }
        }
    }

    var body: some View {
        Form {
            Section("課程類型") {
                Picker("課程類型", selection: typeChoice) {
                    Text("全部").tag(TypeChoice.all)
                    Text("必修").tag(TypeChoice.required)
                    Text("選修").tag(TypeChoice.elective)
                }
                .pickerStyle(.segmented)
                .labelsHidden()
            }

            Section("名額") {
                Picker("名額", selection: seatsChoice) {
                    Text("全部").tag(SeatsChoice.all)
                    Text("尚有名額").tag(SeatsChoice.open)
                    Text("已額滿").tag(SeatsChoice.full)
                }
                .pickerStyle(.segmented)
                .labelsHidden()
            }

            let credits = options?.credits.map(\.doubleValue) ?? []
            if !credits.isEmpty {
                Section("學分") {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 64), spacing: 8)], spacing: 8) {
                        ForEach(credits, id: \.self) { value in
                            creditButton(value)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }

            Section {
                optionLink(
                    title: "系所",
                    choices: options?.departments ?? [],
                    selected: active.departments,
                    make: { CourseFilterDepartment(name: $0) }
                )
                optionLink(
                    title: "學院",
                    choices: options?.colleges ?? [],
                    selected: active.colleges,
                    make: { CourseFilterCollege(name: $0) }
                )
                optionLink(
                    title: "老師",
                    choices: options?.teachers ?? [],
                    selected: active.teachers,
                    make: { CourseFilterTeacher(name: $0) }
                )
            }
        }
    }

    private func creditButton(_ value: Double) -> some View {
        let isSelected = active.credits.contains(value)
        let filter = CourseFilterCredits(credits: value)
        return Button {
            if isSelected {
                bridge.removeFilter(filter: filter)
            } else {
                bridge.addFilter(filter: filter)
            }
        } label: {
            Text(CourseFilterToken(filter).label.replacingOccurrences(of: " 學分", with: ""))
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.bordered)
        .tint(isSelected ? .accentColor : .secondary)
        .accessibilityLabel("\(CourseFilterToken(filter).label)")
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private func optionLink(
        title: String,
        choices: [String],
        selected: Set<String>,
        make: @escaping (String) -> any CourseFilter
    ) -> some View {
        NavigationLink {
            CourseFilterOptionList(
                title: title,
                choices: choices,
                selected: selected,
                onToggle: { name, isOn in
                    if isOn {
                        bridge.removeFilter(filter: make(name))
                    } else {
                        bridge.addFilter(filter: make(name))
                    }
                }
            )
        } label: {
            LabeledContent(title, value: summary(selected))
        }
    }

    private func summary(_ selected: Set<String>) -> String {
        switch selected.count {
        case 0: "不限"
        case 1: selected.first ?? ""
        default: "已選 \(selected.count) 項"
        }
    }
}

private struct CourseFilterOptionList: View {
    let title: String
    let choices: [String]
    let onToggle: (String, Bool) -> Void
    @State private var selected: Set<String>
    @State private var search = ""

    // A pushed destination is not rebuilt when the snapshot changes, so it tracks its own selection.
    init(title: String, choices: [String], selected: Set<String>, onToggle: @escaping (String, Bool) -> Void) {
        self.title = title
        self.choices = choices
        self.onToggle = onToggle
        _selected = State(initialValue: selected)
    }

    private var visible: [String] {
        search.isEmpty ? choices : choices.filter { $0.localizedStandardContains(search) }
    }

    var body: some View {
        List(visible, id: \.self) { name in
            let isOn = selected.contains(name)
            Button {
                onToggle(name, isOn)
                if isOn { selected.remove(name) } else { selected.insert(name) }
            } label: {
                HStack {
                    Text(name).foregroundStyle(.primary)
                    Spacer()
                    if isOn {
                        Image(systemName: "checkmark").foregroundStyle(.tint)
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(isOn ? .isSelected : [])
        }
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .searchable(text: $search, prompt: "搜尋\(title)")
    }
}
