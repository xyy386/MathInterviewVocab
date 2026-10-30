import Foundation

struct AppVersion: Comparable, Equatable, Sendable, CustomStringConvertible {
    let major: Int
    let minor: Int
    let patch: Int

    init?(_ text: String) {
        let parts = text.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 3 else { return nil }
        var numbers: [Int] = []
        for part in parts {
            guard !part.isEmpty, part.utf8.allSatisfy({ (48...57).contains($0) }),
                  part.count == 1 || part.first != "0", let number = Int(part) else { return nil }
            numbers.append(number)
        }
        major = numbers[0]
        minor = numbers[1]
        patch = numbers[2]
    }

    var description: String { "\(major).\(minor).\(patch)" }

    static func < (lhs: Self, rhs: Self) -> Bool {
        if lhs.major != rhs.major { return lhs.major < rhs.major }
        if lhs.minor != rhs.minor { return lhs.minor < rhs.minor }
        return lhs.patch < rhs.patch
    }
}

enum AppUpdateError: Error, LocalizedError, Equatable {
    case invalidRelease
    case invalidChecksum
    case invalidArchive
    case tooLarge
    case status(Int)
    case invalidURL
    case downloadsUnavailable
    case quarantineUnavailable

    var errorDescription: String? {
        switch self {
        case .invalidRelease: "发布信息不完整或格式不正确，请稍后重试或查看 GitHub 发布页。"
        case .invalidChecksum: "更新包校验信息不正确，已停止下载，请稍后重试。"
        case .invalidArchive: "更新包大小或 SHA-256 校验不一致，文件未保留，请重新下载。"
        case .tooLarge: "更新文件超过允许大小，已停止下载。"
        case .status(403), .status(429): "GitHub 暂时限制了请求，请稍后重试。"
        case .status(404): "暂时没有可用的正式发布版本，请稍后重试。"
        case .status: "GitHub 服务暂时不可用，请稍后重试。"
        case .invalidURL: "更新链接不是受信任的 GitHub 地址，已停止操作。"
        case .downloadsUnavailable: "无法写入下载文件夹，请检查磁盘空间和文件夹权限。"
        case .quarantineUnavailable: "无法为更新包保留互联网来源标记，请勿使用本次下载文件。请从 GitHub 发布页用浏览器下载。"
        }
    }
}

struct AppUpdateAsset: Equatable, Sendable {
    let name: String
    let url: URL
    let bytes: Int64
    let digest: String?
}

struct AppUpdateRelease: Equatable, Sendable {
    let version: AppVersion
    let pageURL: URL
    let notes: String
    let archive: AppUpdateAsset
    let checksums: AppUpdateAsset
}

enum AppUpdateCheck: Equatable, Sendable {
    case current
    case available(AppUpdateRelease)
}

enum AppUpdatePolicy {
    static let repositoryURL = "https://github.com/xyy386/MathInterviewVocab"
    static let latestURL = URL(string: "https://api.github.com/repos/xyy386/MathInterviewVocab/releases/latest")!
    static let maximumMetadataBytes: Int64 = 1_048_576
    static let maximumChecksumBytes: Int64 = 131_072
    static let maximumArchiveBytes: Int64 = 524_288_000
    static let allowedHosts: Set<String> = [
        "github.com", "api.github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com"
    ]

    static func isAllowedNetworkURL(_ url: URL) -> Bool {
        guard url.scheme == "https", let host = url.host,
              allowedHosts.contains(host), url.user == nil, url.password == nil,
              url.port == nil || url.port == 443, url.fragment == nil else { return false }
        return true
    }

