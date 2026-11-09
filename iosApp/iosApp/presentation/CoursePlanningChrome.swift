import SwiftUI

extension View {
    // Let content scroll beneath a top bar so it shares the navigation bar's edge effect.
    @ViewBuilder
    func coursePlanningTopBar(@ViewBuilder content: () -> some View) -> some View {
        if #available(iOS 26.0, *) {
            safeAreaBar(edge: .top, spacing: 0, content: content)
        } else {
            safeAreaInset(edge: .top, spacing: 0) {
                content().background(Color(uiColor: .systemGroupedBackground))
            }
        }
    }

    // Keeps controls pinned over scrolling content legible.
    @ViewBuilder
    func coursePlanningGlassCapsule() -> some View {
        if #available(iOS 26.0, *) {
            glassEffect(.regular.interactive(), in: .capsule)
        } else {
            background(.regularMaterial, in: Capsule())
        }
    }

    // Stacked top bars otherwise get a hard edge that cuts content off below the lowest bar.
    @ViewBuilder
    func coursePlanningSoftTopEdge() -> some View {
        if #available(iOS 26.0, *) {
            scrollEdgeEffectStyle(.soft, for: .top)
        } else {
            self
        }
    }
}
