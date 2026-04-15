# Silent Caller 📵

An Android utility application that allows users to permanently silence incoming calls from specific phone numbers. Built using Java and Android Studio.

---

## 📱 About The App

Silent Caller gives users full control over which phone numbers can ring on their device. Unlike the temporary silence using the volume button, this app offers a permanent solution — specific numbers are always silenced automatically while all other calls ring normally.

---

## ✨ Features

- **Selective Silent List** — Add specific phone numbers that will always call silently
- **Manual Add** — Manually type and add any phone number to the silent list with an optional label or nickname
- **Volume Button Trigger** — When you press the volume button to silence a ringing call, a popup appears asking if you want to permanently add that number to the silent list
- **Floating Button on Call Screen** — An "Add to Silent List" button appears directly on the incoming call screen so you can add the number with one tap during the call
- **Remove Anytime** — Remove any number from the silent list whenever you want
- **Survives Reboot** — The silent feature continues working automatically after the phone is restarted
- **Works in Background** — No need to keep the app open — silent call detection runs in the background at all times

---

## 🔄 How It Works

### Adding a Number to Silent List

**Method 1 — Manual:**
1. Open the app
2. Tap the Add button
3. Type the phone number and optional label
4. Tap Save — the number is now permanently silenced

**Method 2 — Volume Button During Call:**
1. Someone calls you
2. You press the volume button to silence the ringing
3. A popup appears: "Add this number to your permanent silent list?"
4. Tap "Yes, Add" — the number is saved automatically
5. All future calls from this number will be permanently silent

**Method 3 — Floating Button During Call:**
1. Someone calls you
2. A floating "Add to Silent List" button appears on the call screen
3. Tap the button — number is added to the silent list instantly
4. Current call is silenced and all future calls from this number will be silent

### When a Silenced Number Calls
- The incoming call screen appears as normal
- No sound or ringtone plays
- You can still answer, decline, or ignore the call
- All other calls from numbers not on the list ring normally

---

## 🛠️ Tech Stack

| Technology | Purpose |
|---|---|
| Java | Programming language |
| Android Studio | IDE |
| Room Database | Local storage for silent numbers list |
| MVVM Architecture | App structure and data management |
| BroadcastReceiver | Detecting incoming calls in background |
| AudioManager | Controlling ringer volume |
| WindowManager | Floating overlay button on call screen |
| LiveData | Real-time UI updates |
| ExecutorService | Background thread database operations |

---

## 📋 Requirements

- Android 10 (API 29) and above
- Tested up to Android 15 (API 35)

---

## 🔐 Permissions Required

| Permission | Reason |
|---|---|
| READ_PHONE_STATE | Detect incoming calls |
| READ_CALL_LOG | Read the caller's phone number |
| MODIFY_AUDIO_SETTINGS | Control ringer volume to silence calls |
| RECEIVE_BOOT_COMPLETED | Restart the background listener after phone reboot |
| SYSTEM_ALERT_WINDOW | Show floating button and popup over the incoming call screen |

All permissions are requested at runtime with a clear explanation. The app guides you to Settings if any permission needs to be granted manually.

---

## 🗄️ Database Structure

**SilentNumber Entity:**

| Field | Type | Description |
|---|---|---|
| id | int | Auto-generated primary key |
| phoneNumber | String | The silenced phone number (unique) |
| label | String | Optional nickname e.g. Spam, Unknown |
| addedAt | long | Timestamp when number was added |
