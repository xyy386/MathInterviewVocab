import CryptoKit
import Foundation

enum UpdateRouteMode: String, Codable, CaseIterable, Identifiable, Sendable {
    case auto, mirror, direct
    var id: String { rawValue }
    var title: String {
        switch self {
        case .auto: "智能多线加速"
        case .mirror: "加速镜像优先"
        case .direct: "GitHub 直连"
        }
    }
}

struct UpdateRoute: Sendable, Equatable {
    let id: String
    let label: String
    let prefix: String
}
struct UpdateIndexAsset: Codable, Sendable, Equatable {
    let name: String
    let bytes: Int64
    let sha256: String
}
struct MacUpdateIndex: Codable, Sendable, Equatable {
    let schema: Int
    let platform: String
    let version: String
    let build: String
    let notes: String
    let assets: [UpdateIndexAsset]
    var archive: UpdateIndexAsset { assets.first { $0.name.hasSuffix(".zip") }! }
    var appcast: UpdateIndexAsset { assets.first { $0.name == "appcast.xml" }! }
    var identity: String { "\(version):\(archive.sha256)" }
}
struct SignedMacUpdateIndex: Sendable {
    let index: MacUpdateIndex
    let data: Data
    let signature: Data
}
struct MacTransferState: Sendable {
    var sequence: UInt64 = 0
    var status = "idle"
    var message = "手动检查更新，学习和发音始终离线。"
    var routeMode = UpdateRouteMode.auto
    var activeRouteLabel = ""
    var version: String?
    var notes = ""
    var downloadedBytes: Int64 = 0
    var totalBytes: Int64 = 0
    var speedBytesPerSecond: Int64 = 0
    var canResume = false
    var hasCachedDownload = false
    var cachedIdentity: String?
    var busy: Bool { ["checking", "downloading", "verifying", "installing"].contains(status) }
    var percent: Int { totalBytes > 0 ? Int(min(100, max(0, downloadedBytes * 100 / totalBytes))) : 0 }
}
enum UpdateTransferError: LocalizedError {
    case failure(String)
    var errorDescription: String? { if case .failure(let message) = self { message } else { nil } }
}

enum DesktopUpdatePolicy {
    static let repository = "https://github.com/xyy386/MathInterviewVocab"
    static let publicKey = "IBdRtdCy/pTzCtxPyODNo0saDZ5zbprza4wRUJ5O1DU="
    static let maximumArchive: Int64 = 500 * 1024 * 1024
    static let maximumIndex = 64 * 1024
    static let routes = [
        UpdateRoute(id: "direct", label: "GitHub 官方直连", prefix: ""),
        UpdateRoute(id: "ghproxy", label: "加速镜像 1 · gh-proxy", prefix: "https://gh-proxy.com/"),
        UpdateRoute(id: "ghfast", label: "加速镜像 2 · ghfast", prefix: "https://ghfast.top/"),
        UpdateRoute(id: "ghproxynet", label: "加速镜像 3 · ghproxy.net", prefix: "https://ghproxy.net/")
    ]
    static let indexPath = "updates/macos/latest.json"

