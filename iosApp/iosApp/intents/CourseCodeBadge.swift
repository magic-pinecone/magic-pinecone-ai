import AppIntents
import Synchronization
import UIKit

/// The first two letters of a class code ("LN1-1040" → "LN"), drawn as the course chip's icon.
enum CourseCodeBadge {
    private static let cache = Mutex<[String: DisplayRepresentation.Image]>([:])

    /// The badge for a class code, or nil when the code doesn't start with a letter.
    static func image(classNo: String) -> DisplayRepresentation.Image? {
        let letters = String(classNo.prefix { $0.isASCII && $0.isLetter }.prefix(2)).uppercased()
        guard !letters.isEmpty else { return nil }
        // Spotlight indexes every course, so each prefix is drawn once.
        return cache.withLock { images in
            if let image = images[letters] { return image }
            guard let data = render(letters) else { return nil }
            let image = DisplayRepresentation.Image(data: data, isTemplate: true)
            images[letters] = image
            return image
        }
    }

    /// Letters cut out of a filled rounded square, so the system tints it like an SF Symbol.
    private static func render(_ letters: String) -> Data? {
        let side: CGFloat = 120
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format)
        let image = renderer.image { context in
            let bounds = CGRect(x: 0, y: 0, width: side, height: side)
            UIColor.black.setFill()
            UIBezierPath(roundedRect: bounds, cornerRadius: side * 0.24).fill()

            let font = UIFont.systemFont(ofSize: side * 0.46, weight: .bold)
            let text = NSAttributedString(string: letters, attributes: [.font: font, .foregroundColor: UIColor.black])
            let size = text.size()
            context.cgContext.setBlendMode(.clear)
            text.draw(at: CGPoint(x: (side - size.width) / 2, y: (side - size.height) / 2))
        }
        return image.pngData()
    }
}
