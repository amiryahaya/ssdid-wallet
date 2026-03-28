package my.ssdid.sdk.testing

import my.ssdid.sdk.domain.history.ActivityRepository
import my.ssdid.sdk.domain.model.ActivityRecord

class InMemoryActivityRepository : ActivityRepository {
    private val activities = mutableListOf<ActivityRecord>()

    override suspend fun addActivity(record: ActivityRecord) { activities.add(record) }
    override suspend fun listActivities(): List<ActivityRecord> = activities.toList()
    override suspend fun listActivitiesForDid(did: String): List<ActivityRecord> = activities.filter { it.did == did }
    override suspend fun clearAll() { activities.clear() }

    // Test helper
    fun allActivities(): List<ActivityRecord> = activities.toList()
}
