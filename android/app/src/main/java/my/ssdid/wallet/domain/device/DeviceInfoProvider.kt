package my.ssdid.wallet.domain.device

interface DeviceInfoProvider {
    val deviceName: String
    val platform: String
}
