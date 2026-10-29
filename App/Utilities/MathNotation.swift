import Foundation

enum MathNotation {
    static func display(_ source: String) -> String {
        var text = source
            .replacingOccurrences(of: "`", with: "")
            .replacingOccurrences(of: "\\(", with: "")
            .replacingOccurrences(of: "\\)", with: "")
            .replacingOccurrences(of: "\\[", with: "")
            .replacingOccurrences(of: "\\]", with: "")

        let replacements: [(String, String)] = [
            ("\\operatorname{adj}", "adj"),
            ("^\\mathsf{T}", "ᵀ"),
            ("\\mathsf{T}", "ᵀ"),
            ("\\mathrm{d}", "d"),
            ("\\mathcal F", "𝓕"),
            ("\\varepsilon", "ε"),
            ("\\epsilon", "ε"),
            ("\\lambda", "λ"),
            ("\\delta", "δ"),
            ("\\xi", "ξ"),
            ("\\alpha", "α"),
            ("\\Omega", "Ω"),
            ("\\sigma", "σ"),
            ("\\infty", "∞"),
            ("\\times", "×"),
            ("\\le", "≤"),
            ("\\ge", "≥"),
            ("\\mid", "∣"),
            ("\\cap", "∩"),
            ("\\oplus", "⊕"),
            ("\\perp", "⊥"),
            ("\\subseteq", "⊆"),
            ("\\det", "det"),
            ("\\dim", "dim"),
            ("\\sum", "∑"),
            ("\\int", "∫"),
            ("\\in", "∈"),
            ("\\ldots", "…"),
            ("\\,", " "),
            ("\\!", "")
        ]

        for (source, replacement) in replacements {
            text = text.replacingOccurrences(of: source, with: replacement)
        }

        let superscripts: [(String, String)] = [
            ("^2", "²"), ("^3", "³"), ("^{-1}", "⁻¹")
        ]
        for (source, replacement) in superscripts {
            text = text.replacingOccurrences(of: source, with: replacement)
        }

        text = text
            .replacingOccurrences(of: "\\{", with: "{")
            .replacingOccurrences(of: "\\}", with: "}")
            .replacingOccurrences(of: "\\", with: "")

        return text
    }
}
