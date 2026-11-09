import Foundation
import Translation

// Translates the static course data in magic-pinecone-lite with Apple's on-device
// Translation framework and writes one source-line → translation dictionary per
// scope and language. Re-running only translates lines missing from the output.

struct Options {
    var liteRepo = URL(fileURLWithPath: "../../../magic-pinecone-lite")
    var outDir = URL(fileURLWithPath: "out")
    var languages = ["en", "vi"]
    var scopes = Scope.allCases
    var strategy = TranslationSession.Strategy.highFidelity
    var limit = Int.max
}

enum Scope: String, CaseIterable {
    case catalog, detail
}

struct Catalog: Decodable {
    struct Course: Decodable {
        let title: String
        let college_name: String
        let department_name: String
    }
    let courses: [Course]
}

struct Detail: Decodable {
    struct Condition: Decodable { let rule: String }
    let objectives: String?
    let content: String?
    let books: String?
    let teaching_method: String?
    let grading_policy: String?
    let distribution_conditions: [Condition]?
}

struct TranslationTable: Codable {
    var source_language = "zh-Hant"
    var target_language: String
    var strategy: String
    var entries: [String: String] = [:]
}

let source = Locale.Language(identifier: "zh-Hant")
let batchSize = 20
let checkpointEvery = 200

func parseOptions() -> Options {
    var options = Options()
    var args = CommandLine.arguments.dropFirst().makeIterator()
    while let arg = args.next() {
        switch arg {
        case "--lite": options.liteRepo = URL(fileURLWithPath: args.next()!)
        case "--out": options.outDir = URL(fileURLWithPath: args.next()!)
        case "--lang": options.languages = args.next()!.split(separator: ",").map(String.init)
        case "--scope": options.scopes = args.next()!.split(separator: ",").map { Scope(rawValue: String($0))! }
        case "--fast": options.strategy = .lowLatency
        case "--limit": options.limit = Int(args.next()!)!
        default:
            print("""
            usage: course-translate [--lite DIR] [--out DIR] [--lang en,vi] [--scope catalog,detail] [--fast] [--limit N]
            """)
            exit(arg == "--help" ? 0 : 2)
        }
    }
    return options
}

// Catalog strings are single lines; syllabus fields are split into lines so long
// texts stay within the model's comfort zone and shared boilerplate is translated once.
func sourceLines(_ scope: Scope, liteRepo: URL) throws -> [String] {
    var texts: [String] = []
    let decoder = JSONDecoder()
    switch scope {
    case .catalog:
        let catalog = try decoder.decode(Catalog.self, from: Data(contentsOf: liteRepo.appending(path: "courses.json")))
        for course in catalog.courses {
            texts += [course.title, course.college_name, course.department_name]
        }
        // Distribution rules are short, shown alongside the course list, and repeat heavily.
        for detail in try details(liteRepo) {
            texts += detail.distribution_conditions?.map(\.rule) ?? []
        }
    case .detail:
        for detail in try details(liteRepo) {
            texts += [detail.objectives, detail.content, detail.books, detail.teaching_method, detail.grading_policy]
                .compactMap { $0 }
        }
    }
    let lines = texts.flatMap { $0.components(separatedBy: "\n") }
        .map { $0.trimmingCharacters(in: .whitespaces) }
        .filter { $0.unicodeScalars.contains { (0x4E00...0x9FFF).contains($0.value) } }
    return Array(Set(lines)).sorted()
}

func details(_ liteRepo: URL) throws -> [Detail] {
    let dir = liteRepo.appending(path: "detail")
    return try FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)
        .filter { $0.pathExtension == "json" }
        .map { try JSONDecoder().decode(Detail.self, from: Data(contentsOf: $0)) }
}

func save(_ dictionary: TranslationTable, to url: URL) throws {
    let encoder = JSONEncoder()
    encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
    try encoder.encode(dictionary).write(to: url, options: .atomic)
}

func translate(_ scope: Scope, into language: String, options: Options) async throws {
    let target = Locale.Language(identifier: language)
    let strategyName = options.strategy == .highFidelity ? "highFidelity" : "lowLatency"
    let status = await LanguageAvailability(preferredStrategy: options.strategy).status(from: source, to: target)
    guard status == .installed else {
        print("zh-Hant → \(language) (\(strategyName)) is \(status), not installed.")
        print("Download it in System Settings › General › Language & Region › Translation Languages,")
        print("and turn on Apple Intelligence for highFidelity.")
        exit(1)
    }

    let url = options.outDir.appending(path: "\(scope.rawValue).\(language).json")
    var dictionary = (try? JSONDecoder().decode(TranslationTable.self, from: Data(contentsOf: url)))
        ?? TranslationTable(target_language: language, strategy: strategyName)
    let pending = try sourceLines(scope, liteRepo: options.liteRepo)
        .filter { dictionary.entries[$0] == nil }
        .prefix(options.limit)
    print("\(scope.rawValue).\(language): \(dictionary.entries.count) done, \(pending.count) to translate")

    let session = TranslationSession(installedSource: source, target: target, preferredStrategy: options.strategy)
    let start = ContinuousClock.now
    var sinceCheckpoint = 0
    for batch in stride(from: pending.startIndex, to: pending.endIndex, by: batchSize) {
        let lines = pending[batch..<min(batch + batchSize, pending.endIndex)]
        let requests = lines.enumerated().map {
            TranslationSession.Request(sourceText: $0.element, clientIdentifier: String($0.offset))
        }
        for response in try await session.translations(from: requests) {
            dictionary.entries[response.sourceText] = response.targetText
        }
        sinceCheckpoint += lines.count
        if sinceCheckpoint >= checkpointEvery {
            try save(dictionary, to: url)
            sinceCheckpoint = 0
            let done = batch + lines.count - pending.startIndex
            print("  \(done)/\(pending.count) in \(ContinuousClock.now - start)")
        }
    }
    try save(dictionary, to: url)
    print("  wrote \(url.path) in \(ContinuousClock.now - start)")
}

let options = parseOptions()
try FileManager.default.createDirectory(at: options.outDir, withIntermediateDirectories: true)
for language in options.languages {
    for scope in options.scopes {
        try await translate(scope, into: language, options: options)
    }
}
