package my.ssdid.wallet.platform.notify

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import my.ssdid.sdk.domain.notify.LocalNotification
import my.ssdid.wallet.platform.notify.LocalNotificationStorage
import my.ssdid.wallet.platform.notify.localNotificationsStore
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalNotificationStorageTest {

    private lateinit var storage: LocalNotificationStorage
    private lateinit var context: Context

    @Before
    fun setup() = runTest {
        context = ApplicationProvider.getApplicationContext()
        // Clear any state left by a previous test
        context.localNotificationsStore.edit { it.clear() }
        storage = LocalNotificationStorage(context)
    }

    @Test
    fun `save and retrieve notifications`() = runTest {
        val notification = LocalNotification(
            id = "n1",
            mailboxId = "mbx_abc",
            identityName = "Alice",
            payload = "Hello from service",
            priority = "normal",
            receivedAt = "2026-03-13T10:00:00Z",
            isRead = false
        )
        storage.save(notification)

        val all = storage.allNotifications.first()
        assertThat(all).hasSize(1)
        assertThat(all[0].id).isEqualTo("n1")
        assertThat(all[0].identityName).isEqualTo("Alice")
    }

    @Test
    fun `unread count reflects read state`() = runTest {
        storage.save(LocalNotification("n1", "mbx_a", "Alice", "msg1", "normal", "2026-03-13T10:00:00Z", false))
        storage.save(LocalNotification("n2", "mbx_b", "Bob", "msg2", "normal", "2026-03-13T10:01:00Z", false))

        assertThat(storage.unreadCount.first()).isEqualTo(2)

        storage.markAsRead("n1")
        assertThat(storage.unreadCount.first()).isEqualTo(1)
    }

    @Test
    fun `mark all as read`() = runTest {
        storage.save(LocalNotification("n1", "mbx_a", "Alice", "msg1", "normal", "2026-03-13T10:00:00Z", false))
        storage.save(LocalNotification("n2", "mbx_b", "Bob", "msg2", "normal", "2026-03-13T10:01:00Z", false))

        storage.markAllAsRead()
        assertThat(storage.unreadCount.first()).isEqualTo(0)
    }

    @Test
    fun `delete notification`() = runTest {
        storage.save(LocalNotification("n1", "mbx_a", "Alice", "msg1", "normal", "2026-03-13T10:00:00Z", false))
        storage.delete("n1")

        val all = storage.allNotifications.first()
        assertThat(all).isEmpty()
    }

    @Test
    fun `duplicate id replaces existing`() = runTest {
        storage.save(LocalNotification("n1", "mbx_a", "Alice", "msg1", "normal", "2026-03-13T10:00:00Z", false))
        storage.save(LocalNotification("n1", "mbx_a", "Alice", "updated", "normal", "2026-03-13T10:00:00Z", false))

        val all = storage.allNotifications.first()
        assertThat(all).hasSize(1)
        assertThat(all[0].payload).isEqualTo("updated")
    }
}
