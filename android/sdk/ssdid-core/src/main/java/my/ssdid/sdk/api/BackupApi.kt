package my.ssdid.sdk.api

import my.ssdid.sdk.domain.backup.BackupManager

class BackupApi internal constructor(private val manager: BackupManager) {
    suspend fun create(passphrase: CharArray): Result<ByteArray> = manager.createBackup(passphrase)
    suspend fun restore(backupData: ByteArray, passphrase: CharArray): Result<Int> =
        manager.restoreBackup(backupData, passphrase)
}
