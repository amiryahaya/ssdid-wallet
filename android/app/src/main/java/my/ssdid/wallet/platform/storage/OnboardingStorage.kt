package my.ssdid.wallet.platform.storage

interface OnboardingStorage {
    suspend fun isOnboardingCompleted(): Boolean
    suspend fun setOnboardingCompleted()
}
