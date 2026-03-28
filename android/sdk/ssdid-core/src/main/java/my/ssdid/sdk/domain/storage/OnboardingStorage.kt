package my.ssdid.sdk.domain.storage

interface OnboardingStorage {
    suspend fun isOnboardingCompleted(): Boolean
    suspend fun setOnboardingCompleted()
}
