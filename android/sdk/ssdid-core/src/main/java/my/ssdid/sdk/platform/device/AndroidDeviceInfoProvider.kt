package my.ssdid.sdk.platform.device

import android.os.Build
import my.ssdid.sdk.domain.device.DeviceInfoProvider

class AndroidDeviceInfoProvider : DeviceInfoProvider {
    override val deviceName: String get() = Build.MODEL ?: "Android Device"
    override val platform: String get() = "android"
}
