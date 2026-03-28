package my.ssdid.sdk.domain.history

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import my.ssdid.sdk.domain.model.ActivityRecord
import my.ssdid.sdk.domain.model.ActivityStatus
import my.ssdid.sdk.domain.model.ActivityType
import org.junit.Before
import org.junit.Test

class InMemoryActivityRepository : ActivityRepository {
    private val records = mutableListOf<ActivityRecord>()

    override suspend fun addActivity(record: ActivityRecord) {
        records.add(0, record)
    }

    override suspend fun listActivities(): List<ActivityRecord> {
        return records.sortedByDescending { it.timestamp }
    }

    override suspend fun listActivitiesForDid(did: String): List<ActivityRecord> {
        return listActivities().filter { it.did == did }
    }

    override suspend fun clearAll() {
        records.clear()
    }
}

class ActivityRepositoryTest {

    private lateinit var repository: ActivityRepository

    @Before
    fun setup() {
        repository = InMemoryActivityRepository()
    }

    @Test
    fun `addActivity and listActivities round trip`() = runTest {
        val record = ActivityRecord(
            id = "1",
            type = ActivityType.IDENTITY_CREATED,
            did = "did:ssdid:abc123",
            timestamp = "2026-03-06T10:00:00Z",
            status = ActivityStatus.SUCCESS
        )
        repository.addActivity(record)

        val activities = repository.listActivities()
        assertThat(activities).hasSize(1)
        assertThat(activities[0].id).isEqualTo("1")
        assertThat(activities[0].type).isEqualTo(ActivityType.IDENTITY_CREATED)
        assertThat(activities[0].did).isEqualTo("did:ssdid:abc123")
        assertThat(activities[0].status).isEqualTo(ActivityStatus.SUCCESS)
    }

    @Test
    fun `listActivities returns sorted by timestamp descending`() = runTest {
        val older = ActivityRecord(
            id = "1",
            type = ActivityType.IDENTITY_CREATED,
            did = "did:ssdid:abc",
            timestamp = "2026-03-05T08:00:00Z",
            status = ActivityStatus.SUCCESS
        )
        val newer = ActivityRecord(
            id = "2",
            type = ActivityType.AUTHENTICATED,
            did = "did:ssdid:abc",
            timestamp = "2026-03-06T12:00:00Z",
            status = ActivityStatus.SUCCESS
        )
        repository.addActivity(older)
        repository.addActivity(newer)

        val activities = repository.listActivities()
        assertThat(activities).hasSize(2)
        assertThat(activities[0].id).isEqualTo("2")
        assertThat(activities[1].id).isEqualTo("1")
    }

    @Test
    fun `listActivitiesForDid filters correctly`() = runTest {
        val record1 = ActivityRecord(
            id = "1",
            type = ActivityType.IDENTITY_CREATED,
            did = "did:ssdid:alice",
            timestamp = "2026-03-06T10:00:00Z",
            status = ActivityStatus.SUCCESS
        )
        val record2 = ActivityRecord(
            id = "2",
            type = ActivityType.TX_SIGNED,
            did = "did:ssdid:bob",
            timestamp = "2026-03-06T11:00:00Z",
            status = ActivityStatus.SUCCESS
        )
        val record3 = ActivityRecord(
            id = "3",
            type = ActivityType.AUTHENTICATED,
            did = "did:ssdid:alice",
            timestamp = "2026-03-06T12:00:00Z",
            status = ActivityStatus.SUCCESS
        )
        repository.addActivity(record1)
        repository.addActivity(record2)
        repository.addActivity(record3)

        val aliceActivities = repository.listActivitiesForDid("did:ssdid:alice")
        assertThat(aliceActivities).hasSize(2)
        assertThat(aliceActivities.map { it.id }).containsExactly("3", "1").inOrder()

        val bobActivities = repository.listActivitiesForDid("did:ssdid:bob")
        assertThat(bobActivities).hasSize(1)
        assertThat(bobActivities[0].id).isEqualTo("2")

        val unknownActivities = repository.listActivitiesForDid("did:ssdid:unknown")
        assertThat(unknownActivities).isEmpty()
    }

    @Test
    fun `clearAll empties the list`() = runTest {
        repository.addActivity(
            ActivityRecord(
                id = "1",
                type = ActivityType.IDENTITY_CREATED,
                did = "did:ssdid:abc",
                timestamp = "2026-03-06T10:00:00Z",
                status = ActivityStatus.SUCCESS
            )
        )
        repository.addActivity(
            ActivityRecord(
                id = "2",
                type = ActivityType.AUTHENTICATED,
                did = "did:ssdid:abc",
                timestamp = "2026-03-06T11:00:00Z",
                status = ActivityStatus.SUCCESS
            )
        )
        assertThat(repository.listActivities()).hasSize(2)

        repository.clearAll()
        assertThat(repository.listActivities()).isEmpty()
    }

    @Test
    fun `addActivity preserves all fields including optional ones`() = runTest {
        val record = ActivityRecord(
            id = "1",
            type = ActivityType.SERVICE_REGISTERED,
            did = "did:ssdid:abc",
            serviceDid = "did:ssdid:service",
            serviceUrl = "https://example.com",
            timestamp = "2026-03-06T10:00:00Z",
            status = ActivityStatus.FAILED,
            details = mapOf("reason" to "timeout")
        )
        repository.addActivity(record)

        val retrieved = repository.listActivities()[0]
        assertThat(retrieved.serviceDid).isEqualTo("did:ssdid:service")
        assertThat(retrieved.serviceUrl).isEqualTo("https://example.com")
        assertThat(retrieved.status).isEqualTo(ActivityStatus.FAILED)
        assertThat(retrieved.details).containsEntry("reason", "timeout")
    }
}
