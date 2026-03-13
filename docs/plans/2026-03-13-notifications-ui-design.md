# Notifications UI Design

## Goal

Add an in-app notifications screen with unread badge to the SSDID Wallet on both Android and iOS.

## Architecture

New local storage layer persists notifications (DataStore on Android, JSON file on iOS). The existing `fetchAndDemux()` flow is modified: instead of dispatching to OS system notification and immediately ack'ing, it first saves to local storage, then acks the server. The in-app UI reads from local storage.

### Data Model — `LocalNotification`

| Field | Type | Description |
|-------|------|-------------|
| `id` | String | From server's `notificationId` |
| `mailboxId` | String | Server mailbox ID |
| `identityName` | String? | Resolved at fetch time |
| `payload` | String | Message text |
| `priority` | String | "normal" or "high" |
| `receivedAt` | String | ISO8601 timestamp |
| `isRead` | Bool | Default false, toggled on tap |

## Screens & Components

### 1. WalletHome Header Badge

Bell icon (`bell.fill` / `Icons.Default.Notifications`) to the left of the settings gear. When unread count > 0, a small red badge circle overlays the icon showing the count (max "99+"). Hidden when count is 0. Tapping navigates to the Notifications screen.

### 2. NotificationsScreen

Full screen, same layout pattern as TxHistoryScreen:

- Header: back button + "Notifications" title + "Mark All Read" text button (top right)
- Flat list in reverse chronological order
- Each row: colored dot (unread = accent blue, read = transparent) + identity name (caption) + payload text (body) + relative timestamp ("2m ago", "1h ago", "Yesterday")
- Swipe-to-delete removes the notification locally
- Empty state: bell icon + "No notifications" + "Notifications from services will appear here"
- Tapping a row marks it as read (dot disappears)

### 3. Behavior

- Notifications are informational only — tapping marks as read, no navigation
- Flat chronological list, not grouped by identity
- Identity name shown on each row for context

## Data Flow

```
Server → fetchAndDemux() → save to LocalNotificationStorage → ack server
                                    ↓
                          WalletHome reads unread count
                          NotificationsScreen reads full list
```

## Changes Per Platform

### Android

- New `LocalNotificationStorage` class (DataStore-backed)
- New `NotificationsScreen` composable + `NotificationsViewModel`
- New `Screen.Notifications` route in NavGraph
- Modify `WalletHomeScreen` header: add bell icon with badge
- Modify `NotifyManager.fetchAndDemux()`: save locally before ack

### iOS

- New `LocalNotificationStorage` class (JSON file via FileManager)
- New `NotificationsScreen` SwiftUI view
- New `.notifications` Route case + navigation destination
- Modify `WalletHomeScreen` header: add bell icon with badge
- Modify `NotifyManager.fetchAndDemux()`: save locally before ack
