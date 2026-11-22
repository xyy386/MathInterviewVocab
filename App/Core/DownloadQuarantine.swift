import Darwin
import Foundation

enum DownloadQuarantine {
    static let attributeName = "com.apple.quarantine"

    static func ensure(_ file: URL) throws {
        let length = file.withUnsafeFileSystemRepresentation { path in
            guard let path else { return -1 }
            return Darwin.getxattr(path, attributeName, nil, 0, 0, XATTR_NOFOLLOW)
        }
        if length < 0 { try mark(file); return }
        guard length > 0, length <= 1024 else { throw AppUpdateError.quarantineUnavailable }
        var data = Data(count: length)
        let read = data.withUnsafeMutableBytes { buffer in
            file.withUnsafeFileSystemRepresentation { path in
                guard let path else { return -1 }
                return Darwin.getxattr(path, attributeName, buffer.baseAddress, buffer.count, 0, XATTR_NOFOLLOW)
            }
        }
        guard read == length, let text = String(data: data, encoding: .utf8),
              let flags = text.split(separator: ";").first.flatMap({ UInt16($0, radix: 16) }), flags & 1 != 0 else {
            throw AppUpdateError.quarantineUnavailable
        }
    }

    static func mark(_ file: URL) throws {
        guard file.isFileURL,
              let attributes = try? FileManager.default.attributesOfItem(atPath: file.path),
              attributes[.type] as? FileAttributeType == .typeRegular else {
            throw AppUpdateError.quarantineUnavailable
        }
        let timestamp = String(Int(Date().timeIntervalSince1970), radix: 16)
        let marker = Data("0081;\(timestamp);MathInterviewVocab;\(UUID().uuidString)".utf8)
        // 保留下载来源提示，不将它视为 Developer ID 签名或公证，也不覆盖已有标记。
        let result = file.withUnsafeFileSystemRepresentation { path -> Int32 in
            guard let path else { return -1 }
            return marker.withUnsafeBytes { bytes in
                Darwin.setxattr(path, attributeName, bytes.baseAddress, bytes.count, 0, XATTR_CREATE | XATTR_NOFOLLOW)
            }
        }
        guard result == 0 else { throw AppUpdateError.quarantineUnavailable }
    }
}
