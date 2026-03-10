package my.ssdid.wallet.feature.backup

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import androidx.lifecycle.SavedStateHandle
import my.ssdid.wallet.domain.backup.BackupManager
import my.ssdid.wallet.platform.biometric.BiometricAuthenticator
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {

    @get:Rule
    val mainDispatcherRule = BackupMainDispatcherRule()

    private lateinit var viewModel: BackupViewModel
    private lateinit var backupManager: BackupManager
    private lateinit var biometricAuth: BiometricAuthenticator

    @Before
    fun setup() {
        backupManager = mockk(relaxed = true)
        biometricAuth = mockk(relaxed = true)
        viewModel = BackupViewModel(backupManager, biometricAuth, SavedStateHandle())
    }

    @Test
    fun `initial state is Idle`() {
        assertThat(viewModel.state.value).isEqualTo(BackupState.Idle)
    }

    @Test
    fun `createBackup success sets state to Success with backup bytes`() = runTest {
        val backupBytes = byteArrayOf(1, 2, 3, 4, 5)
        coEvery { backupManager.createBackup("passphrase") } returns Result.success(backupBytes)

        viewModel.createBackup("passphrase")

        val state = viewModel.state.value
        assertThat(state).isInstanceOf(BackupState.Success::class.java)
        assertThat((state as BackupState.Success).backupBytes).isEqualTo(backupBytes)
        assertThat(viewModel.lastBackupBytes).isEqualTo(backupBytes)
    }

    @Test
    fun `createBackup failure sets Error state`() = runTest {
        coEvery { backupManager.createBackup("bad") } returns Result.failure(
            RuntimeException("No identities to back up")
        )

        viewModel.createBackup("bad")

        val state = viewModel.state.value
        assertThat(state).isInstanceOf(BackupState.Error::class.java)
        assertThat((state as BackupState.Error).message).isEqualTo("No identities to back up")
    }

    @Test
    fun `restoreBackup success sets RestoreSuccess with count`() = runTest {
        val backupData = byteArrayOf(10, 20, 30)
        coEvery { backupManager.restoreBackup(backupData, "passphrase") } returns Result.success(3)

        viewModel.restoreBackup(backupData, "passphrase")

        val state = viewModel.state.value
        assertThat(state).isInstanceOf(BackupState.RestoreSuccess::class.java)
        assertThat((state as BackupState.RestoreSuccess).count).isEqualTo(3)
    }

    @Test
    fun `restoreBackup failure sets Error state`() = runTest {
        val backupData = byteArrayOf(10, 20, 30)
        coEvery { backupManager.restoreBackup(backupData, "wrong") } returns Result.failure(
            RuntimeException("HMAC verification failed")
        )

        viewModel.restoreBackup(backupData, "wrong")

        val state = viewModel.state.value
        assertThat(state).isInstanceOf(BackupState.Error::class.java)
        assertThat((state as BackupState.Error).message).isEqualTo("HMAC verification failed")
    }

    @Test
    fun `resetState sets Idle`() = runTest {
        val backupBytes = byteArrayOf(1, 2, 3)
        coEvery { backupManager.createBackup("pass") } returns Result.success(backupBytes)
        viewModel.createBackup("pass")
        assertThat(viewModel.state.value).isInstanceOf(BackupState.Success::class.java)

        viewModel.resetState()

        assertThat(viewModel.state.value).isEqualTo(BackupState.Idle)
    }

    @Test
    fun `restoreBackup via loaded file bytes`() = runTest {
        val fileBytes = byteArrayOf(5, 6, 7)
        coEvery { backupManager.restoreBackup(fileBytes, "pass") } returns Result.success(2)

        viewModel.onBackupFileLoaded(fileBytes)
        viewModel.restoreBackup("pass")

        val state = viewModel.state.value
        assertThat(state).isInstanceOf(BackupState.RestoreSuccess::class.java)
        assertThat((state as BackupState.RestoreSuccess).count).isEqualTo(2)
        // After successful restore, loadedFileBytes should be cleared
        assertThat(viewModel.loadedFileBytes.value).isNull()
    }

    @Test
    fun `onBackupSaved clears lastBackupBytes`() = runTest {
        val backupBytes = byteArrayOf(1, 2, 3)
        coEvery { backupManager.createBackup("pass") } returns Result.success(backupBytes)
        viewModel.createBackup("pass")
        assertThat(viewModel.lastBackupBytes).isNotNull()

        viewModel.onBackupSaved()

        assertThat(viewModel.lastBackupBytes).isNull()
    }

    @Test
    fun `restoreBackup without loaded file does nothing`() = runTest {
        // Don't call onBackupFileLoaded, so loadedFileBytes is null
        viewModel.restoreBackup("pass")

        // State should remain Idle since the method returns early
        assertThat(viewModel.state.value).isEqualTo(BackupState.Idle)
    }

    @Test
    fun `restoreUri is extracted from SavedStateHandle when provided`() {
        val vm = BackupViewModel(
            backupManager,
            biometricAuth,
            SavedStateHandle(mapOf("restoreUri" to "content://my.ssdid.drive.fileprovider/backups/x.enc"))
        )
        assertThat(vm.restoreUri).isEqualTo("content://my.ssdid.drive.fileprovider/backups/x.enc")
    }

    @Test
    fun `restoreUri defaults to empty when not in SavedStateHandle`() {
        assertThat(viewModel.restoreUri).isEmpty()
    }

    @Test
    fun `restoreBackup with explicit bytes does not clear loadedFileBytes`() = runTest {
        val fileBytes = byteArrayOf(5, 6, 7)
        val restoreBytes = byteArrayOf(9, 10, 11)
        coEvery { backupManager.restoreBackup(restoreBytes, "pass") } returns Result.success(1)

        viewModel.onBackupFileLoaded(fileBytes)
        viewModel.restoreBackup(restoreBytes, "pass")

        // The explicit-bytes overload does NOT clear loadedFileBytes
        assertThat(viewModel.loadedFileBytes.value).isEqualTo(fileBytes)
    }
}

class BackupMainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }
    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
