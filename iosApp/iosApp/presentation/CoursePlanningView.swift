import AppIntents
import Shared
import SwiftUI
import UIKit

struct CoursePlanningView: View {
    let sharedHost: IosSharedHost
    let onOpenSettings: () -> Void

    enum Section {
        case catalog
        case timetable
    }

    @State private var activeSection: Section = .catalog
    @GestureState private var swipeTranslation: CGFloat = 0
    @State private var planBridge = CoursePlanBridge()
    @State private var planSnapshot = CoursePlanSnapshot(selectedCourses: [], canEditPlan: false)
    @State private var isShowingSelectedCourses = false
    @State private var courseDetailSelection: CourseDetailSelection?
    @AppStorage("courseCatalogCompactMode") private var isCompactCatalog = false
    @State private var catalogQuery = ""
    @State private var isSearchPresented = false
    @State private var sectionBeforeSearch: Section?
    @State private var catalogBridge = CourseSearchBridge()
    @State private var catalogSnapshot: CourseCatalogSnapshot?
    @State private var isShowingFilters = false
    @State private var isHidingTokens = false
        @State private var emptiedEvent = SearchEmptiedEvent()
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.layoutDirection) private var layoutDirection
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(AppRouter.self) private var router
    private let semester = CourseIdentity.currentSemester

    private var selectedCourses: [CourseSummary] {
        planSnapshot.selectedCourses
    }

    private var canEditPlan: Bool {
        planSnapshot.canEditPlan
    }

    var body: some View {
        GeometryReader { geometry in
            // Size classes support Duo's inner display; width also supports existing landscape layouts.
            let isWide = horizontalSizeClass == .regular || geometry.size.width >= 600
            let layout = CoursePlanningPaneLayout(
                isExpanded: isWide,
                division: geometry.coursePlanningDivision
            )

            // Retain both hosts while their frames adapt to the available regions.
            layout {
                CourseCatalogView(
                    sharedHost: sharedHost,
                    planBridge: planBridge,
                    selectedCourses: selectedCourses,
                    canEditPlan: canEditPlan,
                    isCompact: isCompactCatalog,
                    semester: semester,
                    query: $catalogQuery,
                    bridge: catalogBridge,
                    catalogSnapshot: catalogSnapshot,
                    onShowFilters: { isShowingFilters = true }
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .offset(x: isWide ? 0 : pageOffset(for: .catalog, width: geometry.size.width))
                .disabled(!isWide && activeSection != .catalog)
                .allowsHitTesting(isWide || activeSection == .catalog)
                .accessibilityHidden(!isWide && activeSection != .catalog)

                Color(uiColor: .separator)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)

                CoursePlanningTimetableView(
                    sharedHost: sharedHost,
                    planBridge: planBridge
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .offset(x: isWide ? 0 : pageOffset(for: .timetable, width: geometry.size.width))
                .allowsHitTesting(isWide || activeSection == .timetable)
                .accessibilityHidden(!isWide && activeSection != .timetable)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .contentShape(Rectangle())
            .gesture(
                sectionSwipe,
                including: !isWide && activeSection == .catalog ? .all : .subviews
            )
            .simultaneousGesture(
                sectionSwipe,
                including: !isWide && activeSection == .timetable ? .all : .subviews
            )
            .animation(reduceMotion ? nil : .snappy, value: activeSection)
            .animation(reduceMotion ? nil : .snappy, value: swipeTranslation)
            .coursePlanningTopBar {
                if !isWide && !isSearchPresented {
                    Picker("選課檢視", selection: $activeSection) {
                        Text("搜尋").tag(Section.catalog)
                        Text("課表").tag(Section.timetable)
                    }
                    .pickerStyle(.segmented)
                    .accessibilityIdentifier("course-planning-section")
                    .padding(.horizontal)
                    .padding(.vertical, 8)
                }
            }
            .searchable(
                text: $catalogQuery,
                tokens: tokensBinding,
                isPresented: $isSearchPresented,
                placement: .navigationBarDrawer(displayMode: .always),
                prompt: "搜尋課程名稱"
            ) { token in
                Label(token.label, systemImage: token.symbol)
            }
            .searchSuggestions {
                ForEach(suggestionTokens) { token in
                    Label(token.label, systemImage: token.symbol)
                        .searchCompletion(token)
                }
            }
            .onSubmit(of: .search) {
                catalogBridge.submit(semester: semester, query: catalogQuery)
            }
            .onChange(of: catalogQuery) { old, newValue in
                catalogBridge.updateQuery(semester: semester, query: newValue)
                if newValue.isEmpty, !old.isEmpty {
                    noteEmptied { $0.textBeforeEmptied = old }
                }
            }
            // Picking a suggestion makes the ViewModel clear the query; reflect that in the field.
            // Filters added from the sheet keep the query, so the field is left alone.
            .onChange(of: catalogSnapshot?.filters.count ?? 0, clearQueryAfterFilterAdded)
            // Searching takes over the catalog page, then returns to the page it started from.
            .onChange(of: isSearchPresented) { _, isPresented in
                if isPresented {
                    sectionBeforeSearch = activeSection
                    activeSection = .catalog
                } else if let section = sectionBeforeSearch {
                    activeSection = section
                    sectionBeforeSearch = nil
                }
            }
            .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea())
            .navigationTitle(isWide ? "選課" : (activeSection == .catalog ? "課程搜尋" : "我的課表"))
            // The panes scroll independently, so a large title has no single scroll view to collapse with.
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("儲存", systemImage: "square.and.arrow.down") {
                        planBridge.requestSave()
                    }
                }

                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        isShowingSelectedCourses = true
                    } label: {
                        Label("已選課程", systemImage: "cart")
                    }
                    .badge(selectedCourses.count)
                    .accessibilityLabel("已選課程")
                    .accessibilityValue("\(selectedCourses.count) 門課")
                    .accessibilityHint("顯示目前已加入的課程")
                }

                if isWide || activeSection == .catalog {
                    ToolbarItem(placement: .topBarTrailing) {
                        Menu {
                            Toggle(
                                "精簡課程卡片", systemImage: "rectangle.compress.vertical",
                                isOn: $isCompactCatalog
                            )
                        } label: {
                            Label("顯示選項", systemImage: "slider.horizontal.3")
                        }
                        .accessibilityIdentifier("course-display-options")
                    }
                }

                if isWide || activeSection == .timetable {
                    ToolbarItem(placement: .topBarTrailing) { CalendarExportButton(sharedHost: sharedHost) }
                }

                ToolbarItem(placement: .topBarTrailing) {
                    Button("設定", systemImage: "gear", action: onOpenSettings)
                        .labelStyle(.iconOnly)
                }

            }
        }
        // Expand the layout itself so pane clipping does not discard content below the tab bar.
        .ignoresSafeArea(.container, edges: .bottom)
        .ignoresSafeArea(.keyboard, edges: .bottom)
        .onAppear {
            planBridge.observeCourseDetailsRequest(observer: { semester, course in
                courseDetailSelection = CourseDetailSelection(semester: semester, course: course)
            })
        }
        .onDisappear {
            planBridge.observeCourseDetailsRequest(observer: nil)
        }
        .task(id: router.pendingRoute) {
            await openPendingRoute()
        }
        // Looking at the timetable is when "what's my next class" is worth suggesting.
        .task(id: activeSection) { if activeSection == .timetable { _ = try? await NextClassIntent().donate() } }
        .task(id: ObjectIdentifier(planBridge)) {
            for await snapshot in planBridge.planSnapshot {
                planSnapshot = snapshot
            }
        }
        .task(id: ObjectIdentifier(catalogBridge)) {
            for await snapshot in catalogBridge.catalogSnapshot {
                catalogSnapshot = snapshot
            }
        }
        .sheet(isPresented: $isShowingFilters) {
            CourseFilterSheet(
                sharedHost: sharedHost,
                bridge: catalogBridge,
                snapshot: catalogSnapshot
            )
        }
        .sheet(isPresented: $isShowingSelectedCourses) {
            SelectedCoursesSheet(
                planBridge: planBridge,
                courses: selectedCourses,
                canEditPlan: canEditPlan
            )
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        }
        .sheet(item: $courseDetailSelection) { selection in
            CourseDetailSheet(
                selection: selection,
                sharedHost: sharedHost,
                planBridge: planBridge,
                isSelected: selectedCourses.contains {
                    $0.serialNumber == selection.course.serialNumber
                },
                canEditPlan: canEditPlan
            )
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        }
    }

    private func pageOffset(for section: Section, width: CGFloat) -> CGFloat {
        let index = section == .catalog ? 0 : 1
        let activeIndex = activeSection == .catalog ? 0 : 1
        let direction: CGFloat = layoutDirection == .rightToLeft ? -1 : 1
        return CGFloat(index - activeIndex) * width * direction + swipeTranslation
    }

    private var sectionSwipe: some Gesture {
        DragGesture(minimumDistance: 30)
            .updating($swipeTranslation) { value, translation, transaction in
                transaction.animation = nil
                guard !isSearchPresented else { return }
                let horizontal = value.translation.width
                guard abs(horizontal) > abs(value.translation.height) * 1.5 else { return }
                let logicalTranslation = layoutDirection == .rightToLeft ? -horizontal : horizontal
                // Keep the first and last page from being dragged into an empty region.
                guard (activeSection == .catalog && logicalTranslation < 0)
                    || (activeSection == .timetable && logicalTranslation > 0) else { return }
                translation = horizontal
            }
            .onEnded { value in
                guard !isSearchPresented else { return }
                let horizontal = value.translation.width
                guard abs(horizontal) >= 60,
                      abs(horizontal) > abs(value.translation.height) * 1.5 else { return }
                let logicalTranslation = layoutDirection == .rightToLeft ? -horizontal : horizontal
                activeSection = logicalTranslation < 0 ? .timetable : .catalog
            }
    }
}

