import Shared
import SwiftUI
import Translation

extension EnvironmentValues {
    /// The in-app language tag, or an empty string when the app follows the phone's language.
    @Entry var appLanguageTag = ""
}

/// Translates campus text, which is Traditional Chinese, with Apple's on-device Translation framework.
enum OnDeviceTranslation {
    static let source = Locale.Language(identifier: "zh-Hant")

    /// The reader's language: the in-app language, or the phone's first language when the app follows the phone.
    /// Nil when that is Chinese, since the text needs no translation.
    static func target(appLanguageTag: String) -> Locale.Language? {
        let identifier = appLanguageTag.isEmpty ? (Locale.preferredLanguages.first ?? "en") : appLanguageTag
        let language = Locale.Language(identifier: identifier)
        return language.languageCode == .chinese ? nil : language
    }

    /// Whether this device can translate into [target], counting languages it would still need to download.
    static func isAvailable(to target: Locale.Language) async -> Bool {
        await LanguageAvailability().status(from: source, to: target) != .unsupported
    }

    /// Translates each line of [texts] and keeps blank lines, so paragraphs stay where they were.
    static func translate(_ texts: [String], with session: TranslationSession) async throws -> [String] {
        let lines = texts.map { $0.components(separatedBy: "\n") }
        var requests: [TranslationSession.Request] = []
        for (textIndex, textLines) in lines.enumerated() {
            for (lineIndex, line) in textLines.enumerated() where !line.trimmingCharacters(in: .whitespaces).isEmpty {
                let id = "\(textIndex):\(lineIndex)"
                requests.append(TranslationSession.Request(sourceText: line, clientIdentifier: id))
            }
        }
        var translated = lines
        for response in try await session.translations(from: requests) {
            let parts = response.clientIdentifier?.split(separator: ":").compactMap { Int($0) } ?? []
            guard parts.count == 2 else { continue }
            translated[parts[0]][parts[1]] = response.targetText
        }
        return translated.map { $0.joined(separator: "\n") }
    }
}

/// Translates a screen's texts when asked, and keeps the result so switching back and forth is instant.
@MainActor @Observable
final class TextTranslation {
    /// Set while a translation runs; `.translationTask` starts when it is set.
    fileprivate(set) var request: TranslationSession.Configuration?
    private var translations: [String: String]?
    private(set) var showsTranslation = false
    private(set) var failed = false

    /// [original] in the reader's language while the translation is switched on, otherwise [original] itself.
    func text(_ original: String) -> String {
        showsTranslation ? translations?[original] ?? original : original
    }

    fileprivate func toggle(into target: Locale.Language) {
        if showsTranslation {
            showsTranslation = false
        } else if translations != nil {
            showsTranslation = true
        } else {
            failed = false
            // The system asks to download the languages on first use, then translates on the device.
            request = TranslationSession.Configuration(source: OnDeviceTranslation.source, target: target)
        }
    }

    fileprivate func reset() {
        translations = nil
        showsTranslation = false
        failed = false
    }

    fileprivate func translate(_ texts: [String], with session: TranslationSession) async {
        do {
            let translated = try await OnDeviceTranslation.translate(texts, with: session)
            translations = Dictionary(zip(texts, translated)) { first, _ in first }
            showsTranslation = true
        } catch {
            failed = true
        }
        request = nil
    }
}

extension View {
    /// Adds a toolbar button that translates [texts] on the device into the reader's language.
    /// The button only appears when the device can translate into that language and some text is still Chinese.
    func onDeviceTranslation(_ translation: TextTranslation, texts: [String]) -> some View {
        modifier(OnDeviceTranslationModifier(translation: translation, texts: texts))
    }
}

private struct OnDeviceTranslationModifier: ViewModifier {
    let translation: TextTranslation
    let texts: [String]

    @Environment(\.appLanguageTag) private var appLanguageTag
    @State private var target: Locale.Language?

    /// Texts with Chinese in them; ones the backend already translated stay as they are.
    private var translatableTexts: [String] {
        var seen = Set<String>()
        return texts.filter { text in
            text.unicodeScalars.contains { $0.properties.isIdeographic } && seen.insert(text).inserted
        }
    }

    func body(content: Content) -> some View {
        content
            .toolbar {
                if let target, !translatableTexts.isEmpty {
                    ToolbarItem(placement: .primaryAction) { button(into: target) }
                }
            }
            .task(id: appLanguageTag) {
                guard let language = OnDeviceTranslation.target(appLanguageTag: appLanguageTag) else {
                    return target = nil
                }
                target = await OnDeviceTranslation.isAvailable(to: language) ? language : nil
            }
            .onChange(of: texts) { translation.reset() }
            .translationTask(translation.request) { session in
                await translation.translate(translatableTexts, with: session)
            }
    }

    @ViewBuilder
    private func button(into target: Locale.Language) -> some View {
        if translation.request != nil {
            // A request stays set until its translation finishes or fails.
            ProgressView()
                .accessibilityLabel(MR.strings().common_translate.localized)
        } else if translation.showsTranslation {
            Button(MR.strings().common_show_original.localized, systemImage: "character.bubble") {
                translation.toggle(into: target)
            }
        } else {
            Button(MR.strings().common_translate.localized, systemImage: "translate") {
                translation.toggle(into: target)
            }
        }
    }
}

/// Says the text was machine translated, or that translating failed.
struct TranslationNote: View {
    let translation: TextTranslation

    var body: some View {
        if translation.showsTranslation {
            Label(MR.strings().common_translated_on_device.localized, systemImage: "translate")
        } else if translation.failed {
            Text(MR.strings().common_translation_failed.localized)
        }
    }
}
