# 👻 Ghost

**Ghost remembers the conversations you forget.**

Ghost is an Android app that detects incoming messages across WhatsApp, SMS, Instagram, Messenger, and Telegram, learns your personal response patterns per contact, and tells you when a conversation is waiting longer than it usually would — without accessing messaging apps' private databases, sending data off-device, or requiring an account.

Built solo, from zero prior Android experience, as an exercise in shipping something real rather than following tutorials.

---

## The problem

Messaging apps are good at delivering messages. They're bad at answering one specific question: *"Who have I forgotten to reply to?"*

Most reminder tools use a blunt rule — "you haven't replied in 3 hours, reply now." Ghost's premise is different: **a fixed threshold is meaningless without knowing what's normal for that specific relationship.** Ghost learns the difference from your own response behavior.

---

## Supported platforms

| Platform | Status |
|---|---|
| WhatsApp | ✅ Fully tested on real device data |
| SMS | ✅ Fully tested on real device data |
| Instagram DMs | ✅ Fully tested on real device data |
| Messenger | ✅ Fully tested on real device data |
| Telegram | ⚠️ Implemented, not yet device-tested |

Every platform runs through the same generic pipeline:

**package name → platform detection → notification parser → normalized message**

This architecture allows new notification-based platforms to be added without creating a separate processing system for each app.

---

## How it works

```text
Messaging app notification
        ↓
NotificationListenerService
        ↓
Message parser
        ↓
Rule-based classifier
        ↓
Room database
        ↓
Conversation grouping
        ↓
Ghost intelligence
        ↓
Jetpack Compose UI
```

Ghost only processes information exposed through Android notifications. It does not access the private databases of messaging platforms.

All intelligence is calculated locally from response events generated through Ghost. There is no backend, account system, analytics service, or AI API call in this version.

---

# Key Features

## 📩 Multi-platform message detection

Ghost detects incoming notifications from multiple messaging platforms through Android's `NotificationListenerService`.

Currently supported:

- WhatsApp
- SMS
- Instagram
- Messenger
- Telegram

WhatsApp, SMS, Instagram, and Messenger have been validated using real device data. Telegram is implemented but still awaiting physical-device testing.

Ghost also:

- Handles grouped and multi-message notifications.
- Filters irrelevant system and summary notifications.
- Deduplicates notification replay.
- Normalizes messages from different platforms into the same internal pipeline.

---

## 💬 Conversation intelligence

Ghost organizes individual messages into conversations instead of treating every notification as an isolated event.

Features include:

- 1:1 conversation grouping.
- Group-chat support.
- Correct sender attribution inside group chats.
- English message classification.
- Arabic-script classification.
- Franco-Arabic classification.
- Question detection.
- Urgency detection.

Each message can be classified as:

```text
REPLY_REQUIRED
NO_REPLY_REQUIRED
POSSIBLY_REQUIRES_REPLY
```

And assigned:

```text
HIGH
MEDIUM
LOW
```

---

## 🎯 Latest-message priority

Conversation priority reflects the **latest successfully processed incoming message**.

Example:

```text
Normal message → MEDIUM
Urgent message → HIGH
Normal message → MEDIUM
```

The conversation correctly returns to `MEDIUM`.

Ghost also protects this state from notification problems:

- Duplicate notifications cannot overwrite the current priority.
- Older/out-of-order messages cannot overwrite a newer priority.
- Each individual message keeps its own historical classification.
- `WAITING_FOR_REPLY` logic remains separate from priority.

This ensures the dashboard represents what is happening **now**, rather than being stuck on an old urgent message.

---

## 🧠 Personal response baseline

Ghost learns how quickly you normally respond to each contact.

Every time a conversation is marked as replied, Ghost records a response event locally containing:

- Message received time.
- Reply time.
- Calculated response duration.

Ghost requires a minimum number of events before trusting a baseline.

Once enough data exists, Ghost can display information such as:

> Usually replies in ~2m

The baseline is personal to the relationship rather than being a universal threshold.

---

## ⏱️ Unusual-delay detection

Ghost compares the current unanswered wait against the contact's personal response baseline.

If the conversation is taking significantly longer than normal, Ghost can flag it as unusual.

For example:

```text
Usually replies in ~2m

Current wait:
5+ minutes

→ This is longer than usual
```

Ghost deliberately returns an "unknown" state when there is not enough historical data instead of pretending that it knows the user's normal behavior.

---

