import Foundation
import Observation

struct SubjectSummary: Identifiable, Hashable {
    let id: String
    let name: String
    let englishName: String
    let count: Int
    let importantCount: Int
}

@MainActor
@Observable
final class TermLibrary {
    private(set) var terms: [Term] = []
    private(set) var loadError: String?

    init(bundle: Bundle = .main) {
        load(from: bundle)
    }

    var subjects: [SubjectSummary] {
        let preferredOrder = ["subjects", "analysis", "algebra", "probability", "statistics"]
        return preferredOrder.compactMap { subjectID in
            let subjectTerms = terms.filter { $0.subjectID == subjectID }
            guard let first = subjectTerms.first else { return nil }
            return SubjectSummary(
                id: subjectID,
                name: first.subjectName,
                englishName: first.subjectNameEn,
                count: subjectTerms.count,
                importantCount: subjectTerms.filter(\.important).count
            )
        }
    }

    func term(id: Int) -> Term? {
        terms.first { $0.id == id }
    }

    private func load(from bundle: Bundle) {
        guard let url = bundle.url(forResource: "terms", withExtension: "json") else {
            loadError = "未找到内置词汇数据 terms.json。"
            return
        }

        do {
            let data = try Data(contentsOf: url)
            let decoded = try JSONDecoder().decode([Term].self, from: data)
            guard decoded.count == 326 else {
                loadError = "词汇数据不完整：应为 326 条，实际为 \(decoded.count) 条。"
                return
            }
            terms = decoded.sorted { $0.id < $1.id }
        } catch {
            loadError = "无法读取词汇数据：\(error.localizedDescription)"
        }
    }
}
