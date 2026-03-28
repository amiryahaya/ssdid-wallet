import Foundation

public struct ActivityRecord: Codable, Identifiable {
    public let id: String
    public let type: ActivityType
    public let did: String
    public var serviceDid: String? = nil
    public var serviceUrl: String? = nil
    public let timestamp: String
    public let status: ActivityStatus
    public var details: [String: String] = [:]

    public init(
        id: String,
        type: ActivityType,
        did: String,
        serviceDid: String? = nil,
        serviceUrl: String? = nil,
        timestamp: String,
        status: ActivityStatus,
        details: [String: String] = [:]
    ) {
        self.id = id
        self.type = type
        self.did = did
        self.serviceDid = serviceDid
        self.serviceUrl = serviceUrl
        self.timestamp = timestamp
        self.status = status
        self.details = details
    }
}
