package my.ssdid.wallet.platform.storage

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import my.ssdid.wallet.domain.history.ActivityRepository
import my.ssdid.sdk.domain.model.ActivityRecord
import javax.inject.Inject
import javax.inject.Singleton

private val Context.activityStore: DataStore<Preferences> by preferencesDataStore(name = "ssdid_activity")

@Singleton
class ActivityRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ActivityRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val activitiesKey = stringPreferencesKey("activities")

    override suspend fun addActivity(record: ActivityRecord) {
        val current = listActivities().toMutableList()
        current.add(0, record)
        context.activityStore.edit { prefs ->
            prefs[activitiesKey] = json.encodeToString(current)
        }
    }

    override suspend fun listActivities(): List<ActivityRecord> {
        val jsonStr = context.activityStore.data.map { it[activitiesKey] }.first()
            ?: return emptyList()
        return try {
            val records: List<ActivityRecord> = json.decodeFromString(jsonStr)
            records.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize activities", e)
            emptyList()
        }
    }

    override suspend fun listActivitiesForDid(did: String): List<ActivityRecord> {
        return listActivities().filter { it.did == did }
    }

    override suspend fun clearAll() {
        context.activityStore.edit { prefs ->
            prefs.remove(activitiesKey)
        }
    }

    companion object {
        private const val TAG = "ActivityRepository"
    }
}