    static func verify(_ data: Data, signature: Data, publicKey: String = publicKey) throws -> SignedMacUpdateIndex {
        guard data.count <= maximumIndex, signature.count <= 1024,
              let text = String(data: signature, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines),
              text.range(of: "^[A-Za-z0-9+/]{86}==$", options: .regularExpression) != nil,
              let rawSignature = Data(base64Encoded: text), let rawKey = Data(base64Encoded: publicKey),
              let key = try? Curve25519.Signing.PublicKey(rawRepresentation: rawKey),
              key.isValidSignature(rawSignature, for: data),
              let object = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              Set(object.keys) == Set(["schema", "platform", "version", "build", "notes", "assets"]),
              let assets = object["assets"] as? [[String: Any]],
              assets.allSatisfy({ Set($0.keys) == Set(["name", "bytes", "sha256"]) }) else {
            throw UpdateTransferError.failure("更新索引签名或格式无效。")
        }
        let index = try JSONDecoder().decode(MacUpdateIndex.self, from: data)
        guard index.schema == 1, index.platform == "macos-universal", AppVersion(index.version) != nil,
              let build = UInt64(index.build), build > 0, String(build) == index.build,
              index.notes.utf16.count <= 8000, index.assets.count == 2,
              Set(index.assets.map(\.name)) == Set(["MathInterviewVocab-macOS-universal-v\(index.version).zip", "appcast.xml"]),
              index.assets.allSatisfy({ $0.bytes > 0 && $0.bytes <= ($0.name == "appcast.xml" ? 64 * 1024 : maximumArchive)
                  && $0.sha256.range(of: "^[a-f0-9]{64}$", options: .regularExpression) != nil }) else {
            throw UpdateTransferError.failure("更新索引的平台、构建号或附件无效。")
        }
        return SignedMacUpdateIndex(index: index, data: data, signature: signature)
    }
    static func assetURL(_ asset: UpdateIndexAsset, in index: MacUpdateIndex) -> URL {
        URL(string: "\(repository)/releases/download/v\(index.version)/\(asset.name)")!
    }
    static func metadataCandidates(_ mode: UpdateRouteMode) -> [(UpdateRoute, URL)] {
        let raw = "https://raw.githubusercontent.com/xyy386/MathInterviewVocab/main/\(indexPath)"
        let direct = (routes[0], URL(string: raw)!)
        if mode == .direct { return [direct] }
        let mirrors = routes.dropFirst().map { ($0, URL(string: $0.prefix + raw)!) }
        let cdns = ["testingcf.jsdelivr.net", "fastly.jsdelivr.net"].enumerated().map { number, host in
            (UpdateRoute(id: "cdn\(number)", label: number == 0 ? "jsDelivr CF" : "jsDelivr Fastly", prefix: ""),
             URL(string: "https://\(host)/gh/xyy386/MathInterviewVocab@main/\(indexPath)")!)
        }
        return mode == .mirror ? mirrors + cdns + [direct] : [direct] + mirrors + cdns
    }
    static func assetCandidates(_ url: URL, mode: UpdateRouteMode, preferred: String?) -> [(UpdateRoute, URL)] {
        var ordered = mode == .direct ? [routes[0]] : mode == .mirror ? Array(routes.dropFirst()) + [routes[0]] : routes
        if let preferred, let index = ordered.firstIndex(where: { $0.id == preferred }) { ordered.insert(ordered.remove(at: index), at: 0) }
        return ordered.map { ($0, URL(string: $0.prefix + url.absoluteString)!) }
    }

    static func checkedURL(_ url: URL, redirected: Bool = false) throws -> URL {
        guard url.scheme == "https", url.user == nil, url.password == nil, url.port == nil, url.fragment == nil,
              let host = url.host, let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              !components.percentEncodedPath.contains("%"), !url.path.contains("\\") else {
            throw UpdateTransferError.failure("更新地址不属于允许的 HTTPS 路径。")
        }
        func official(_ host: String, _ path: String) -> Bool {
            if host == "github.com" {
                return path.range(of: "^/xyy386/MathInterviewVocab/releases/download/v(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)/(MathInterviewVocab-macOS-universal-v[0-9.]+\\.zip|appcast\\.xml)$", options: .regularExpression) != nil
            }
            let root = "/xyy386/MathInterviewVocab/main/\(indexPath)"
            return host == "raw.githubusercontent.com" && [root, root + ".sig"].contains(path)
        }
        var allowed = official(host, url.path) && url.query == nil
        if ["release-assets.githubusercontent.com", "objects.githubusercontent.com"].contains(host) { allowed = redirected }
        if ["gh-proxy.com", "ghfast.top", "ghproxy.net"].contains(host), url.query == nil {
            for original in ["github.com", "raw.githubusercontent.com"] {
                let prefix = "/https://\(original)"
                if url.path.hasPrefix(prefix) { allowed = official(original, String(url.path.dropFirst(prefix.count))) }
            }
        }
        if ["testingcf.jsdelivr.net", "fastly.jsdelivr.net"].contains(host), url.query == nil {
            let root = "/gh/xyy386/MathInterviewVocab@main/\(indexPath)"
            allowed = [root, root + ".sig"].contains(url.path)
        }
        guard allowed else { throw UpdateTransferError.failure("更新地址不属于本项目的允许资源。") }
        return url
    }
    static func effectiveOffset(status: Int, length: Int64?, range: String?, offset: Int64, total: Int64) throws -> Int64 {
        if status == 200 {
            guard length == nil || length == total else { throw UpdateTransferError.failure("安装包大小与签名索引不一致。") }
            return 0
        }
        let expected = "bytes \(offset)-\(total - 1)/\(total)"
        guard status == 206, range == expected, length == nil || length == total - offset else {
            throw UpdateTransferError.failure("断点续传响应范围无效。")
        }
        return offset
    }
    static func digest(_ data: Data) -> String { SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined() }
}
