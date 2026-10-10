# FocusRefine - Google Play Store Submission Material

## 1. App Store Listing Details

### App Name (Max 30 chars)
**FocusRefine: Distraction Free**

### Short Description (Max 80 chars)
FocusRefine: Privacy first notification blocker with on device AI

### Full Description
FocusRefine is a modern, privacy-first Android notification filtering application designed to help you reclaim your attention and eliminate daily digital distractions. 

Powered by on-device intelligence (MediaPipe local semantic heuristics) and customizable filtering modes (Commute, Do Not Disturb, Work Focus, and custom user-defined modes), FocusRefine automatically categorizes incoming notifications into **Shown (Urgent)** and **Muted (Archived)** feeds.

#### Key Features:
- **Smart On-Device AI Filtering**: Automatically filters out low-priority notifications while ensuring critical alerts (emergencies, family calls, work deadlines) always get through.
- **Customizable Modes**: Tailor prompts and natural language rules for Commute, DND, or custom workflows.
- **Right-Swipe Learning**: Swipe right on any notification in your inbox to hide/unhide and teach FocusRefine your personal preferences with automatic sensitive data sanitization.
- **Live Update Ongoing Notification**: Keep track of suppressed notification counts in real-time with quick-toggle start/stop controls from your notification shade.
- **Material Design 3 UI**: Clean, dark-mode-first Material 3 interface with edge-to-edge cards and seamless navigation.
- **100% Privacy Focused**: All classification and data processing happen entirely on-device. No notification data ever leaves your phone.

---

## 2. Required Permissions & Justifications

1. **`BIND_NOTIFICATION_LISTENER_SERVICE`**:
   - Required core functionality to read incoming system notifications, evaluate them against your active mode rules, and suppress non-urgent notifications.
2. **`FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE`**:
   - Required to run FocusRefine's background filtering engine and maintain the live update suppression counter notification in the status bar.
3. **`POST_NOTIFICATIONS`**:
   - Required on Android 13+ to display the ongoing foreground service status notification and alert indicators.

---

## 3. Data Safety & Privacy Disclosures

- **Does the app collect or share any user data?** No.
- **Is all data processed locally on-device?** Yes. All notification filtering, AI classification, and user learning rule adjustments are stored locally in a secure Room database on your device.
- **Data Sharing:** None.
- **Security Practices:** Data is processed entirely locally on-device. No third-party tracking or analytics SDKs are embedded.

---

## 4. Google Play Console Pre-Publication Checklist & Compliance

### A. Developer Program Policies Compliance
- **Core Functionality Declaration:** FocusRefine's primary core purpose is notification management and distraction filtering.
- **Prominent Disclosure:** FocusRefine includes an explicit in-app disclosure during first-time launch (`OnboardingActivity`) informing users that notification access is used locally on-device to filter distractions and is never uploaded to cloud servers. This satisfies Google Play's restricted permissions policy requirements.

### B. Play App Signing Terms of Service
- **Acceptance:** You must accept the Play App Signing Terms of Service in the Google Play Console when creating your first release before uploading your production Android App Bundle (`app-release.aab`).

### C. US Export Laws & Encryption Compliance
- **Export Compliance:** Acknowledge in the Play Console that FocusRefine is subject to United States export laws due to its on-device AI/machine learning capabilities (MediaPipe). Certified that the application complies with all such laws and is authorized for export.
