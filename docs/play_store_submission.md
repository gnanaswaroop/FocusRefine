# FocusFlow - Google Play Store Submission Material

## 1. App Store Listing Details

### App Name
**FocusFlow: Intelligent Notification & Focus Filter**

### Short Description (80 characters max)
Filter notification noise with on-device AI and semantic matching. Stay focused.

### Full Description
FocusFlow is a modern, privacy-first Android notification filtering application designed to help you reclaim your attention and eliminate daily digital distractions. 

Powered by on-device intelligence (MediaPipe Gemini Nano / local semantic heuristics) and customizable filtering modes (Commute, Do Not Disturb, Work Focus, and custom user-defined modes), FocusFlow automatically categorizes incoming notifications into **Shown (Urgent)** and **Muted (Archived)** feeds.

#### Key Features:
- **Smart On-Device Filtering**: Automatically filters out low-priority notifications while ensuring critical alerts (emergencies, family calls, work deadlines) always get through.
- **Customizable Modes**: Tailor prompts and natural language rules for Commute, DND, or custom workflows.
- **Right-Swipe Learning**: Swipe right on any notification in your inbox to hide/unhide and teach FocusFlow your personal preferences with automatic sensitive data sanitization.
- **Live Update Ongoing Notification**: Keep track of suppressed notification counts in real-time with quick-toggle start/stop controls from your notification shade.
- **Material Design 3 UI**: Clean, dark-mode-first Material 3 interface with edge-to-edge cards and seamless navigation.
- **100% Privacy Focused**: All classification and data processing happen entirely on-device. No notification data ever leaves your phone.

---

## 2. Required Permissions & Justifications

1. **`BIND_NOTIFICATION_LISTENER_SERVICE`**:
   - Required core functionality to read incoming system notifications, evaluate them against your active mode rules, and suppress non-urgent notifications.
2. **`FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE`**:
   - Required to run FocusFlow's background filtering engine and maintain the live update suppression counter notification in the status bar.
3. **`POST_NOTIFICATIONS`**:
   - Required on Android 13+ to display the ongoing foreground service status notification and alert indicators.

---

## 3. Data Safety & Privacy Disclosures

- **Does the app collect or share any user data?** No.
- **Is all data processed locally on-device?** Yes. All notification filtering, AI classification, and user learning rule adjustments are stored locally in an encrypted Room database on your device.
- **Data Sharing:** None.
- **Security Practices:** Data is encrypted in transit and at rest locally. No third-party tracking or analytics SDKs are embedded.
