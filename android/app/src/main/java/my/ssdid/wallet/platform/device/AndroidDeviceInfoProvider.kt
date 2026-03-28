package my.ssdid.wallet.platform.device

import android.os.Build
import my.ssdid.sdk.domain.device.DeviceInfoProvider
import javax.inject.Inject

class AndroidDeviceInfoProvider @Inject constructor() : DeviceInfoProvider {
    override val deviceName: String get() = Build.MODEL ?: "Android Device"
    override val platform: String get() = "android"
}
