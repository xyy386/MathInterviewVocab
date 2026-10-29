import Foundation

/// Extracts a pronunciation-safe string from an AppKit UTF-16 selection.
enum SpeechSelectionSanitizer {
    private static let boundaryCharacters = CharacterSet.whitespacesAndNewlines
        .union(.punctuationCharacters)

    /// Returns the selected substring after trimming surrounding whitespace and
    /// punctuation. Internal apostrophes, hyphens, and spaces are preserved.
    static func selectedText(in source: String, range: NSRange) -> String? {
        guard range.location != NSNotFound, range.length > 0 else {
            return nil
        }

        let sourceLength = (source as NSString).length
        guard range.location <= sourceLength,
              range.length <= sourceLength - range.location else {
            return nil
        }

        let rawSelection = (source as NSString).substring(with: range)
        return sanitize(rawSelection)
    }

    /// Trims boundaries and rejects selections that contain no letter or number.
    static func sanitize(_ selection: String) -> String? {
        let trimmed = selection.trimmingCharacters(in: boundaryCharacters)
        guard !trimmed.isEmpty,
              trimmed.unicodeScalars.contains(where: CharacterSet.alphanumerics.contains) else {
            return nil
        }
        return trimmed
    }
}
