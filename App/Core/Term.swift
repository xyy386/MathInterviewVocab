import Foundation

struct Term: Codable, Identifiable, Hashable, Sendable {
    let id: Int
    let subjectID: String
    let subjectName: String
    let subjectNameEn: String
    let chinese: String
    let english: String
    let definition: String
    let important: Bool
    let speechText: String
    let pronunciation: TermPronunciation

    init(
        id: Int,
        subjectID: String,
        subjectName: String,
        subjectNameEn: String,
        chinese: String,
        english: String,
        definition: String,
        important: Bool,
        speechText: String? = nil,
        pronunciation: TermPronunciation = .empty
    ) {
        self.id = id
        self.subjectID = subjectID
        self.subjectName = subjectName
        self.subjectNameEn = subjectNameEn
        self.chinese = chinese
        self.english = english
        self.definition = definition
        self.important = important
        self.speechText = speechText ?? english
        self.pronunciation = pronunciation
    }

    private enum CodingKeys: String, CodingKey {
        case id
        case subjectID
        case subjectName
        case subjectNameEn
        case chinese
        case english
        case definition
        case important
        case speechText
        case pronunciation
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)

        id = try container.decode(Int.self, forKey: .id)
        subjectID = try container.decode(String.self, forKey: .subjectID)
        subjectName = try container.decode(String.self, forKey: .subjectName)
        subjectNameEn = try container.decode(String.self, forKey: .subjectNameEn)
        chinese = try container.decode(String.self, forKey: .chinese)
        english = try container.decode(String.self, forKey: .english)
        definition = try container.decode(String.self, forKey: .definition)
        important = try container.decode(Bool.self, forKey: .important)
        speechText = try container.decodeIfPresent(String.self, forKey: .speechText) ?? english
        pronunciation = try container.decodeIfPresent(
            TermPronunciation.self,
            forKey: .pronunciation
        ) ?? .empty
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)

        try container.encode(id, forKey: .id)
        try container.encode(subjectID, forKey: .subjectID)
        try container.encode(subjectName, forKey: .subjectName)
        try container.encode(subjectNameEn, forKey: .subjectNameEn)
        try container.encode(chinese, forKey: .chinese)
        try container.encode(english, forKey: .english)
        try container.encode(definition, forKey: .definition)
        try container.encode(important, forKey: .important)
        try container.encode(speechText, forKey: .speechText)
        try container.encode(pronunciation, forKey: .pronunciation)
    }
}
