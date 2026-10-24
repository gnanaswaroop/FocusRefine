# Proposed GitHub Issues for FocusRefine Security Enhancements

## Issue 1: Add Biometric Authentication (Fingerprint/Face Unlock) on App Startup

**Title:** Security: Add Biometric Authentication to protect notification history
**Labels:** `security`, `enhancement`

**Description:**
Currently, the FocusRefine app stores and displays user notification history in plain text on the `MainActivity` without any authentication barrier. If the user's phone is left unlocked or accessible by someone else, sensitive information from previous notifications can be viewed.

To improve security and user privacy (especially crucial for a privacy-first application handling sensitive alerts), we should implement Biometric Authentication (fingerprint or face unlock) upon launching the app or trying to open the UI.

**Proposed Changes:**
1. Add the `androidx.biometric:biometric` library dependency to `app/build.gradle.kts`.
2. Implement a `BiometricPrompt` in `MainActivity` (or a dedicated Authentication Activity like `SplashActivity` or a new `LockActivity` that launches before it).
3. The prompt should be triggered in `onResume` or similar lifecycle events. Ensure it handles cases where the user navigates away and comes back.
4. Provide fallback options (like device PIN/Pattern) if biometrics fail or aren't set up.
5. The app should require successful authentication before displaying the inbox (the `isUrgent` and `muted` feeds).
6. Add a setting in `SettingsActivity` to allow users to toggle this "App Lock" feature on or off. Store this preference in `SharedPreferences`.

**Implementation Details:**
```kotlin
val executor = ContextCompat.getMainExecutor(this)
val biometricPrompt = BiometricPrompt(this, executor,
    object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            // Unblock UI
        }
        // Handle errors...
    })

val promptInfo = BiometricPrompt.PromptInfo.Builder()
    .setTitle("Unlock FocusRefine")
    .setSubtitle("Authenticate to view your notifications")
    .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
    .build()

biometricPrompt.authenticate(promptInfo)
```

**Why this matters for Play Store:**
Enhancing the protection of sensitive user data aligns with Google Play's Developer Program Policies regarding User Data and Security. It demonstrates a commitment to privacy-first principles.

---

## Issue 2: Encrypt the Room Database for Notification Storage (Data at Rest)

**Title:** Security: Encrypt the local Room Database using SQLCipher
**Labels:** `security`, `enhancement`

**Description:**
The application stores the intercepted notification metadata, including the text content, in a local SQLite database via Room (`AppDatabase.kt`). While Android's application sandbox (UID isolation) protects this data from other non-root apps, root access or physical extraction tools could potentially dump the database. Currently, the database is unencrypted.

To provide stronger data-at-rest protection and align with strict privacy-first claims, the local Room database should be encrypted.

**Proposed Changes:**
1. Integrate `net.zetetic:android-database-sqlcipher` to provide SQLite encryption. Add it to `app/build.gradle.kts`.
2. Modify the Room `AppDatabase` builder in `AppDatabase.Companion.getDatabase()` to use a `SupportOpenHelperFactory` provided by SQLCipher.
3. Securely generate and store the encryption key using the Android Keystore system (`androidx.security.crypto.EncryptedSharedPreferences` or directly via `KeyStore`). DO NOT hardcode the key.
4. Handle database migrations securely if users are upgrading from an unencrypted version. Since the current database relies heavily on `fallbackToDestructiveMigration()`, a destructive migration might be acceptable for a beta version, but a proper manual migration process (read unencrypted -> write encrypted) is preferred to avoid losing user's learned rules and history.

**Implementation Details:**
```kotlin
// Generate a 256-bit key securely
// ... using Android KeyStore ...
val passphrase = getOrGenerateKey()

val factory = SupportFactory(passphrase)
Room.databaseBuilder(
    context.applicationContext,
    AppDatabase::class.java,
    "focusrefine_database"
)
.openHelperFactory(factory)
.build()
```

**Why this matters for Play Store:**
Encrypting sensitive user data (like personal notification contents) at rest provides defense-in-depth and reduces the risk associated with potential data exposure, supporting the app's claim of being a privacy-first solution.
