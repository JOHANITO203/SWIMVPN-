# Google Play Data Safety Draft

Date: 2026-05-22

This is a draft and must be validated against the final production backend, Android build, logging, analytics, and privacy policy before submission.

## Product Context

SWIMVPN is an Android VPN app. It may process profile/access information, device identifiers used for entitlement/device binding, order/payment status, imported VPN configs stored locally or sent to backend only when the user explicitly imports/submits them, and runtime diagnostics needed to operate the VPN connection.

## Conservative Data Categories To Review

### Personal Info

Potentially involved:

- user identifier / profile identifier,
- contact data if the order/support flow collects it,
- payment-related order reference, but not necessarily raw payment credentials if handled by payment providers.

Validation needed:

- confirm exact fields sent by Android to gateway-service,
- confirm support bot/admin flows,
- confirm whether email/phone are mandatory in production.

### App Activity / App Info and Performance

Potentially involved:

- VPN runtime state,
- app diagnostics,
- crash/build diagnostics if any tooling is enabled,
- latency/probe metadata for server selection.

Validation needed:

- confirm whether any third-party analytics/crash SDK exists in release,
- confirm retention and transport.

### Device or Other IDs

Potentially involved:

- device ID used for entitlement/device binding,
- Android installation/runtime identifiers.

Validation needed:

- confirm exact generation/storage path,
- confirm whether it is resettable by reinstall.

### User Content

Potentially involved:

- imported VPN config text/QR/manual input.

Validation needed:

- confirm whether imported configs remain local only or are transmitted to backend in specific flows,
- confirm raw config preservation policy and admin visibility restrictions.

## Security / Sharing Position To Validate

- VPN traffic is routed through the selected VPN configuration/provider.
- SWIMVPN should not sell user traffic.
- Payment processing is delegated to supported payment flows.
- Backend entitlement logic remains the source of truth for paid/trial access.

## Required Before Submission

- Final privacy policy URL.
- Final Data Safety answers entered in Play Console.
- Final review of logs, crash reporting, analytics, payment flow, support bot, Telegram admin notifications, and backend retention.
