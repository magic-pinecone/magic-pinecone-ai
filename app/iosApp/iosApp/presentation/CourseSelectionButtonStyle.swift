import SwiftUI

struct CourseSelectionButtonStyle: ButtonStyle {
    let isSelected: Bool

    @Environment(\.isEnabled) private var isEnabled
    @Environment(\.colorScheme) private var colorScheme

    // Plain red on its own 15% tint is under 4.5:1; shift it toward the text contrast side just enough.
    private var removeForeground: Color {
        colorScheme == .dark
            ? Color.red.mix(with: .white, by: 0.2, in: .device)
            : Color.red.mix(with: .black, by: 0.3, in: .device)
    }

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .fontWeight(.semibold)
            .foregroundStyle(isSelected ? removeForeground : Color.white)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .frame(minHeight: 44)
            .background(isSelected ? Color.red.opacity(0.15) : Color.accentColor, in: Capsule())
            .contentShape(Capsule())
            .opacity(isEnabled ? (configuration.isPressed ? 0.65 : 1) : 0.4)
    }
}
