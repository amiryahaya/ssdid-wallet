# Device Management

The SDK supports multi-device identity usage through a pairing protocol. One device initiates pairing, the other joins, and the primary device approves.

## Initiate Pairing

Start a pairing session from the primary device. Returns pairing data that must be shared with the secondary device (e.g., via QR code).

### Kotlin

```kotlin
val identity = sdk.identity.get(keyId)!!
sdk.device.initiatePairing(identity).onSuccess { pairingData ->
    println("Pairing ID: ${pairingData.pairingId}")
    println("Challenge: ${pairingData.challenge}")
    // Encode pairingData as QR code for the secondary device to scan
}
```

### Swift

```swift
let identity = try await sdk.vault.getIdentity(keyId: keyId)!
let pairing = try await sdk.client.initiatePairing(identity: identity)
print("Pairing ID: \(pairing.pairingId)")
```

## Join Pairing (Secondary Device)

The secondary device scans the pairing data and joins the session.

### Kotlin

```kotlin
sdk.device.joinPairing(
    did = identity.did,
    pairingId = pairingData.pairingId,
    challenge = pairingData.challenge,
    identity = secondaryIdentity,
    deviceName = "Alice's Tablet"
).onSuccess { confirmationCode ->
    println("Joined pairing. Confirmation: $confirmationCode")
}
```

## Check Pairing Status

Poll the pairing status from either device.

### Kotlin

```kotlin
sdk.device.checkPairingStatus(identity.did, pairingData.pairingId)
    .onSuccess { status ->
        println("Status: ${status.state}")
    }
```

## Approve Pairing (Primary Device)

The primary device approves the pairing request after verifying the confirmation code.

### Kotlin

```kotlin
sdk.device.approvePairing(identity, pairingData.pairingId).onSuccess {
    println("Pairing approved")
}
```

## List Devices

List all devices authorized for an identity.

### Kotlin

```kotlin
sdk.device.listDevices(identity).onSuccess { devices ->
    devices.forEach { device ->
        println("${device.name}: ${device.keyId}")
    }
}
```

### Swift

```swift
let devices = try await sdk.client.listDevices(identity: identity)
for device in devices {
    print("\(device.name): \(device.keyId)")
}
```

## Revoke a Device

Remove a device's authorization. The revoked device can no longer act on behalf of the identity.

### Kotlin

```kotlin
sdk.device.revokeDevice(identity, targetKeyId = deviceKeyId).onSuccess {
    println("Device revoked")
}
```

### Swift

```swift
try await sdk.client.revokeDevice(identity: identity, targetKeyId: deviceKeyId)
```

## Pairing Flow Summary

1. **Primary** calls `initiatePairing()` and displays QR code
2. **Secondary** scans QR and calls `joinPairing()`
3. Both devices can call `checkPairingStatus()` to monitor
4. **Primary** calls `approvePairing()` to authorize the secondary device
5. Both devices can now sign and act on behalf of the identity

## See Also

- [Key Rotation](key-rotation.md) -- rotating keys across devices
- [Identity Management](identity-management.md) -- identity lifecycle
