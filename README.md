# Flikkchat 💬

Made in India 🇮🇳 chat app, from the [Flikk](https://github.com/Tnaveenrohini/drishya) family.

## How it works
- **Chats stay on your phone** (SQLite). Nothing is stored on a server.
- Firebase Firestore only acts as a **postman**: the message is dropped in the receiver's inbox, the receiver's phone saves it locally and **deletes it from the server**.
- Ticks: 🕒 pending → ✓ sent → ✓✓ delivered → 🔵✓✓ read.

## Phase 1 features
- Login with name + phone number (Firebase Anonymous Auth, free)
- Find friends from your contacts
- 1-to-1 text chat, emoji
- Sent / delivered / read ticks
- Online / last seen / typing...
- Notifications (while the app is running in the background)
- Profile (name, about)

## Phase 2 features
- 📷 Photos & 🎥 videos (up to 16 MB). Sent as small chunks through Firestore and deleted after delivery, so it stays free (no Firebase Storage needed)
- 🎤 Voice messages
- 👥 Group chats: create, add/remove members (admin), exit group
- ↩️ Reply, ↪ forward, 🗑 delete for me / delete for everyone, copy
- 🔍 Search chats & messages, ⭐ starred messages

## Phase 3 features
- 🔒 End-to-end encryption (EC P-256 ECDH + AES-256-GCM). Text, photos, videos and voice are locked on the phone before sending; Firebase only sees encrypted data. Security code check available
- 🚫 Block / unblock
- ⏱️ Disappearing messages (24h / 7d / 90d), synced to both phones
- 🧹 Clear chat
- 💾 Backup & restore (.zip, can be saved to Google Drive)

## Setup
1. Add `app/google-services.json` from the Firebase console (package `com.flikk.chat`).
2. Firebase → Authentication → enable **Anonymous**.
3. Firestore → Rules → paste `firestore.rules`.
4. Push → GitHub Actions builds `Flikkchat.apk` → Releases.
