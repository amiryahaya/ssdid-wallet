import Foundation

/// Protocol for persisting activity records.
protocol ActivityRepository {
    func addActivity(_ record: ActivityRecord) async throws
    func listActivities() async -> [ActivityRecord]
    func listActivitiesForDid(_ did: String) async -> [ActivityRecord]
    func clearAll() async throws
}

/// UserDefaults-based implementation of ActivityRepository.
final class UserDefaultsActivityRepository: ActivityRepository {

    private static let storageKey = "ssdid_activity_records"
    private let defaults: UserDefaults
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func addActivity(_ record: ActivityRecord) async throws {
        var records = await listActivities()
        records.insert(record, at: 0) // Most recent first

        // Keep a reasonable limit
        if records.count > 500 {
            records = Array(records.prefix(500))
        }

        let data = try encoder.encode(records)
        defaults.set(data, forKey: Self.storageKey)
    }

    func listActivities() async -> [ActivityRecord] {
        guard let data = defaults.data(forKey: Self.storageKey) else {
            return []
        }
        return (try? decoder.decode([ActivityRecord].self, from: data)) ?? []
    }

    func listActivitiesForDid(_ did: String) async -> [ActivityRecord] {
        let all = await listActivities()
        return all.filter { $0.did == did }
    }

    func clearAll() async throws {
        defaults.removeObject(forKey: Self.storageKey)
    }
}
