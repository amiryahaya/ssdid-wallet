package my.ssdid.sdk.domain.history

import my.ssdid.sdk.domain.model.ActivityRecord

interface ActivityRepository {
    suspend fun addActivity(record: ActivityRecord)
    suspend fun listActivities(): List<ActivityRecord>
    suspend fun listActivitiesForDid(did: String): List<ActivityRecord>
    suspend fun clearAll()
}
