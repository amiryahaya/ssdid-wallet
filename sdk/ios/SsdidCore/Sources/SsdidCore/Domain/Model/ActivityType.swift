import Foundation

public enum ActivityType: String, Codable, CaseIterable {
    case IDENTITY_CREATED
    case IDENTITY_DEACTIVATED
    case KEY_ROTATED
    case DEVICE_ENROLLED
    case DEVICE_REMOVED
    case SERVICE_REGISTERED
    case AUTHENTICATED
    case TX_SIGNED
    case CREDENTIAL_RECEIVED
    case CREDENTIAL_PRESENTED
    case BACKUP_CREATED
}
