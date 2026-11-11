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
