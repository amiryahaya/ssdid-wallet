import Foundation

struct ActivityRecord: Codable, Identifiable {
    let id: String
    let type: ActivityType
    let did: String
    var serviceDid: String? = nil
    var serviceUrl: String? = nil
    let timestamp: String
    let status: ActivityStatus
    var details: [String: String] = [:]
}