    static func check(_ data: Data, currentVersion: String) throws -> AppUpdateCheck {
        guard data.count <= maximumMetadataBytes, let current = AppVersion(currentVersion) else {
            throw AppUpdateError.invalidRelease
        }
        let release: GitHubRelease
        do { release = try JSONDecoder().decode(GitHubRelease.self, from: data) }
        catch { throw AppUpdateError.invalidRelease }
        guard !release.draft, !release.prerelease, release.tag_name.hasPrefix("v"),
              let version = AppVersion(String(release.tag_name.dropFirst())),
              release.html_url == "\(repositoryURL)/releases/tag/v\(version)",
              let pageURL = URL(string: release.html_url),
              isAllowedNetworkURL(pageURL) else { throw AppUpdateError.invalidRelease }

        // 历史正式版可能采用旧附件命名；不更新时无需要求旧版附件符合新约定。
        guard current < version else { return .current }
        let archiveName = "MathInterviewVocab-macOS-universal-v\(version).zip"
        let archive = try asset(named: archiveName, in: release, maximumBytes: maximumArchiveBytes)
        let checksums = try asset(named: "SHA256SUMS.txt", in: release, maximumBytes: maximumChecksumBytes)
        return .available(AppUpdateRelease(
            version: version, pageURL: pageURL,
            notes: String((release.body ?? "暂无更新说明。").prefix(8_000)),
            archive: archive, checksums: checksums
        ))
    }

    static func checksum(in data: Data, for filename: String) throws -> String {
        guard data.count <= maximumChecksumBytes, let text = String(data: data, encoding: .utf8) else {
            throw AppUpdateError.invalidChecksum
        }
        var hashes: [String: String] = [:]
        for rawLine in text.components(separatedBy: "\n") {
            let line = rawLine.hasSuffix("\r") ? String(rawLine.dropLast()) : rawLine
            if line.isEmpty { continue }
            let bytes = Array(line.utf8)
            guard bytes.count > 66, bytes[64] == 32, bytes[65] == 32 || bytes[65] == 42 else {
                throw AppUpdateError.invalidChecksum
            }
            let digest = String(decoding: bytes.prefix(64), as: UTF8.self)
            let name = String(decoding: bytes.dropFirst(66), as: UTF8.self)
            guard isDigest(digest), !name.isEmpty, name.count <= 160, name != ".", name != "..",
                  name.utf8.allSatisfy({ byte in
                      (48...57).contains(byte) || (65...90).contains(byte) || (97...122).contains(byte)
                          || [45, 46, 95].contains(byte)
                  }), hashes[name] == nil else { throw AppUpdateError.invalidChecksum }
            hashes[name] = digest
        }
        guard let expected = hashes[filename] else { throw AppUpdateError.invalidChecksum }
        return expected
    }

    static func isDigest(_ value: String) -> Bool {
        value.utf8.count == 64 && value.utf8.allSatisfy { (48...57).contains($0) || (97...102).contains($0) }
    }

    private static func asset(named name: String, in release: GitHubRelease, maximumBytes: Int64) throws -> AppUpdateAsset {
        let matches = release.assets.filter { $0.name == name }
        guard matches.count == 1, let asset = matches.first,
              asset.state == "uploaded", asset.size > 0, asset.size <= maximumBytes,
              asset.browser_download_url == "\(repositoryURL)/releases/download/\(release.tag_name)/\(name)",
              let url = URL(string: asset.browser_download_url), isAllowedNetworkURL(url) else {
            throw AppUpdateError.invalidRelease
        }
        var digest: String?
        if let declared = asset.digest {
            guard declared.hasPrefix("sha256:"), isDigest(String(declared.dropFirst(7))) else {
                throw AppUpdateError.invalidRelease
            }
            digest = String(declared.dropFirst(7))
        }
        return AppUpdateAsset(name: name, url: url, bytes: asset.size, digest: digest)
    }

    private struct GitHubRelease: Decodable {
        let tag_name: String
        let html_url: String
        let draft: Bool
        let prerelease: Bool
        let body: String?
        let assets: [GitHubAsset]
    }

    private struct GitHubAsset: Decodable {
        let name: String
        let browser_download_url: String
        let size: Int64
        let state: String
        let digest: String?
    }
}