## 👻 Ghost Risk Score

Waiting conversations can receive a combined **0–100 Ghost Risk Score**.

The score considers signals including:

- Conversation priority.
- Latest message's reply requirement.
- Current wait compared with the personal baseline.
- Long waits when baseline data is not yet available.

The score is explainable.

Tapping the score shows:

> **Why am I seeing this?**

with human-readable reasons such as:

- Message appears high priority.
- Message appears to require a reply.
- Waiting much longer than usual for this contact.

Ghost therefore does not simply produce an unexplained number.

---

# 🔔 Ghost Notification System

Ghost can proactively remind the user about conversations without exposing private message content.

## New message awareness

Ghost aggregates incoming messages into a single notification.

### Single platform

```text
👻 Ghost

You have 3 new WhatsApp messages
```

### Multiple platforms

```text
👻 Ghost

You have 4 new messages
WhatsApp • Instagram • Messenger
```

Ghost dynamically handles singular/plural wording and detects which platforms contributed to the count.

## Waiting-for-reply alerts

Ghost can show:

```text
You have 2 conversations waiting for a reply
```

The count updates as conversations are marked replied.

## Unusual-delay alerts

When enough personal baseline data exists:

```text
1 conversation is taking longer than usual
```

These notifications are based on Ghost's actual personal-response data.

### Notification design

Ghost's notification system is designed to avoid spam:

- Notifications are aggregated.
- Existing Ghost notifications are updated instead of creating endless notifications.
- Message text is never displayed.
- Sender names are never displayed.
- Platform information can be shown.
- New-message notifications clear when Ghost is opened.
- Android 13+ notification permission is handled.

Ghost also ignores its own notifications inside the notification listener to prevent notification loops.

---

# 🚀 Go to Platform

Ghost provides a **"Go to Platform"** button inside the specific conversation detail screen.

The button is intentionally **not displayed on the main dashboard**.

The user can open a conversation:

```text
Ghost
  ↓
Conversation Detail
  ↓
Go to Platform
  ↓
WhatsApp / SMS / Instagram / Messenger / Telegram
```

This makes replying faster without requiring the user to close Ghost and manually find the original messaging app.

### Platform-aware launching

Ghost uses Android Intents and platform-aware launching logic.

It supports:

- WhatsApp deep links where possible.
- SMS `smsto:` launching.
- Instagram launching/deep linking where possible.
- Messenger launching.
- Telegram launching/deep linking where possible.
- Fallback to the platform's main application when a direct conversation link is unavailable.

If a platform cannot support a direct conversation link, Ghost gracefully opens the platform rather than treating the feature as a failure.

Most importantly:

**"Go to Platform" does not mark the Ghost conversation as replied.**

The reply state is only changed through Ghost's own reply-marking flow.

---

# 🎨 UI

Ghost uses Kotlin and Jetpack Compose for the interface.

Current UI areas include:

- Dashboard.
- Conversation detail.
- Settings.
- Statistics.
- Onboarding / permission explanation.
- Custom Ghost theme.
- Theme preferences.
- Platform-aware actions.
- Priority indicators.
- Ghost Risk Score.
- Expandable "Why am I seeing this?" explanations.

The interface was developed incrementally alongside the underlying engine and validated on a physical Android device.

---

# 🔐 Privacy & Security

Privacy is a core architectural decision in Ghost, not an afterthought.

## What Ghost can see

Ghost registers as an Android `NotificationListenerService`.

The user must explicitly grant Notification Access through Android system settings.

Once granted, Ghost can receive information exposed by supported notifications, including:

- Sender information.
- Message text exposed by the notification.
- Notification timestamps.
- Platform/package information.

## What Ghost deliberately does not access

Ghost does **not** access:

- WhatsApp's private message database.
- Other messaging apps' private databases.
- Contacts.
- Camera.
- Microphone.
- Location.
- Remote servers.
- Third-party analytics.

Ghost has no backend/network component in this version.

## Local storage

All application data is stored locally inside Ghost's Android application sandbox using Room/SQLite.

Ghost does not synchronize this information to a server.

## Database encryption

Ghost uses SQLCipher for encrypted database storage.

The security design includes:

- AES-256 encryption at rest.
- A randomly generated database passphrase.
- Android Keystore protection for the encryption key material.
- No plaintext database encryption key stored in the database.

This means extracting the raw database file alone should not expose readable Ghost data without also compromising the relevant device security/Keystore protection.

