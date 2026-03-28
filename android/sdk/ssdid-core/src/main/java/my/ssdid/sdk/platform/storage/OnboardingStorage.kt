package my.ssdid.sdk.platform.storage

interface OnboardingStorage {
    suspend fun isOnboardingCompleted(): Boolean
    suspend fun setOnboardingCompleted()
}
