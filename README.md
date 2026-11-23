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

## Setup
1. Add `app/google-services.json` from the Firebase console (package `com.flikk.chat`).
2. Firebase → Authentication → enable **Anonymous**.
3. Firestore → Rules → paste `firestore.rules`.
4. Push → GitHub Actions builds `Flikkchat.apk` → Releases.
