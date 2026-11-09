import SwiftUI

// One layout type retains both hosts through resizing and fold transitions.
struct CoursePlanningPaneLayout: Layout {
    let isExpanded: Bool
    let division: CGRect?

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        proposal.replacingUnspecifiedDimensions()
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let frames = CoursePlanningPaneFrames(
            size: bounds.size,
            isExpanded: isExpanded,
            division: division
        )
        for (subview, frame) in zip(subviews, [frames.catalog, frames.separator, frames.timetable]) {
            subview.place(
                at: CGPoint(x: bounds.minX + frame.minX, y: bounds.minY + frame.minY),
                anchor: .topLeading,
                proposal: ProposedViewSize(frame.size)
            )
        }
    }
}

// Use logical coordinates; SwiftUI mirrors placement and region queries together for RTL.
struct CoursePlanningPaneFrames {
    let catalog: CGRect
    let separator: CGRect
    let timetable: CGRect

    init(size: CGSize, isExpanded: Bool, division: CGRect?) {
        let bounds = CGRect(origin: .zero, size: size)
        guard isExpanded else {
            catalog = bounds
            timetable = bounds
            separator = .zero
            return
        }

        if let division {
            let region = division.intersection(bounds)
            if !region.isNull && region.width > 0 && region.height > 0 {
                if division.height >= division.width, region.minX > 0, region.maxX < size.width {
                    let left = CGRect(x: 0, y: 0, width: region.minX, height: size.height)
                    let right = CGRect(x: region.maxX, y: 0, width: size.width - region.maxX, height: size.height)
                    catalog = left
                    timetable = right
                    separator = .zero
                    return
                }
                if division.width > division.height, region.minY > 0, region.maxY < size.height {
                    // Keep the timetable visible above the fold and search controls below it.
                    timetable = CGRect(x: 0, y: 0, width: size.width, height: region.minY)
                    catalog = CGRect(x: 0, y: region.maxY, width: size.width, height: size.height - region.maxY)
                    separator = .zero
                    return
                }
            }
        }

        let separatorWidth = min(1, size.width)
        let catalogWidth = max(0, size.width - separatorWidth) * 0.42
        let timetableWidth = max(0, size.width - catalogWidth - separatorWidth)
        catalog = CGRect(
            x: 0,
            y: 0, width: catalogWidth, height: size.height
        )
        timetable = CGRect(
            x: catalogWidth + separatorWidth,
            y: 0, width: timetableWidth, height: size.height
        )
        separator = CGRect(
            x: catalogWidth,
            y: 0, width: separatorWidth, height: size.height
        )
    }
}

extension GeometryProxy {
    var coursePlanningDivision: CGRect? {
        #if compiler(>=6.4)
        guard #available(iOS 27.1, *) else { return nil }
        // The frame already includes the system's margins for interactive content.
        return reservedRegions(kind: .division).first?.frame
        #else
        return nil
        #endif
    }
}
