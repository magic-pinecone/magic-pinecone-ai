import Foundation
import Shared
import SwiftSoup

/// Parses HTML with SwiftSoup.
/// A document that fails to parse reads as empty, and a selector that fails matches nothing.
final class SwiftSoupHtmlParser: NSObject, HtmlParser {
    func parse(html: String, baseUrl: String) -> HtmlElement {
        guard let document = try? SwiftSoup.parse(html, baseUrl) else {
            return EmptyHtmlElement()
        }
        return SwiftSoupElement(element: document)
    }
}

private final class SwiftSoupElement: NSObject, HtmlElement {
    private let element: Element

    init(element: Element) {
        self.element = element
    }

    func select(cssQuery: String) -> [HtmlElement] {
        guard let matches = try? element.select(cssQuery) else { return [] }
        return matches.array().map { SwiftSoupElement(element: $0) }
    }

    func attr(name: String) -> String? {
        guard element.hasAttr(name) else { return nil }
        return try? element.attr(name)
    }

    func absUrl(name: String) -> String? {
        guard let resolved = try? element.absUrl(name), !resolved.isEmpty else { return nil }
        return resolved
    }

    func text() -> String {
        (try? element.text()) ?? ""
    }

    func html() -> String {
        (try? element.html()) ?? ""
    }

    func htmlWithAbsoluteLinks() -> String {
        guard let copy = element.copy() as? Element, let links = try? copy.select("a[href]") else { return html() }
        for link in links.array() {
            if let resolved = try? link.absUrl("href"), !resolved.isEmpty {
                _ = try? link.attr("href", resolved)
            }
        }
        return (try? copy.html()) ?? ""
    }
}

private final class EmptyHtmlElement: NSObject, HtmlElement {
    func select(cssQuery: String) -> [HtmlElement] { [] }
    func attr(name: String) -> String? { nil }
    func absUrl(name: String) -> String? { nil }
    func text() -> String { "" }
    func html() -> String { "" }
    func htmlWithAbsoluteLinks() -> String { "" }
}