private extension CoursePlanningView {
    func openPendingRoute() async {
        if let query = router.take(\.catalogSearch) {
            // Results the user asked for should not open behind a sheet left from earlier.
            courseDetailSelection = nil
            isShowingSelectedCourses = false
            isShowingFilters = false
            activeSection = .catalog
            catalogQuery = query
            catalogBridge.submit(semester: semester, query: query)
        } else if let identity = router.take(\.course) {
            guard let course = try? await sharedHost.course(
                semester: identity.semester,
                serialNumber: identity.serialNumber
            ) else { return }
            planBridge.requestCourseDetails(semester: identity.semester, course: course)
        }
    }

    func clearQueryAfterFilterAdded(old: Int, new: Int) {
        if new > old, catalogSnapshot?.query.isEmpty == true, !catalogQuery.isEmpty {
            catalogQuery = ""
        }
    }

    var suggestionTokens: [CourseFilterToken] {
        (catalogSnapshot?.suggestions ?? []).map(CourseFilterToken.init)
    }

    var tokensBinding: Binding<[CourseFilterToken]> {
        Binding {
            guard !isHidingTokens else { return [] }
            return (catalogSnapshot?.filters ?? []).map(CourseFilterToken.init)
        } set: { newTokens in
            let current = (catalogSnapshot?.filters ?? []).map(CourseFilterToken.init)
            let newIDs = Set(newTokens.map(\.id))
            let currentIDs = Set(current.map(\.id))
            for token in newTokens where !currentIDs.contains(token.id) {
                catalogBridge.pickSuggestion(filter: token.filter)
            }
            let removed = current.filter { !newIDs.contains($0.id) }
            guard !removed.isEmpty else { return }
            noteEmptied {
                let known = $0.removedTokens
                $0.removedTokens += removed.filter { !known.contains($0) }
                $0.hadTokens = current.count
            }
        }
    }

