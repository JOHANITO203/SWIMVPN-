# Google Play VpnService Declaration Draft

Date: 2026-05-22

Reference policy: https://support.google.com/googleplay/android-developer/answer/12564964

## Core Purpose

SWIMVPN uses Android `VpnService` to provide a user-facing VPN connection. The service is used to establish and maintain VPN tunnels from the Android device to VPN configurations selected or imported by the user.

## User Benefit

The app helps users:

- connect to VPN access purchased in-app,
- activate available trial VPN access during the pre-release period,
- import their own VPN configs and use the app as a client,
- view server/node guidance and runtime indicators before connecting.

## What The VPN Service Does

- Starts only as part of user-visible VPN connection behavior.
- Maintains foreground runtime visibility while active.
- Routes traffic according to the selected VPN/runtime mode and configuration.
- Reports connection state back to the UI.

## What The VPN Service Must Not Do

- It must not be used for ad tracking.
- It must not silently collect unrelated app traffic for monetization.
- It must not expose premium server/config access to expired users.
- It must not bypass backend entitlement checks for paid/trial access.

## Implementation Notes To Validate

- Confirm the final foreground notification mirrors the app state.
- Confirm all VPN runtime modes are accurately described in user-facing copy.
- Confirm imported configs remain user-controlled and raw config data is preserved.
- Confirm expired trial/subscription users can still enter freemium/import mode without premium backend access.
