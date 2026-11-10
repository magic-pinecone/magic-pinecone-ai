import Foundation
import Shared

/// Resolves shared moko-resources strings in the in-app language, e.g. `MR.strings().catalog_clear.localized`.
/// Call these while building a view body, which `ContentView` rebuilds when the language changes.
extension StringResource {
    var localized: String {
        IosStringsKt.localized(resource: self)
    }

    func localized(_ args: Any...) -> String {
        IosStringsKt.localizedFormatted(resource: self, args: args)
    }
}

extension PluralsResource {
    func localized(_ quantity: Int, _ args: Any...) -> String {
        IosStringsKt.localizedPlural(resource: self, quantity: Int32(quantity), args: args)
    }
}

extension CourseType {
    var localizedLabel: String {
        labelResource().localized
    }
}

extension Double {
    var creditString: String {
        formatted(.number.precision(.fractionLength(0...2)))
    }
}