    // SwiftUI reports Close, the field's clear button and a token backspace as text and token
    // changes. Collect them for one turn, then tell them apart by whether search is still presented.
    func noteEmptied(_ update: (inout SearchEmptiedEvent) -> Void) {
        let isFirst = !emptiedEvent.isPending
        update(&emptiedEvent)
        emptiedEvent.isPending = true
        guard isFirst else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15, execute: resolveEmptied)
    }

    // The field drops its tokens on Close and keeps them when the binding value never changes,
    // so publish an empty array for one turn before the real filters.
    func restoreTokens() {
        isHidingTokens = true
        DispatchQueue.main.async { isHidingTokens = false }
    }

    func resolveEmptied() {
        let event = emptiedEvent
        emptiedEvent = SearchEmptiedEvent()
        if !isSearchPresented {
            // Close only leaves focus: keep the text and the filters.
            if let text = event.textBeforeEmptied { catalogQuery = text }
            restoreTokens()
            return
        }
        let removedAll = event.hadTokens > 0 && event.removedTokens.count == event.hadTokens
        if removedAll && (event.hadTokens > 1 || event.textBeforeEmptied != nil) {
            catalogBridge.clearFilters()
            catalogBridge.submit(semester: semester, query: "")
        } else {
            for token in event.removedTokens {
                catalogBridge.removeFilter(filter: token.filter)
            }
            if event.textBeforeEmptied != nil {
                catalogBridge.submit(semester: semester, query: "")
            }
        }
    }
}

struct SearchEmptiedEvent {
    var isPending = false
    var textBeforeEmptied: String?
    var removedTokens: [CourseFilterToken] = []
    var hadTokens = 0
}
