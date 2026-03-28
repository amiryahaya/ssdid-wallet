import Foundation

/// Facade for activity history operations.
public struct HistoryApi {
    private let repo: ActivityRepository

    init(repo: ActivityRepository) {
        self.repo = repo
    }

    public func log(_ record: ActivityRecord) async throws {
        try await repo.addActivity(record)
    }

    public func list() async -> [ActivityRecord] {
        await repo.listActivities()
    }

    public func listForDid(_ did: String) async -> [ActivityRecord] {
        await repo.listActivitiesForDid(did)
    }

    public func clearAll() async throws {
        try await repo.clearAll()
    }
}
