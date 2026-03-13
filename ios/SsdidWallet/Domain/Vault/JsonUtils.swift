import Foundation

/// Shared utility for deterministic (canonical) JSON serialization.
/// Keys are sorted alphabetically at every nesting level, no whitespace.
enum JsonUtils {
    /// Produces deterministic JSON by recursively sorting dictionary keys.
    /// Matches the registry's canonical_json implementation.
    static func canonicalJson(_ value: Any) -> String {
        if let dict = value as? [String: Any] {
            let members = dict.keys.sorted().map { key -> String in
                let escapedKey = escapeJsonString(key)
                let childJson = canonicalJson(dict[key]!)
                return "\"\(escapedKey)\":\(childJson)"
            }
            return "{\(members.joined(separator: ","))}"
        } else if let array = value as? [Any] {
            let members = array.map { canonicalJson($0) }
            return "[\(members.joined(separator: ","))]"
        } else if let string = value as? String {
            return "\"\(escapeJsonString(string))\""
        } else if let nsNumber = value as? NSNumber {
            // NSNumber wraps both booleans and numbers; check boolean first
            // using the ObjC type encoding to distinguish them reliably.
            if CFGetTypeID(nsNumber) == CFBooleanGetTypeID() {
                return nsNumber.boolValue ? "true" : "false"
            } else if nsNumber === kCFBooleanTrue || nsNumber === kCFBooleanFalse {
                return nsNumber.boolValue ? "true" : "false"
            }
            let dbl = nsNumber.doubleValue
            if dbl == dbl.rounded() && !dbl.isInfinite && dbl >= Double(Int.min) && dbl <= Double(Int.max) {
                return "\(nsNumber.intValue)"
            }
            return "\(dbl)"
        } else {
            return "null"
        }
    }

    static func escapeJsonString(_ s: String) -> String {
        var result = ""
        result.reserveCapacity(s.count)
        for ch in s.unicodeScalars {
            switch ch {
            case "\\": result += "\\\\"
            case "\"": result += "\\\""
            case "\n": result += "\\n"
            case "\r": result += "\\r"
            case "\t": result += "\\t"
            case "\u{08}": result += "\\b"
            case "\u{0C}": result += "\\f"
            default:
                if ch.value < 0x20 {
                    result += String(format: "\\u%04x", ch.value)
                } else {
                    result += String(ch)
                }
            }
        }
        return result
    }
}
