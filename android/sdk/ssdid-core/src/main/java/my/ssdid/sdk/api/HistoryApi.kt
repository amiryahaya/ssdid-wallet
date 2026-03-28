package my.ssdid.sdk.api

import my.ssdid.sdk.domain.history.ActivityRepository
import my.ssdid.sdk.domain.model.ActivityRecord

class HistoryApi internal constructor(private val repo: ActivityRepository) {
    suspend fun log(record: ActivityRecord) = repo.addActivity(record)
    suspend fun list(): List<ActivityRecord> = repo.listActivities()
    suspend fun listForDid(did: String): List<ActivityRecord> = repo.listActivitiesForDid(did)
    suspend fun clearAll() = repo.clearAll()
}