## User data control

Ghost provides user-facing controls for viewing and deleting stored data.

Notification Access can also be revoked by the user through Android system settings.

---

# ⚠️ Known Limitations

These limitations were discovered through real-device testing.

### Notification-only data

Ghost only knows what messaging platforms expose through notifications.

If an app does not generate a notification, Ghost cannot detect the message.

### Long messages

Messaging platforms can truncate long notification text before Ghost receives it.

Ghost cannot recover text that was never exposed through the notification.

### Muted conversations

Muted chats may not be detected because the messaging application may not generate a notification.

### Franco-Arabic

Franco-Arabic has no standardized spelling.

For example, the same phrase can be written in multiple ways.

Ghost contains common variants, but rule-based keyword matching cannot cover every possible spelling.

This is one of the reasons a more advanced classifier is a possible future direction.

### Telegram

Telegram support is implemented but has not yet been validated on the physical test device.

### Deep links

Not every messaging platform exposes a reliable public deep link to a specific conversation.

When direct launching is unavailable, Ghost falls back to opening the platform's main application.

### Dashboard performance

Larger conversation counts can produce minor loading lag because some data is currently loaded through per-row database queries.

A batched-loading or denormalized-cache approach could improve performance at larger scale.

---

# 🏗️ Architecture

```text
                Android Notifications
                         │
                         ▼
              NotificationListenerService
                         │
                         ▼
                 Platform Detection
                         │
                         ▼
                   Message Parser
                         │
                         ▼
                    Deduplication
                         │
                         ▼
                 MessageClassifier
                         │
                         ▼
                Conversation Grouping
                         │
                         ▼
                     Room DB
                         │
              ┌──────────┴──────────┐
              ▼                     ▼
     BaselineCalculator      RiskScoreCalculator
              │                     │
              └──────────┬──────────┘
                         ▼
                   GhostViewModel
                         │
                         ▼
                    Compose UI
                         │
              ┌──────────┴──────────┐
              ▼                     ▼
      Ghost Notifications     Go to Platform
```

---

# 🛠️ Tech Stack

- **Kotlin**
- **Jetpack Compose**
- **Room / SQLite**
- **SQLCipher**
- **Android Keystore**
- **NotificationListenerService**
- **Android Intents**
- **Kotlin Coroutines / StateFlow**
- **Gradle / Kotlin DSL**

### Architecture principles

- Local-first.
- No backend.
- No account required.
- No analytics.
- No third-party network calls.
- Notification-based integration.
- Explainable rule-based intelligence.
- Real-device validation.

---

# 🧪 Development & Testing

Ghost was built milestone by milestone with real-device testing throughout development.

Several important bugs were discovered only after processing real notification data, including:

1. WhatsApp replaying recent conversation history.
2. Group-summary notifications being stored as fake messages.
3. WhatsApp group titles changing with unread counts and fragmenting conversations.
4. WhatsApp system notifications being mistaken for conversations.
5. Low-signal messages incorrectly reopening `WAITING_FOR_REPLY`.
6. Franco-Arabic spelling differences causing classifier misses.
7. Conversation priority being overwritten by duplicate notifications.
8. Older/out-of-order messages overwriting newer conversation priorities.
9. Platform package/deep-link differences preventing external app launching.
10. Android 11+ package visibility affecting Instagram detection.

These issues were fixed through real testing instead of relying only on theoretical implementation.

---

# 📈 Development Status

Ghost's core product loop is implemented:

```text
Detect
  ↓
Parse
  ↓
Classify
  ↓
Group
  ↓
Store
  ↓
Learn
  ↓
Detect unusual behavior
  ↓
Score
  ↓
Explain
  ↓
Notify
  ↓
Open platform
  ↓
Reply
```

The project has progressed from a basic notification listener into a working local-first personal response assistant.

---

# 🔮 What's Next

Potential future improvements include:

- Replacing or augmenting the rule-based classifier with a local/LLM-based classifier.
- Better Franco-Arabic understanding.
- Reply suggestions.
- More robust platform-specific notification parsing.
- Telegram real-device validation.
- Performance optimization for large datasets.
- More extensive notification edge-case testing.
- Additional polish before a public release.

---

# 📄 License

This project is currently intended as a personal/portfolio project.

**All rights reserved** unless a separate license is explicitly added to the repository.

The source code may not be copied, redistributed, modified for redistribution, or deployed as another application without the author's permission.
