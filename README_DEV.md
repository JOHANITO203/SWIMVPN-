# SWIMVPN+ V1 - Development Overview

## Architecture
- **Backend**: Node.js / Express (running on port 3000).
- **Web Preview**: React + Tailwind (Simulates the Android app UI).
- **Android App**: Kotlin + Jetpack Compose (Source code in `/android`).

## V1 Scope
1. **Onboarding**: Multi-step onboarding with 7-day trial highlight.
2. **Connectivity**: One-tap connection logic.
3. **Server Selection**: Authorized server list fetched from backend.
4. **Subscription Management**: Access via user number, renewal support.
5. **Settings**: Simple vs. Advanced configurations.

## How to Build the Android APK
1. Export the project.
2. Open the `/android` folder in Android Studio.
3. Allow Gradle to sync.
4. Run `assembleDebug` to produce the APK.

## Source of Truth
- The backend handles all critical business logic (trial status, server lists).
- The frontend/Android app interacts via standard REST API endpoints.
