# TODO

## [2026-05-19] Trial Store Follow-up
- [x] Add an authenticated admin import path for `TrialConfig` batches.
- [x] Recover pending trial grants automatically after `TrialConfig` import.
- [x] Enforce one `TrialGrant` per customer/campaign in PostgreSQL.
- [x] Persist expired Trial Store grants and emit `TRIAL_EXPIRED`.
- [ ] Populate production Trial Store configs before enabling broad campaign activation.
- [ ] Add admin reporting for `TRIAL_PENDING_NO_CAPACITY`, `TRIAL_CONFIG_ASSIGNED`, and depleted trial stock.
- [ ] After the migration window, remove creation support for legacy `Order/TRIAL:3D` paths if no production records still need it.
- [ ] Confirm Android pending trial refresh behavior after a no-capacity trial grant is later assigned.
## [2026-05-18] VPN Update Audit
- [ ] Execute Lot A: document responsibility map for parser, normalization, runtime, backend metadata, entitlement, and inventory.
- [ ] Execute Lot B: build parser/runtime matrix for VLESS, VMess, Trojan, Shadowsocks, JSON Xray/V2Ray, Clash/sing-box, Happ wrappers, and unsupported modern schemes.
- [ ] Execute Lot C: build Android runtime readiness/liveness matrix for Xray, SOCKS, tun2socks, reconnect, network handoff, and persisted runtime state.
- [ ] Execute Lot D: verify backend VPN/inventory boundary, including raw config preservation, device binding, entitlement, and non-exposure of premium configs to expired users.
- [ ] Execute Lot E: define camouflage scoring versus real obfuscation support without inventing server-side capabilities.
- [ ] Execute Lot F: collect performance evidence before tuning latency, DNS, MTU, Xray, or tun2socks behavior.
- [ ] Execute Lot G: run real-device QA for imported configs, backend premium configs, freemium, screen-off, Wi-Fi/mobile handoff, and runtime failures.

## [2026-05-09] SwimPay Live QA
- [ ] Configure the rotated `SWIMPAY_SECRET_KEY` and `SWIMPAY_WEBHOOK_SECRET` in production env.
- [ ] Save `https://api.swimvpn.pro/api/v1/payments/swimpay/webhook` in SwimPay Developer Integration.
- [ ] Run SwimPay test webhook.
- [ ] Create a SWIMVPN `SWIMPAY` checkout from Android.
- [ ] Manually confirm in SwimPay and verify premium fulfillment occurs only after signed `payment.confirmed`.
- [ ] Send a mismatch test webhook and confirm the order stays PENDING with no fulfillment.
## Now
- Continue Android VPN core maturity from the now-live runtime baseline:
  - extend true routing/system integration after validated `FULL_TUNNEL` and `LOCAL_PROXY`
  - prioritize split-tunnel, DNS handling, and Android-system routing integration after auto-connect truth + kill-switch honesty
  - keep `Proxy` as the recommended user-facing mode and `Tunnel` as the full-device mode
- Set up Docker Compose networking logic to replace `0.0.0.0` with proper service names.

## Next
- Implement Telegram Admin webhook integration for order notifications and fulfillment alerts.
- Build basic Admin Panel Frontend (optional, or focus on Bot).
- End-to-end testing of VpnState transitions from Android UI to Backend.

## Done
- Scaffold and implement 6 backend microservices and shared libraries.
- Implement inter-service TCP communication and HTTP Gateway.
- Define Android Network Models and API Service.
- Connect Android `MainViewModel` and `SubscriptionScreen` to the real backend.
- Perform PostgreSQL migration to enable `nanoid` extension and seed initial plans.
- Implement robust `vpn-config-engine-service` logic for VLESS (Reality/GRPC) and Shadowsocks.
- Add DTO validation using `class-validator` to all TCP/HTTP endpoints.
- Replace mock logic in services with real Prisma DB operations.
- Refactor Android backend integration and polish UI (VpnState handling, persistent selection).
- Implement OCR and QR code scanning for the "Import via QR" feature in Android.
- Implement Admin authentication and Telegram admin control layer.

## Alignment Follow-ups
- Expand vpn-config-engine protocol coverage beyond current parser support to match full priority ecosystem (VMess, Trojan, JSON Xray, JSON V2Ray).
- Add deeper admin session hardening (token rotation / refresh flow) when moving beyond minimal MVP compliance.
- Add integration tests for order paid->fulfilled path and session-bound admin auth flow.


## Notification Bot Next Steps
- Add pre-expiry reminder workflow (quota/offer reminder) in a separate scheduler-safe batch.
- Add explicit delivery history endpoint contract for admin panel consumption.
- Add integration tests for inventory-delivery-service -> 
otification-bot-service event handoff.
- Re-test manual card proof flow on Telegram after redeploy:
  - checkout deep link opens `start=card_<orderRef>`
  - screenshot as photo is accepted
  - screenshot as image document is accepted
  - proof after bot restart is recovered from persisted `CARD_PAYMENT_FLOW_OPENED`
  - user contact confirmation is forwarded to the review chat
  - admin approve/reject still triggers fulfillment/rejection email


## Admin Support Bot Next Steps
- Persist support escalation tickets in PostgreSQL (support_cases) when this scope is approved.
- Add admin command handlers for /order, /resend, /status, /help in support-bot context (currently only guided customer escalation flow is implemented).
- Add optional callback actions in admin group (mark delivered, copy VPN link) when linked to finalized support-case storage.
- Add integration tests covering Telegram callback flow and support-group relay failure paths.

## Secrets Follow-up
- Provide real RESEND_API_KEY in root .env and ackend/.env before enabling delivery emails in production.
- Verify sender domain/email (support@swimvpn.pro) is validated in Resend account.
- Rotate previously exposed Telegram bot token and keep only regenerated token in non-versioned .env files.

## Env Provisioning
- Provide real values for placeholders in `/.env` and `/backend/.env`:
  - `TRAEFIK_DASHBOARD_AUTH`
  - `TELEGRAM_BOT_TOKEN`
  - `NOTIFICATION_BOT_TOKEN` (or leave empty intentionally)
  - `ADMIN_CHAT_ID`
  - `ADMIN_SUPPORT_BOT_TOKEN`
  - `RESEND_API_KEY`

## Dockploy Routing Follow-up
- Configure Dockploy Domain mapping for `api.swimvpn.pro` -> `gateway-service:3000`.
- Configure Dockploy Domain mapping for `admin.swimvpn.pro` according to desired backend route policy.
- Re-run deployment and verify health endpoint externally.

## Admin Support Bot Next
- Set `ADMIN_SUPPORT_REPORT_CHAT_ID=7161959711` in Dockploy env to receive personal support reports.
- Redeploy `main` and verify escalation flow: message -> email -> confirmation + group relay + personal report relay.

## Ops Scripts Follow-up
- Run `chmod +x scripts/ops/*.sh` on VPS after pulling latest `main`.
- Validate scripts on VPS with `bash -n scripts/ops/*.sh`.
- Wire periodic `backup-db.sh` via Dockploy schedule or cron.

## Android Follow-up
- Install and smoke-test the signed release APK on a real device:
  - fresh profile -> `TRIAL_AVAILABLE` app shell
  - profile CTA -> trial activation
  - active trial relaunch -> premium servers/config available
  - expired/no-access user -> imported config remains usable
- In Android Studio: install the rebuilt APK on the affected device and confirm startup crash is resolved after scanner migration + Retrofit base URL fix.
- Re-test on device after the Technical screen theme refactor and confirm `System`, `Light`, and `Dark` visibly differ.
- Verify the new `Proxy` recommended badge and `Tunnel` tunneling badge on the Technical screen.
- Verify boot/package-replace auto-connect on a connected device with `autoConnect=true`.
- Verify kill-switch chip transitions correctly when Android `Always-on VPN` and `Block connections without VPN` are toggled for SWIMVPN+.
- Extend Material theme token cleanup beyond the Technical screen to other light-only hardcoded screens.
- Re-run Android Studio's 16 KB APK compatibility check and confirm the old `libimage_processing_util_jni.so` warning is gone.
- If startup still crashes, collect `adb logcat` for the first fatal exception before making any broader Android changes.
- If production must support non-GMS devices later, evaluate a separate fallback scanner path as an explicit future task.

## Trial Contract Follow-up
- Re-test on a real Android device with a fresh install:
  - first open -> onboarding
  - onboarding complete -> profile completion
  - email + phone submission -> trial activation
  - trial profile -> home
- Validate one-time trial protection by retrying activation with the same device, email, and phone.
- Decide whether the frontend should show `offerCode` (`WEEK` / `MONTH` / `QUARTER`) more explicitly in premium badges or keep the current `accessType` labeling.
- Re-test the implemented backend checkout redirect flow for both `CARD_MANUAL` and `CRYPTO` on the signed release APK.

## Home Screen Follow-up
- Re-test the rebuilt Android home screen on device:
  - selected server card click -> servers page
  - profile icon -> profile page
  - floating `+` -> unified import hub
- Decide whether the home server card should eventually display real backend latency/load once the backend exposes those metrics explicitly.

## Import Flow Follow-up
- Re-test on device that both entry points lead to the same experience:
  - home `+` -> import hub
  - profile management import row -> import hub
- Confirm QR, paste, and manual input all:
  - preserve raw config locally
  - update the visible imported profile list
  - select the first newly imported server immediately
  - show the imported server on Home and Locations without requiring app restart
  - attempt backend profile sync without throwing a blocking full-screen error
- Decide later whether code activation should return as a separate approved product flow instead of a hidden generic import action.
- If backend usage metering is introduced later, replace the current honest hybrid display (`backend dataUsedBytes + local session bytes`) with a fully server-driven total.

## Android Build Follow-up
- Re-test Technical Settings on device:
  - theme `SYSTEM / LIGHT / DARK`
  - language switching `EN / FR / RU`
  - honest kill-switch shortcut -> Android VPN settings
  - auto-connect only when permission and access are already valid
  - routing selection:
    - `FULL_TUNNEL`
    - `LOCAL_PROXY`
- Validate the new native Xray local proxy path on device:
  - start / stop lifecycle
  - Xray process stays alive
  - local SOCKS port responds
  - logs are written under runtime session directory
- Complete the next VPN core native batch:
  - validate the JNI-driven `FULL_TUNNEL` data plane end-to-end on device
  - confirm `tun fd` forwarding is stable across repeated start/stop cycles
  - decide whether executable fallback remains only diagnostic or should now be removed
  - add runtime bytes telemetry instead of session-only placeholders
  - confirm upstream auto-build of `tun2socks` remains reproducible on clean environments
- Re-test the subscription screen on device:
  - backend plans render with correct name, duration, quota, and RUB price
  - selecting a plan still enables order creation
  - create order shows the honest pending-payment message instead of a fake checkout URL
- Re-test the support screen on device:
  - email row -> opens mail app with support@swimvpn.pro
  - Telegram row -> opens @SWIMVPNSUPPORTADMINBOT in Telegram or web fallback
  - renew button -> routes to subscription
- Use the restored repository wrapper as the default Android verification path:
  - `android\\gradlew.bat assembleDebug`
- Re-test the freshly rebuilt APK on device before making the next frontend alignment batch.
- Re-test the aligned profile screen on device for:
  - trial active
  - paid active
  - expired
  - profile incomplete
- [FIXED] Updated `MainViewModel.kt` to handle HTTP 500 and network timeouts with localized error messages.
- [INVESTIGATED] HTTP 500 on `bootstrap` is likely due to `backend/.env` using `localhost` for `DATABASE_URL` instead of `db` (when running in Docker), or missing `npx prisma db seed` on the server.

## Prisma / Production Follow-up
- Apply the new Prisma production rollout on the real deployment target:
  - back up production database
  - run `npm run prisma:baseline:prod` once if the schema was previously created without migrations
  - run `npm run prisma:migrate:deploy`
  - run `npm run prisma:seed`
- Preferred operational path on the server:
  - `scripts/ops/prisma-rollout.sh --baseline` for the first rollout on the old production DB
  - `scripts/ops/prisma-rollout.sh` for subsequent normal rollouts
- Execute the exact Dockploy runbook now that it is documented in `DEPLOYMENT_GUIDE.md`.
- Re-run `scripts/ops/prisma-rollout.sh --baseline` on Dockploy after the seed stability fix (`--transpile-only` + `256m` seed container).
- Redeploy after attaching `gateway-service` to `dokploy-network` and adding explicit Traefik labels for `api.swimvpn.pro` / `admin.swimvpn.pro`.
- After rollout, re-test public backend endpoints:
  - `GET /api/v1/store/plans`
  - `POST /api/v1/access/bootstrap`
  - `POST /api/v1/access/trial`
- Re-test the Android VPN engine on device after the Xray APK extraction fix:
  - confirm the old `Packaged Xray runtime is missing for ABI arm64-v8a` error is gone
  - capture the next runtime/logcat signal if connection still fails
- Re-test the Android VPN engine after enabling extracted native libraries:
  - confirm the old `Cannot run program .../no_backup/.../bin/xray` error is gone
  - capture the next runtime/logcat signal if connection still fails
- Re-test the Android VPN engine after the parser/runtime payload gRPC fix:
  - validate a real `vless://...type=grpc&serviceName=...` inventory config on device
  - confirm the runtime now carries `grpcSettings.serviceName`
  - capture the next device/logcat signal if the tunnel still fails after parser preservation
- Re-test Android import/parser behavior against real-world variants inside the supported scope:
  - `trojan://` with `raw` alias and `Reality`
  - `vmess://` URL-safe / no-padding Base64 payloads
  - `ss://` base64 + bracketed IPv6 variants
  - JSON payloads where the first outbound is not the usable tunnel
- Re-test Android import/parser behavior against real-world decorated tags:
  - `trojan://` links with emoji / brackets / mixed encoded fragment text
  - `vless://` links with non-trivial fragment labels
- If production still returns `500`, collect runtime logs from:
  - gateway-service
  - store-engine-service
  - customer-order-service

## Disk Cleanup Follow-up
- Re-check Android build behavior now that disk pressure has been reduced significantly and shell startup no longer forces `D:\Dev`.
- If Android build temp folders are recreated and become large again, clean them after each heavy build/debug batch:
  - `android/.gradle-user-home/`
  - `android/wrapper/`
- Keep `android/.gradle-user-home/` out of commits permanently; it is now intentionally ignored by Git.
- If `android/.gradle-user-home/` reappears later, treat it as disposable local cache and delete it after the build/debug batch instead of trying to version it.
- Re-test Android grouped imports on device:
  - import a payload containing multiple `trojan://` links
  - confirm the servers page shows a dedicated imported group with multiple selectable nodes
  - confirm pinning persists across app restart
  - confirm selecting an imported node starts the VPN with that node's raw config instead of the backend profile URL
- Re-test mixed catalog behavior:
  - backend access servers and imported groups should coexist on the servers page
  - the active selection should restore correctly from saved preferences
- Re-test Android latency and throughput observability on device:
  - open the servers page and confirm measured ping values appear for backend and imported nodes
  - connect through a full-tunnel imported node and verify bytes in/out start moving in the UI
  - compare the same node in `FULL_TUNNEL` vs `LOCAL_PROXY` mode to isolate tun2socks overhead from server-side slowness
- If a node is still subjectively slow after metrics appear:
  - capture the displayed ping
  - note whether bytes counters advance smoothly or stall
  - collect Xray/tun2socks session logs for that exact server
- Re-test the same imported node after MTU/DNS stabilization:
  - compare `FULL_TUNNEL` vs `LOCAL_PROXY`
  - verify whether internet pages now complete loading more reliably
  - inspect the technical screen diagnostics for Xray/tun2socks session ids and log paths
- If sessions are still too slow:
  - capture the diagnostic paths from the technical screen
  - pull the corresponding Xray/tun2socks stderr logs from app storage
  - use those logs to decide whether the next batch should target tun2socks/full-tunnel tuning or conclude the node quality is the main bottleneck

## Android Priority Reset
- Use `ANDROID_EXECUTION_STATUS.md` as the source of truth for Android roadmap state.
- Do not open a new Android performance batch before checking whether the next blocker is actually parser truth or engine/runtime completion.

### Priority 1 - Parser Coverage And Truth
- Continue hardening supported formats only:
  - `vless://`
  - `vmess://`
  - `trojan://`
  - `ss://`
  - JSON Xray/V2Ray
- Re-test real-world failing supported links and fix recognition/import gaps first.
- Ensure one imported source can consistently produce:
  - preserved raw input
  - coherent group
  - individually activable nodes
- Re-test the newly hardened parser cases specifically:
  - `vless://` / JSON VLESS with `flow=xtls-rprx-vision`
  - links using `insecure=1`, `allowInsecure=1`, `tlsInsecure=1`, or `skip-cert-verify=1`
  - links containing preserved-but-not-runtime-verified `fragment` / `noises`
  - `vmess://` with `net=grpc` where `path` must become `serviceName`
  - `vless://` / `trojan://` / JSON configs using:
    - `http`
    - `h2`
    - `httpupgrade`
    - `xhttp`
    - `splithttp`
  - `ss://` and JSON Shadowsocks payloads carrying:
    - `plugin`
    - `plugin-opts`
- If `Shadowsocks` plugin metadata is preserved but the node still does not connect, treat the next batch as engine/runtime completion rather than reopening parser work blindly.
- Re-test Happ wrapper cases explicitly:
  - `happ://add/<direct-supported-link>`
  - `happ://add/<subscription-url>`
  - `happ://crypt3/...`
  - `happ://crypt4/...`
  - `happ://crypt5/...`
  - `happ://routing/add/...`
  - `happ://routing/onadd/...`
  - `happ://routing/off`
  - plain `https://...` subscription URLs
- Next parser step after this batch:
  - re-test remote subscription fetch/import on real provider URLs
  - continue direct-link parser hardening if fetched payloads expose new supported-link variants
- Re-test remote subscription import:
  - `https://my.sub.tg/...`
  - `happ://add/https://...`
  - Base64 subscription content
  - plain multi-link subscription content
- Keep out of current parser scope until explicitly selected:
  - Happ `crypt3/crypt4/crypt5` import using an authorized provider key/format
  - Happ routing profile import
- Keep Happ encrypted links as explicit aborts until resolution is real:
  - no fake preview
  - no partial import
  - no claiming support without usable payload
- Prefer for real-world Happ-compatible imports today:
  - plain provider `https://...` subscription URLs
  - `happ://add/https://...` wrappers
  - unencrypted standard node links such as `vless://`, `vmess://`, `trojan://`, `ss://`, or JSON Xray/V2Ray
- Re-test backend/admin generation for SWIMVPN-owned encrypted imports:
  - configure `SWIMVPN_CRYPT1_KEY_BASE64`
  - call authenticated `POST /api/v1/admin/crypt-import`
  - confirm generated `swimvpn://crypt1/...` links are not decryptable inside the APK
- Re-test backend-side client resolution for SWIMVPN encrypted imports:
  - `POST /api/v1/subscription/resolve-crypt`
  - valid `userNumber + deviceId + ACTIVE` access succeeds
  - mismatched device fails
  - inactive/expired access fails
  - Android imports resolved payload into the grouped server catalog
- Add replay/expiry controls before broad release:
  - payload issue timestamp
  - optional one-time token or nonce tracking
  - key id for future `crypt2`
- Next secure-link backend step:
  - persist generated-link audit metadata if we expose this beyond admin tooling
  - plan key IDs / key rotation for `crypt2`
- Re-test modern unsupported protocol recognition in mixed subscriptions:
  - `hy2://`
  - `hysteria2://`
  - `hysteria://`
  - `tuic://`
  - `socks://`
  - `socks5://`
  - `wg://`
  - `wireguard://`
- Do not start engine work for those protocols until we explicitly choose a runtime strategy:
  - Xray-only where possible
  - sing-box core
  - separate protocol-specific core

### Priority 2 - Engine / Runtime Completion
- Once a supported node parses correctly, verify:
  - selected node -> runtime payload -> Xray launch -> traffic flow
- Keep improving diagnostics for sessions that connect but do not actually behave correctly.
- Re-test Android runtime state synchronization on additional devices:
  - home screen should show `Connected` while Xray/tun2socks/tun0 are active
  - power button should stop an active runtime even after app background/foreground
  - stale active runtime snapshots should fall back to `Disconnected`
- Continue re-testing `FULL_TUNNEL` after the upstream YAML tun2socks fix:
  - DNS resolution through the VPN
  - page-load success
  - byte counters during browsing
  - comparison against `LOCAL_PROXY`
- Re-test Android disconnect lifecycle on a real device:
  - connect in `LOCAL_PROXY`
  - disconnect
  - fully close the app
  - confirm no local Xray port remains occupied for other VPN apps
  - repeat the same flow during `CONNECTING` to confirm startup cancellation works

### Priority 3 - Performance
- Only after parser and engine truth are stable, continue performance comparison between:
  - `LOCAL_PROXY`
  - `FULL_TUNNEL`
- Use latency, byte counters, and diagnostics before making further tuning choices.
- Re-test mixed import UX:
  - supported-only payload shows a positive imported-count success message
  - supported + recognized unsupported payload imports supported servers without noisy warnings
  - recognized unsupported-only payload fails clearly with no fake success
  - duplicate-only payload still reports duplicate
- Follow-up from ADB runtime smoke check:
  - while connected, capture tun2socks stdout/stderr/exit files if they are created
  - verify whether the JNI bridge is truly moving packets or only Xray is alive
  - run a controlled page-load test while sampling `/proc/net/dev` for `tun0`
  - if `tun0` counters barely move, inspect `SwimVpnService` tun2socks launch/JNI status path next
- Re-test technical connectivity routing panel on device:
  - idle full tunnel selected shows neutral selected tunnel state
  - idle local proxy selected shows neutral selected proxy state
  - running full tunnel shows green tunnel indicator only
  - running local proxy shows green proxy indicator only
  - switching mode while disconnected persists the choice
- Local proxy ADB follow-up:
  - test at least three different imported nodes with the same proxy curl method
  - treat nodes whose host pings but TCP port times out as dead or blocked candidates
  - add a future server quality status that can downgrade nodes after TCP port timeout
- Re-test provider subscription URL import on device after robust payload selection:
  - `https://wb.routerwb.ru/jtz5386jCHkztYRZ`
  - expect direct-link payload preference over opaque provider JSON when multiple User-Agents respond
  - expect standard or URL-safe Base64 decode into grouped `vless://` imported servers
  - confirm imported VLESS Reality nodes appear in the server catalog
- Keep expanding parser regression fixtures when new provider samples arrive:
  - multiline subscriptions
  - JSON/string-wrapped direct links
  - Base64 provider payloads
  - mixed supported and recognized-unsupported protocols
- Re-test manual import button activation:
  - provider `https://wb.routerwb.ru/jtz5386jCHkztYRZ` enables Import without preview
  - random text still keeps Import disabled
  - direct supported node link still shows preview and enables Import
- Continue Android localization cleanup beyond this batch:
  - audit `ConfigImportScreen` and other remaining screens for user-facing hardcoded strings
  - keep new string keys synchronized across `values`, `values-fr`, and `values-ru`
  - re-run a focused ADB locale smoke test after each future UI text batch
- After locale flicker fix, continue localization cleanup carefully:
  - audit ConfigImportScreen and remaining screens for user-facing hardcodes
  - keep locale application outside Compose-side reactive loops
  - add a focused device smoke test for en, r, and 
u after each future language batch
- Re-test the Android technical screen on device after the entry-action guard:
  - opening Param�tres techniques should no longer jump into Android Settings
  - verify VPN session stays active while the technical screen opens
  - verify the kill-switch shortcut still opens Android VPN settings after the short entry delay
- Continue phase-status verification:
  - Auto-connect: implemented, device re-test still useful
  - Kill switch status + shortcut: implemented
  - DNS/routing hardening: partial, not yet fully closed
  - Split tunnel foundation: not closed, runtime still reports unavailable

## [2026-04-23] DNS/Routing Hardening Follow-up
- Re-test after the IPv4 DNS/routing hardening batch:
  - compare the same node in `FULL_TUNNEL` and `LOCAL_PROXY`
  - verify that slow "connected but incomplete page load" cases are reduced
  - note whether remaining failures now look more like remote node quality than local DNS/runtime fragility
- Phase status clarification:
  - Auto-connect: implemented, still worth a device re-test
  - Kill switch status + shortcut: implemented and stabilized
  - DNS/routing hardening: code batch implemented, device validation still required on current imported nodes
  - Split tunnel foundation: not closed, runtime still reports unavailable

## [2026-04-23] Product Contract Follow-up - Tunnel vs Proxy
- Keep `Tunnel` as the default user-facing mode for normal browsing/app usage.
- Keep `Proxy` available as an advanced/manual mode.
- Do not reopen proxy engine tuning before a new explicit product decision; the latest live ADB audit already confirmed the local proxy path itself is functional.

## [2026-04-23] Proxy Rollback Follow-up
- Re-test `LOCAL_PROXY` on the same node that previously felt fast.
- Compare only user-perceived page opening after the proxy-specific rollback.
- Keep `FULL_TUNNEL` unchanged unless a new regression appears there.
## [2026-04-24] Proxy Stabilization Closed
- Treat the current proxy runtime as frozen/stable.
- Do not retune `LOCAL_PROXY` again unless a reproducible regression returns.
- Move implementation focus to the next product task.
## [2026-04-24] Payment MVP Follow-up
- Configure real env values before rollout:
  - `CRYPTO_PAY_API_TOKEN`
  - `PAYMENT_BOT_USERNAME`
  - `MANUAL_CARD_NUMBER`
  - optional `PAYMENT_REVIEW_CHAT_ID`
- Rebuild/redeploy backend services after env setup.
- Test Android payment selection end-to-end:
  - card-manual deep link to bot
  - screenshot proof submission
  - admin approve/reject callbacks
  - crypto invoice creation + webhook callback
- Add a future admin UI for payment review only if Telegram review becomes insufficient.

## [2026-04-24] Subscription Analytics Follow-up
- Keep the current UI wording honest until a real backend usage source exists.
- If subscription-wide consumption is needed later:
  - add a server-side usage source of truth
  - populate `dataUsedBytes` with real measured values
  - then reintroduce a true global usage progress view

## [2026-04-24] Backend Quota + Device Enforcement Follow-up
- Add a real backend usage source of truth for sold subscriptions and imported supplier links.
- Populate `dataUsedBytes` with measured subscription consumption only.
- Enforce plan quota exhaustion as a strict access rule.
- Model supplier capacity explicitly:
  - large supplier links can expose `1000 GB`
  - each such source can be split across at most `5` different users in SWIMVPN
- Track those `5` user allocations server-side so Telegram or the Android app never becomes the source of truth.

## [2026-04-24] Quota Enforcement Follow-up
- Apply the manual migration `backend/prisma/migrations/20260424093000_shared_quota_usage_foundation/migration.sql` on the target database if `prisma migrate dev` remains blocked by the local schema-engine issue.
- Add the producer for `record_assignment_usage` so measured usage actually flows into `dataUsedBytes` during runtime.
- Decide the final operational trigger for usage updates:
  - app runtime reports
  - backend worker/provider sync
  - or admin/manual reconciliation
- Add admin visibility for:
  - source remaining quota
  - distinct users already consuming the source
  - sources close to the `5 users` cap or near quota exhaustion

## [2026-04-24] Usage Producer Follow-up
- Start the local PostgreSQL instance or target database, then apply and resolve migration `20260424093000_shared_quota_usage_foundation`.
- Add a second usage-report trigger beyond manual stop if product validation shows it is needed:
  - app resume/foreground reconciliation
  - graceful service shutdown callback
  - or trusted backend/provider reconciliation
- Validate end-to-end that stopping VPN updates `dataUsedBytes` and reduces the subscription quota progress bar in the Android profile screen.

## [2026-04-24] Local Usage Flow Validation
- Seed or verify a local fulfilled order + assignment, then test that `POST /api/v1/subscription/usage` updates `dataUsedBytes` as expected.
- Run the Android app against the local/backend target, generate traffic, stop VPN manually, and verify the profile quota progress decreases.
- If manual-stop reporting feels too sparse in product tests, add the next safe trigger without introducing a background spam loop.

## [2026-04-24] Payment Flow Follow-up
- Re-test the VPS checkout flow and inspect gateway/customer-service logs if `HTTP 500` still appears after redeploy.
- Localize the new payment-confirmation modal strings cleanly for FR/RU after the current ASCII-safe unblock.
- Decide the business shape of a future paid weekly offer instead of reusing the internal trial plan.

## [2026-04-24] VPS Checkout Debug Follow-up
- Redeploy the updated backend services on the VPS, then re-test `/api/v1/orders/checkout` because the public endpoint still returns `Internal server error` in the current deployment.
- Capture gateway-service and customer-order-service logs on the VPS during a payment attempt if the error persists after redeploy.
- Reinstall/update the Android app build on the phone after backend redeploy to verify that the subscription page and profile no longer show stale trial/week labels.
## [2026-04-25] Weekly Offer + Checkout Redeploy Validation
- Redeploy the backend services on the VPS with the 2026-04-25 payment/store batch.
- Re-test `GET /api/v1/store/plans` on the live API and confirm it returns all three paid offers: `WEEK`, `MONTH`, `QUARTER`.
- Re-test `POST /api/v1/orders/checkout` on the live API and confirm it no longer collapses known checkout failures into `Internal server error`.
- If checkout still fails after redeploy, capture gateway-service and customer-order-service logs for the same payment attempt before changing more code.
- After backend redeploy, update/reinstall the Android build only if the live app still shows stale trial/week data.
## [2026-04-25] Parser Follow-up
- Extend metadata detection for additional provider-specific subscription banners when real samples are collected.
- Add non-importable profile surfacing if we later want to preview partially supported entries without importing them.
- Consider moving preview generation to the normalized subscription parser once the runtime preview contract is cleaned up.

## [2026-04-25] Active Config Follow-up
- Expand `Active Config` presentation for richer parser metadata only when the source stays clearly labeled:
  - server/location hints
  - provider banner details
  - parser warnings when useful for diagnosis
- Add focused scenario coverage for profile truth separation:
  - trial profile with imported active config
  - paid profile with SWIMVPN-managed active config
  - imported config carrying usage-only metadata
  - imported config carrying expiration-only metadata
- Re-run the targeted Android parser/config tests after the local Gradle/JVM memory issue is cleared so Task 5 can be closed without verification concerns.

## [2026-04-25] Supplier Capacity Backend Follow-up
- Redeploy the backend services with the new supplier-slot logic before trusting live checkout/fulfillment behavior.
- Add an admin UI surface for the new backend controls:
  - inventory overview
  - config health updates
  - assignment revocation
  - assignment move
  - retry fulfillment for pending paid orders
- Validate live scenarios after redeploy:
  - payment success + allocatable config => fulfilled
  - payment success + no capacity => pending fulfillment
  - revoked assignment stops appearing active
  - supplier-expired config marks linked assignments expired
- Keep the Android truth visible in product follow-up:
  - old imported configs still need re-import before the profile card can show the persisted parser analytics metadata.

## [2026-04-25] Supplier Bundle Parsing Follow-up
- Import a real `wb.routerwb.ru` supplier resource through the admin/backend path and verify the stored inventory row contains:
  - extracted subscription URL
  - source used bytes
  - source total bytes
  - supplier expiry
  - provider name
  - connected-device-derived `used_resale_slots`
- Surface the new supplier metadata through the admin UI so resources no longer look empty.
- Re-check the Android/profile flow after a real supplier-backed assignment exists, especially for traffic remaining and expiry display.

## [2026-04-25] Paid Profile Truth Follow-up
- Re-test the paid-profile path on device after a real supplier-backed order is assigned and fulfilled.
- Verify the user now sees:
  - `Basic / Premium / Platinum` instead of `WEEK / MONTH / QUARTER`
  - exact expiration date in `SWIMVPN Access`
  - real used traffic percentage derived from backend supplier analytics
- Keep the Android import-config debt visible separately:
  - previously imported configs still need re-import before their persisted parser analytics appear on the profile card.

## [2026-04-25] Pending Fulfillment UI Follow-up
- Re-test on device a paid order that lands in `PENDING_FULFILLMENT` and confirm both home and profile show the same state.
- Verify a supplier quota with decimals, such as `178.7 GB`, renders correctly in the paid access analytics card.

## [2026-04-25] Payment Runtime Follow-up
- Redeploy the backend services so the payment bot username resolution and Crypto Pay URL normalization are active on the VPS.
- Re-test both live paths after redeploy:
  - card checkout redirect lands in the correct Telegram bot chat
  - crypto checkout opens a fresh Crypto Bot invoice
- If live checkout still fails after redeploy, capture the exact HTTP response body from `/api/v1/orders/checkout` before changing code again.

## [2026-04-25] Branding Retest Follow-up
- Install or build the next Android test artifact with the new `swimvpn+` branding assets.
- Visually verify:
  - launcher icon
  - splash logo
  - home screen logo
  - notification small icon fallback behavior

## [2026-04-25] Trial/Profile Analytics Follow-up
- Re-test on device that a pure trial now shows:
  - `Unlimited` in `SWIMVPN Access`
  - no quota progress bar
  - trial expiry only
- Re-test on device that a paid subscription still dominates analytics when it exists after a trial.
- Verify one fresh imported subscription shows its parsed traffic and expiry in `Active Config` without relying on `SWIMVPN Access`.
- If imported analytics still appear empty after a fresh import, inspect the saved `SwimVpnProfile` values for:
  - `subscriptionTrafficUsedBytes`
  - `subscriptionTrafficTotalBytes`
  - `subscriptionExpiresAt`

## [2026-04-25] Trial Consistency Retest
- Verify on device that profile-incomplete/no-access states do not expose a misleading device allowance.
- Verify the trial card now reads as `Trial Access` and not as a paid plan quota section.
- Keep one focused follow-up only if needed: remove provider-name prominence from `Active Config` if it still feels too technical during user tests.

## [2026-04-25] Payment And Imported Config Retest
- Redeploy the backend services after this batch so `customer-order-service` receives the new Docker environment wiring.
- Re-test card checkout and confirm the redirect is built from the actual payment bot token/runtime.
- Re-test crypto checkout and confirm the runtime no longer fails fast on missing config when `CRYPTO_PAY_API_KEY` or `CRYPTO_PAY_API_TOKEN` is present.
- Re-import a real Russian supplier bundle and verify `Active Config` now shows:
  - quota progress bar
  - used/total traffic
  - expiry date

## [2026-04-25] VPS Payment Redeploy
- Recreate the VPS services using the updated root `docker-compose.yml`, not only a container restart.
- Re-test card checkout after redeploy and confirm `customer-order-service` no longer reports missing bot configuration.
- Re-test crypto checkout after redeploy and confirm the service sees the configured crypto token/key.

## [2026-04-25] Landing Domain Rollout
- Ensure the Spaceship `A` record for `app.swimvpn.pro` points to the VPS IP.
- Redeploy the root compose so Dokploy creates the new `landing-service`.
- Verify `https://app.swimvpn.pro` resolves with a valid TLS certificate and loads the built landing page.

## [2026-04-28] Trial/Access QA Follow-up
- Re-test on device: fresh profile incomplete -> onboarding, not expired/paywall.
- Re-test on device: `TRIAL_AVAILABLE` enters app shell/freemium and can activate trial without a dead end.
- Re-test on device: `ACTIVE_TRIAL` receives premium servers/configs and can connect.
- Re-test on device: expired trial/subscription stays in app shell, imported configs remain usable, managed premium server actions route to subscription/paywall.
- Re-test on device: usage reporting still refreshes profile after disconnect with matching device ID.
- Re-test security manually: calls to `/api/v1/servers` without `x-device-id` or with a wrong device ID return no premium servers.
- Investigate local `npx prisma migrate status` `Schema engine error` before deployment readiness can be marked YES.
- Add or wire a backend `npm test` script so backend test readiness is not permanently blocked by package configuration.

## [2026-04-28] Backend Deployment Verification Follow-up
- For local migration status, either start the local Postgres container stack or override `DATABASE_URL` with a URL-encoded localhost connection string before running `cd backend && npm run prisma:migrate:status`.
- On VPS/Dokploy, verify the one-shot migration job after deploy with:
  - `docker compose ps prisma-migrate prisma-seed`
  - `docker compose logs prisma-migrate --tail 200`
  - `docker compose logs prisma-seed --tail 200`
- Do not use plain `npm test` for backend readiness unless a real test runner script is added later; use `npm run test:policy` or `npm run verify:deploy`.

## [2026-04-28] Dokploy DATABASE_URL Alignment
- Add or update the Dokploy/root environment variable `DATABASE_URL` with the URL-encoded Postgres connection string that uses host `db` and schema `public`.
- Keep `POSTGRES_USER`, `POSTGRES_PASSWORD`, and `POSTGRES_DB` unchanged unless you intentionally rotate the database password.
- After redeploy, verify:
  - `docker compose logs prisma-migrate --tail 200`
  - `docker compose logs prisma-seed --tail 200`
  - `curl -f https://api.swimvpn.pro/api/v1/health`

## [2026-04-29] Release Trial/Freemium Retest
- Sign the newly built release APK with the production key before installing over the user's signed release.
- Re-test on signed release APK: profile/trial screen shows "Continue without trial" and enters the app shell without premium servers.
- After Dokploy redeploy, verify `POST /api/v1/access/trial/activate` without `deviceId` returns `400`, already-used trial returns `409`, and unauthorized device returns `403`.
- Confirm expired/trial-used users can still open the app shell, use imported configs, and reach subscription offers.

## [2026-04-29] Release Trial Denial QA
- Redeploy backend so `POST /api/v1/access/profile/complete` is available in production.
- Produce a signed release APK from the current source; `assembleRelease` currently creates an unsigned local APK.
- Test signed release: enter already-used email/phone -> trial denial -> Continue without trial -> profile shows contact info -> subscription checkout can be opened.
- Test technical settings in signed release: opening the page, toggling routing/autoconnect/language/theme, kill-switch row, and back/save should not reset the app.
- Consider adding explicit `@SerializedName` annotations to access DTOs after this hotfix, even though current ProGuard keeps protect them.
- Bump `versionCode` before public release so APK provenance is not ambiguous.

## [2026-04-29] Release Import/Payment QA
- Sign and install the rebuilt release APK, then import `https://wb.routerwb.ru/jtz5386jCHkztYRZ` again.
- Confirm Access Configurations shows the 11 imported VLESS Reality profiles after import.
- Confirm Profile -> Active Config shows source `Imported`, traffic used/total from `subscription-userinfo`, and expiry from the provider header.
- Confirm Locations shows imported servers for trial-used/freemium users while backend premium servers remain protected.
- Test manual card flow with live Telegram bot: `/start card_<orderRef>` -> screenshot -> confirmation prompt -> contact text -> admin review packet -> approve/reject.
- Verify Dokploy env has `MANUAL_CARD_NUMBER`; without it the bot will correctly refuse to start card payment flow.

## Manual Card Payment Retest
- Redeploy backend notification-bot-service.
- Re-test Telegram card flow end-to-end:
  - open payment bot from app order link
  - send proof screenshot
  - send final email / phone / sender phone in one message
  - verify admin review chat receives `SWIMVPN+ CARD PAYMENT FINAL REVIEW` with approve/reject buttons
  - approve and confirm config delivery email goes to the confirmed final email
- If final confirmation still does not reach admin chat, inspect Dokploy logs for `Failed to forward manual payment contact confirmation` and verify `PAYMENT_REVIEW_CHAT_ID` points to a chat where the bot is a member.

## Manual Payment Approval Retest
- In Dokploy env, verify masked presence of:
  - `NOTIFICATION_BOT_TOKEN`
  - `ADMIN_CHAT_ID`
  - `PAYMENT_REVIEW_CHAT_ID`
  - `PAYMENT_BOT_USERNAME`
  - `MANUAL_CARD_NUMBER`
  - `RESEND_API_KEY`
  - `MAILER_FROM_EMAIL`
- Optional hardening: set `ADMIN_USER_IDS` to the comma-separated Telegram user ids allowed to click approve/reject.
- After redeploy, click approve/reject from the configured review group and verify:
  - approve marks order paid/fulfilled or pending fulfillment
  - delivery email record is created
  - Resend sends email or logs `EMAIL_FAILED`
  - reject marks pending order failed and sends rejection email

## Resale Cap Alignment Completed
- [DONE] `DEFAULT_RESALE_SLOT_CAP` is now `2`.
- [DONE] Basic/Premium/Platinum each consume one resale slot per paid order.
- [DONE] Subscription UI displays up to 2 devices for each plan.
- [DONE] Prisma migration added to normalize existing plan/assignment slot counts and inventory resale caps.

## Admin Operations Bot Secure Import MVP Completed
- [DONE] `admin-control-service` bot now requires explicit admin authorization through `ADMIN_USER_IDS` or a personal `ADMIN_CHAT_ID`.
- [DONE] `/stock` shows allocatable inventory by Basic/Premium/Platinum category.
- [DONE] `/add basic|premium|platinum <config-or-url>` imports supplier configs through `inventory-delivery-service` with `maxResaleSlots = 2` and supplier device metadata defaulting to 5.
- [DONE] Root `docker-compose.yml` passes `ADMIN_USER_IDS` into `admin-control-service`.
- Next: implement the richer admin wizard/accounting ledger only after live Telegram QA proves the secure import MVP works.

## [2026-04-30] Dokploy Prisma Migration Recovery
- If Dokploy already failed on migration `20260430093000_resale_cap_two_orders`, run once after pulling the fixed code/image:
  - `docker compose run --rm prisma-migrate npx prisma migrate resolve --rolled-back 20260430093000_resale_cap_two_orders`
- Then redeploy or run:
  - `docker compose run --rm prisma-migrate npm run prisma:migrate:deploy`
- Keep `DATABASE_URL` URL-encoded in Dokploy env. Do not rely on composing it from raw `POSTGRES_PASSWORD` when the password contains `@`, `#`, `/`, `:` or similar URL-sensitive characters.

## Admin Operations Bot Live QA
- After redeploy, test in Telegram with an authorized `ADMIN_USER_IDS` account:
  - `/stock`
  - `/pending`
  - `/retry all` when an order is pending fulfillment
  - `/expire <inventoryId>` on a test supplier config
  - `/disable <inventoryId>` on a test supplier config
  - `/quota_reached <inventoryId>` on a test supplier config
  - `/orders_today`
  - `/revenue_today`
- Verify disabled/expired supplier configs no longer expose active assignments in the app profile/access payload.
- Later: implement schema-backed accounting entries for expenses, crypto asset tracking, exchange rates, and profit reports.

## Accounting Ledger Live QA
- After redeploy, verify the new migration `20260430103000_accounting_entries` applies in Dokploy.
- Test Telegram admin commands:
  - `/add_expense 1500 RUB supplier renewal`
  - `/profit_month`
- Complete one paid fulfillment and confirm a `REVENUE` accounting entry is created for the order.
- Later: add crypto exchange-rate capture and richer reports by currency/asset.

## Admin Bot Guided Import Live QA
- After redeploy, test `/add_wizard` from an authorized Telegram admin account.
- Verify the wizard flow:
  - choose `basic`, `premium`, or `platinum`
  - paste one supplier config or subscription URL
  - verify the confirmation preview does not expose the full raw config
  - reply `confirm`
  - verify import result shows protocol, quota, usage, expiry, health, and `0/2` or parsed slots
- Verify direct `/add basic|premium|platinum <config>` still works for emergency imports.
- Verify `/stock` shows the imported config under the selected bucket.

## Supplier Healthcheck Scheduler Live QA
- After redeploy, inspect `inventory-delivery-service` logs and verify:
  - `Inventory healthcheck scheduler enabled every 1800000ms`
  - scheduled checks complete without crashing the service
- Optional env controls:
  - `INVENTORY_HEALTHCHECK_INTERVAL_MS=0` disables scheduler
  - `INVENTORY_HEALTHCHECK_INTERVAL_MS=600000` runs every 10 minutes
- Keep `/healthcheck` available as a manual admin command for emergency checks.

## Admin Bot Slash Menu Retest
- After redeploying `admin-control-service`, open the admin bot chat and type `/`.
- Verify Telegram shows commands including:
  - `/stock`
  - `/add_wizard`
  - `/pending`
  - `/retry`
  - `/add_expense`
  - `/profit_month`
  - `/healthcheck`
- If commands do not appear immediately, close/reopen Telegram or send `/help`; Telegram clients can cache command menus briefly.
- Check logs for `Registered 19 Telegram admin bot commands`.

## Telegram Admin Identity Live QA
- After redeploy, send `/whoami` to each Telegram bot chat you test.
- Copy the returned `User id` into Dokploy env as `ADMIN_USER_IDS=<telegramUserId>` or comma-separated for multiple admins.
- Keep `ADMIN_CHAT_ID` for the admin/review group; do not rely on a `-100...` group id to authorize private admin commands.
- Verify logs after restart:
  - `Registered 19 Telegram admin bot commands`
  - `Registered 4 Telegram support bot commands`
  - `Registered 5 Telegram notification bot commands`
- Retest private admin commands:
  - Admin Operations Bot: `/stock`, `/add_wizard`, `/pending`
  - Notification/Payment Bot: `/help`, `/status <orderRef>`, `/resend <orderRef>`
- Retest support bot menu with `/`, `/help`, `/language`, `/whoami`.

## Telegram Admin Identity Runtime Retest
- Redeploy backend bot services after the auth diagnostic patch.
- Send `/whoami` to the Admin Operations Bot.
- Expected for Telegram user `7161959711`:
  - `Authorized: yes`
  - `Configured admin ids: 1` or more
  - `Current user in ADMIN_USER_IDS: yes`
- If `Configured admin ids: 0`, Dokploy did not inject `ADMIN_USER_IDS` into the running bot service.
- If `Configured admin ids` is non-zero but `Current user in ADMIN_USER_IDS: no`, re-check the exact value and separators in Dokploy env.
- Then retest `/start`, `/stock`, and `/add_wizard`.

## Manual Payment Bot Live QA
- Ensure Dokploy env points manual payment to the dedicated payment bot:
  - `PAYMENT_BOT_TOKEN=<payment bot token>`
  - `PAYMENT_BOT_USERNAME=<payment bot username without @>`
  - `PAYMENT_REVIEW_CHAT_ID=<admin review group id>`
  - `ADMIN_USER_IDS=7161959711`
- After redeploy, start card payment from Android and verify the checkout URL opens the same bot that accepts screenshots.
- Send proof screenshot from the Telegram chat opened by the app.
- Expected bot response: `Payment proof received.` followed by the email/phone/sender-phone confirmation prompt.
- Reply with email, phone, and sender phone.
- Verify `PAYMENT_REVIEW_CHAT_ID` receives the final review packet with approve/reject buttons.
- Approve a test order and verify fulfillment/email delivery; reject another and verify rejection email/status.

## Notification Bot Token Ownership Live QA
- After redeploy, confirm `notification-bot-service` logs show the payment/notification bot command menu registration.
- Trigger a delivery/admin notification with inline buttons.
- Click `resend`, `copy`, or `mark delivered` and confirm the callback is handled by the same bot.
- Trigger manual card proof review and verify `approve`/`reject` buttons answer instead of silently doing nothing.
- Keep `TELEGRAM_BOT_TOKEN` reserved for admin operations unless used only as legacy one-way notification fallback.

## Android VPN Stability Release QA
- Build a signed release APK on a machine/session with enough memory for R8; local release build currently fails from Gradle/JVM OOM during `minifyReleaseWithR8`, while debug build passes.
- On signed release, verify Technical Settings Auto-Connect:
  - enabling with an active imported config stays enabled
  - enabling without a runnable config shows a clear toast and remains off
  - no process restart or crash buffer entry appears
- Verify VPN lifecycle on device with logcat:
  - connect full tunnel
  - keep app open for 5 minutes
  - background app
  - lock/unlock phone
  - remove app from recents and confirm foreground VPN behavior is intentional
  - switch Wi-Fi/mobile if possible
  - confirm any disconnect has a structured `SwimVpnService` reason
- Validate that full tunnel only reports Connected when tun2socks data plane is active.
- Later hardening: add network callback/backoff reconnect policy after live logs prove how the provider/runtime behaves on network handoff.

## Product Claims Cleanup Before Public Launch
- Replace or qualify public claims that are not implemented/proven yet:
  - total anonymity
  - military security/encryption
  - advanced obfuscation
  - strict zero-logs
  - fixed encrypted-node counts
- Document implemented reality separately from planned non-LLM AI/smart routing features.

## Backend Premium Boundary Live QA
- Redeploy backend services after the 2026-05-01 backend risk closure patch.
- Confirm unmanaged activation codes return a clear rejection and do not create paid access.
- Confirm Stripe/YooKassa webhook paths do not fulfill until signature verification is implemented.
- Confirm a paid customer assigned to a supplier config still receives access when the config is `FULL` because resale capacity is exhausted.
- Confirm `EXPIRED` or `DISABLED` supplier inventory hides premium config/server access.
- Confirm `/servers` returns only the customer's assigned backend config metadata, not generic premium server rows.
- Confirm manual payment approval reaches `customer-order-service` through `CUSTOMER_SERVICE_HOST`/`CUSTOMER_SERVICE_PORT`.

## Non-LLM Smart Routing Plan
- Treat "AI" as deterministic network intelligence, not an LLM/chatbot.
- Implement later only after stability tests: latency scoring, failure scoring, provider/config health scoring, safe reconnect backoff, and adaptive config selection.
- Do not publicly claim adaptive AI, advanced obfuscation, or smart routing as implemented until runtime evidence and UI/backend support exist.

## Adaptive VPN Phase 1 Live QA
- Build/install a signed release with the adaptive patch.
- Enable Auto-Connect only after selecting a valid imported or premium config.
- Verify manual disconnect does not reconnect.
- Force or observe one runtime failure and confirm logs:
  - `event=runtime_failed`
  - `event=decision_agent_action_taken`
  - `event=reconnect_started`
  - `event=reconnect_success` or a bounded give-up message
- Confirm repeated failure avoids the unstable server for the cooldown and switches to another visible/authorized server.
- Confirm free/expired users never fallback to backend premium servers; imported configs remain candidates.
- Do not implement Standard/Stealth auto-switch until configs are classified and live metrics prove the reconnect layer is stable.

## Android Local Build Environment Follow-Up
- `prepareTun2SocksRuntimeAssets` is fixed and verified.
- If full `assembleDebug` or `assembleRelease` still crashes locally, reduce active apps/processes or run with reduced Gradle memory:
  - `$env:GRADLE_OPTS='-Dorg.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8'`
  - `cd android; .\gradlew.bat :app:assembleDebug --no-daemon --max-workers=1 --console=plain`
- If release R8 still OOMs, build signed release on the VPS/build machine or a local session with more RAM/pagefile.

## Manual Card Bot Incident Follow-Up
- Redeploy `notification-bot-service` after the proof-forwarding guard.
- For the affected customer, ask them to send one message in the payment bot with:
  - final email
  - final phone
  - sender payment phone
- If the proof event exists from the earlier screenshot, the bot can recover the pending confirmation from PostgreSQL while the order is still `PENDING`.
- Check Dokploy logs for `Failed to forward manual card photo proof` or `review chat is not configured` to confirm whether `PAYMENT_REVIEW_CHAT_ID`, bot group membership, or bot permissions caused the silent failure.

## Manual Card Admin Rescue QA
- After redeploy, test in the payment bot as admin:
  - `/pending_cards`
  - `/review_card <orderRef>`
  - `/approve_card <orderRef>`
  - `/reject_card <orderRef>` on a disposable pending order only.
- Confirm `/approve_card` refuses orders with no stored `CARD_PAYMENT_PROOF_SUBMITTED` event.
- Confirm approved manual card orders become fulfilled or pending fulfillment through the normal backend flow and delivery email is sent when inventory is available.

## Manual Card Bot Auto-Fallback Live QA
- Redeploy `notification-bot-service`.
- Confirm the payment bot can DM the admin user ids in `ADMIN_USER_IDS`; each admin must have started the payment bot at least once.
- Test with a pending card order:
  - send screenshot as customer
  - confirm review group receives media + approve/reject buttons
  - temporarily break/disable review chat if possible and confirm admin receives direct text fallback with approve/reject buttons
  - approve and confirm fulfillment/email delivery
- For the current incident, use `/pending_cards`, `/review_card <orderRef>`, then `/approve_card <orderRef>` after bank confirmation.

## Manual Card Reminder Loop Live QA
- Redeploy `notification-bot-service`.
- Confirm logs show `Manual card reminder loop started`.
- Leave a test manual card order in `PENDING` with stored proof for longer than `MANUAL_CARD_REMINDER_MIN_AGE_MS`.
- Confirm admin receives reminder with approve/reject actions.
- Confirm repeated reminders do not spam more often than `MANUAL_CARD_REMINDER_INTERVAL_MS`.
- Confirm `/approve_card <orderRef>` or the inline approve button triggers fulfillment and email delivery.

## Manual Card Confirmation Parser Live QA
- Redeploy `notification-bot-service` after the parser hardening.
- Test that customer confirmation messages parse correctly when written on one line, with French/English/Russian labels, with spaced phone numbers, and with punctuation separators.
- Confirm admin review packets include final email, final phone, and sender payment phone before approving.
- Confirm parser fallback uses the customer phone as sender phone only when no separate sender phone is provided.

## Manual Card Review Output Trace Live QA
- Redeploy `notification-bot-service` after adding `/trace_card`.
- For any paid-but-not-delivered card order, run `/trace_card <orderRef>` first.
- Use `/review_card <orderRef>` to resend stored proof plus latest contact confirmation summary.
- If the trace shows `CARD_PAYMENT_CONTACT_REVIEW_NOTIFICATION_FAILED`, verify `PAYMENT_REVIEW_CHAT_ID`, bot group membership, and bot permission to send messages/media in the review group.
- If the trace shows no `CARD_PAYMENT_CONTACT_CONFIRMED`, ask the customer to reply once with email, phone, and sender payment phone, then run `/trace_card` again.

## Manual Card + Premium Runtime Live QA
- Redeploy `notification-bot-service` so contact confirmation sends a complete proof + contact review packet.
- Verify `PAYMENT_REVIEW_CHAT_ID` points to the dedicated review group and the payment bot can send media/messages there.
- Build and install a new signed release APK after the Android subscription-runtime resolver change.
- Test a paid Basic/Premium/Platinum backend resource whose raw inventory config is an `https://` supplier subscription URL.
- Confirm tapping connect fetches/parses the subscription and no longer fails with `Unsupported configuration format`.

## Customer Cancellation / Resale Capacity QA
- Redeploy backend services after adding `subscription/cancel-current`.
- Build and install a new signed release APK after Android profile cancellation UI changes.
- Test active paid backend config cancellation:
  - Profile shows �R�silier l�acc�s�.
  - Dialog confirms the action.
  - Backend returns refreshed profile without premium runtime config.
  - If a backend premium config is selected and VPN is running, VPN stops and auto-connect is disabled.
  - Imported configs remain visible and usable.
  - Inventory overview shows the revoked assignment and recomputed `usedResaleSlots`, allowing the supplier link to be resold if capacity remains.
- Test cancellation with wrong device id returns a clear denial and does not revoke the assignment.

## Post-cancellation / manual card fulfillment QA
- Redeploy `customer-order-service`, `inventory-delivery-service`, `gateway-service`, and `notification-bot-service` after the standard UX and fulfillment trace changes.
- Build/install a new signed release APK after the Android profile/home wording and stale VPN-error reset changes.
- For the affected order, run `/trace_card ORD-1777674402099-772` after redeploy, then retry `/approve_card ORD-1777674402099-772` if the order is still not fulfilled.
- If approval still fails, use the exact `FULFILLMENT_FAILED` error now returned by the bot to decide whether stock, inventory health, delivery/email, or order state needs admin action.
- Verify a cancelled paid user sees `MODE STANDARD`, no red expired badge, no old provider remaining-days value, and can immediately open subscription offers or use/import configs.
- Verify the cancelled assignment is `REVOKED` and the supplier resource can be resold within the configured two-order cap.

## Pending fulfillment cancellation QA
- Redeploy backend services after the `CUSTOMER_PENDING_ORDER_CANCELLED` change.
- Build/install a new signed APK after the pending badge, stale VPN error, and standard card copy changes.
- Test paid-but-not-fulfilled order cancellation: app must return to `MODE STANDARD`, no red pending badge, no stale `Connection failed`, and no long freemium explanatory message.
- Verify order audit contains `CUSTOMER_PENDING_ORDER_CANCELLED` and no inventory slot was consumed.

## Manual card approval exact-error QA
- Redeploy `inventory-delivery-service` and `customer-order-service` before retesting `/approve_card`.
- Retry the failing order. If fulfillment still fails, capture the exact returned error and use `/trace_card <orderRef>` to confirm the audit payload.
- Do not mark a customer delivered unless fulfillment returns success or inventory assignment is visible in admin inventory/order status.

## Manual card pending fulfillment QA

- After redeploy, retry `/approve_card ORD-...` for the paid customer.
- Expected if delivery still cannot complete: bot says payment approved and fulfillment pending with the exact reason.
- Resolve the shown reason, then rerun `/approve_card ORD-...` or use pending fulfillment tooling to release the config.

## Production migration required for resale-slot fulfillment

- Run Prisma migrate deploy after deploying the migration `20260502093000_drop_stale_unique_assignment_indexes`.
- Retry `/approve_card ORD-1777677975691-606` after migration succeeds.
- Expected result: fulfillment can attach the second assignment to the same inventory item if capacity remains.

## Prisma migrate failed migration recovery

- If Dokploy recorded `20260502093000_drop_stale_unique_assignment_indexes` as failed, run `prisma migrate resolve --rolled-back 20260502093000_drop_stale_unique_assignment_indexes` in the `prisma-migrate` service, then rerun migrate deploy.

## Safe migration deploy wrapper QA

- After redeploy, confirm `prisma-migrate` logs show the known failed migration being rolled back before deploy.
- Confirm unrelated migration failures are still blocking and are not auto-resolved.

## Prisma migrate exit 137 QA

- After redeploy, confirm `prisma-migrate` no longer exits 137.
- Expected logs: status check, optional known migration rollback recovery, then `prisma migrate deploy` success.

## Premium config activation QA

- After a paid Basic approval, verify the server selector shows VLESS nodes from the assigned subscription, not `HTTPS - wb.routerwb.ru`.
- Verify the profile/access quota shows the plan quota (Basic 50 GB, Premium 150 GB, Platinum 500 GB), not the supplier 1000 GB metadata.
- Verify supplier website/provider host is not shown on the managed active config card.

## Premium subscription runtime node QA

- After backend redeploy and new signed APK install, approve a Basic order backed by `https://wb.routerwb.ru/...` inventory.
- Confirm `/servers` does not expose `HTTPS - wb.routerwb.ru` as a server row.
- Confirm Android expands the entitled subscription into selectable VLESS nodes and connects using one of those raw node configs.
- Confirm the managed profile card shows the plan quota sold to the customer, not the supplier global 1000 GB quota.
- Confirm supplier website/provider host is not shown on the managed active config card.

## Premium subscription runtime node QA result

- Live QA passed on a signed Android build: a purchased Basic access expanded the supplier subscription into a concrete `vless` node and connected successfully.
- Keep one backend/container policy-test rerun before final release notes, because local backend dependencies are currently unavailable.

## Provider time + sold quota enforcement QA

- Run backend policy tests in a container with dependencies: `cd backend && npm run test:policy`.
- Live QA with a small quota test plan/config:
  - Connect to a Premium backend config.
  - Confirm usage reporting is invisible in the app.
  - Force measured usage over the plan quota or use a tiny quota fixture.
  - Confirm backend marks assignment `PLAN_QUOTA_EXHAUSTED` / no longer premium.
  - Confirm Android stops VPN automatically, disables auto-connect, refreshes profile, and returns to `MODE STANDARD`.
  - Confirm imported configs remain visible/usable.
  - Confirm the supplier link capacity is recalculated and can be resold if under cap.

## Multi-agent code review follow-up

- Fix quota reporting integrity:
  - make `OrderAssignment.measured_used_bytes` monotonic with `max(existing, reported)`;
  - reject or audit lower client reports unless an admin reset path exists;
  - keep `reportUsage` response shape compatible with Android.
- Fix backend entitlement/source truth:
  - include expired assignments in profile resolution when the correct state is `EXPIRED_TRIAL` or `EXPIRED_SUBSCRIPTION`;
  - keep revoked/cancelled assignments as `FREEMIUM` / standard mode;
  - align `customer-order-service` and `store-engine-service` expiry checks.
- Fix inventory safety:
  - include `source_quota_bytes/source_used_bytes` in allocation and health status decisions;
  - prevent source-quota exhaustion from being cleared by health recalculation;
  - prevent `moveAssignment` from reactivating revoked/expired/failed assignments.
- Move managed subscription URL expansion behind backend authorization:
  - Android should not be the authoritative resolver for SWIMVPN-managed supplier subscription URLs;
  - backend should expose only entitlement-checked runtime nodes/config payloads.
- Harden manual card/payment bots:
  - require explicit `ADMIN_USER_IDS` for approve/reject/copy/resend actions;
  - add a recoverable delivery state for fulfilled-but-email-failed orders;
  - filter active assignments before resend/copy delivery actions;
  - avoid storing or broadcasting raw VPN config in broad Telegram group contexts.
- Harden deploy/bootstrap:
  - document or template root `DATABASE_URL`;
  - split production reference seed from demo/starter inventory seed;
  - provide an explicit fresh-DB admin bootstrap path without default credentials.

## Deploy/bootstrap seed QA

- After dependencies are available, run `cd backend && npm run verify:deploy` to exercise Prisma validate/generate before lint/build/policy tests.
- On fresh production bootstrap, keep `SEED_DEMO_DATA=false` and create real inventory through the authenticated admin import flow.
- Create the first `Admin` row through a controlled ops step with a bcrypt `password_hash`; do not add default credentials to seed files.

## Multi-agent review fix batch QA

- Run backend policy tests in a dependency-complete environment: `cd backend && npm run test:policy`.
- Run backend deploy verification in the container or after dependency restore: `cd backend && npm run verify:deploy`.
- Run Android targeted test when Windows memory/paging is stable: `cd android && .\gradlew.bat :app:testDebugUnitTest --tests "com.swimvpn.app.data.network.AccessProfileResponseTest" --no-daemon --max-workers=1 --console=plain`.
- Live QA: create a newer revoked/failed order while an older order remains active and confirm the app still sees the active assignment.
- Live QA: force a usage-report failure during active backend premium connection and confirm the app does not lose the selected runtime node/config.
- Admin QA: attempt to move an assignment with measured usage onto a near-exhausted inventory source and confirm the move is rejected.

## Backend local dependency QA

- Backend modules have been restored locally with `npm ci`.
- Re-run before final deploy if customer-order-service changes again:
  - `cd backend && npm run prisma:validate`
  - `cd backend && npm run lint`
  - `cd backend && npm run test:policy`
- Optional security follow-up: review the 22 `npm audit` vulnerabilities separately; do not run `npm audit fix --force` without a dependency risk review.

## Backend npm audit follow-up

- Do not run `npm audit fix --force` blindly; it proposes major NestJS upgrades.
- Safe next investigation: run `npm audit fix` without `--force` in a branch, inspect `package-lock.json`, then run:
  - `cd backend && npm run prisma:validate`
  - `cd backend && npm run lint`
  - `cd backend && npm run test:policy`
  - `cd backend && npm run build:all`
- Planned remediation: create a dedicated NestJS 11 upgrade task for the remaining high/moderate vulnerabilities that require semver-major package moves.

## Android supplier subscription parser QA

- Build and install a signed release APK after the subscription cookie jar fix.
- Live QA: import or activate the `subs.eu-fffast.com` supplier link and confirm the app expands it into concrete VLESS nodes.
- Confirm the app does not display the supplier website as a runtime VPN server.
- Confirm the selected runtime node connects and traffic passes.
- Confirm existing working supplier links such as `wb.routerwb.ru` still expand and connect.

## 2026-05-07 Follow-ups
- [ ] Capture real-device logcat for disconnects and confirm whether causes are NETWORK_LOST, ENGINE_CRASH, SERVICE_KILLED, or BATTERY_RESTRICTION.
- [ ] Inspect tun2socks JNI fd ownership and explicitly document whether native duplicates or owns the VPN tun fd.
- [ ] Add a user-facing battery optimization help screen after validating copy and OEM-specific paths.
- [ ] Do not add Hysteria/TUIC runtime import until the runtime engine path is designed and tested.

## 2026-05-07 Provider Sample Follow-up
- [ ] Test one node from `subs.eu-fffast.com` on a real Android device and capture redacted logcat for Xray startup/handshake/runtime errors.

## 2026-05-07 18:20:04 +03:00 - Android VPN stability follow-up
- Test the underlay reconnect fix on a signed build matching the installed package, or reinstall after explicitly preserving/exporting test configs.
- Run a long screen-off test and Wi-Fi/mobile handoff test with logcat filters for network_lost, reconnect_scheduled, reconnect_success, service_destroyed, and engine_crashed.

## 2026-05-07 19:56:56 +03:00 - Remaining security review items
- Verify production Docker/Traefik exposure for internal TCP microservice ports before changing bind hosts.
- Keep raw ANDROID_ID as the documented operational device identity; verify it is not exposed in public APIs or logs.
- Add gateway/admin rate limiting and decide whether Swagger should be disabled or gated in production.
- Decide certificate pinning and HTTP subscription policy after confirming backend TLS and supplier requirements.

## 2026-05-07 20:18:31 +03:00 - Security hardening follow-up after multi-agent audit
- [x] Verify production Docker/Traefik exposure for internal TCP microservice ports before changing bind hosts.
- [x] Add gateway/admin rate limiting and decide whether Swagger should be disabled or gated in production.
- [ ] Keep raw ANDROID_ID as the documented operational device identity; verify it is not exposed in public APIs or logs.
- [ ] Decide certificate pinning and HTTP subscription policy after confirming backend TLS and supplier requirements.
- [ ] Add explicit `traefik.enable=false` labels to private-only services in the production compose.
- [ ] Bind `backend/docker-compose.yml` local Postgres/gateway port mappings to `127.0.0.1` or document that file as dev-only.
- [ ] Configure `GATEWAY_CORS_ORIGINS` in production with the final landing/admin origins.
- [ ] Decide whether `GET /access/:userNumber` should require matching device proof before returning email/phone.

## 2026-05-07 20:44:12 +03:00 - After installed-system audit
- [ ] Apply only low-risk compose/gateway hardening next: private Traefik deny labels, localhost-only dev compose ports, production CORS env.
- [ ] Keep raw ANDROID_ID as the operational device identity; protect DB/backups/secrets/admin access around it.
- [ ] Replace or reduce public `GET /access/:userNumber` only after mapping Android refresh usage and adding a safe profile mode.
- [ ] Add Android long-run QA for screen-off, Wi-Fi/mobile handoff, and reconnect cause logs before release tagging.

## 2026-05-07 21:02:44 +03:00 - Documentation alignment follow-up
- [ ] Continue treating current code/worklogs as source of truth when reviewing old docs.
- [ ] Do not document or plan device hashing unless the product decision changes.
- [ ] Keep legal/privacy copy aligned with implemented behavior after future privacy migrations.


## 2026-05-07 21:16:09 +03:00 - Raw Android device identity protection
- [ ] Verify no public API response includes raw `device_id`.
- [ ] Verify logs do not print raw Android device identifiers.
- [ ] Keep backend device checks on trial activation, profile completion, checkout binding, cancellation, crypt1 resolution, server exposure, and usage reporting.
- [ ] Protect DB backups, secrets, and admin access because raw device identity is intentionally stored.

## 2026-05-07 22:02:00 +03:00 - Android sticky restore QA
- [ ] On a real device, connect VPN, keep auto-connect enabled, then simulate service process death and confirm `sticky_restore_started` appears and tunnel reconnects.
- [ ] Repeat after waiting more than 15 seconds with a stale runtime snapshot and confirm sticky restore is skipped.
- [ ] Keep the boot-completed flow bootstrap-gated; do not add direct premium restore after reboot without entitlement revalidation.

## 2026-05-07 22:34:00 +03:00 - Tunnel speed QA follow-up
- [ ] Install matching signed build and compare CPU/d�bit for full tunnel before/after Xray stats/sniffing trim.
- [ ] Benchmark full tunnel vs local proxy on the same server and same network.
- [ ] Test Wi-Fi 2.4 GHz, stronger Wi-Fi/5 GHz if available, and mobile network separately before changing MTU.
- [ ] Only tune MTU/tun2socks buffers after collecting baseline speed, CPU, reconnect, and packet-loss evidence.

## 2026-05-07 22:52:00 +03:00 - Notification QA
- [ ] Install debug APK and verify the foreground notification text is `Run`, `En marche`, or `????????` according to the selected app language.
- [ ] Tap the VPN notification and confirm it returns to SwimVPN without restarting the tunnel.
- [ ] Lock screen for several minutes and confirm the foreground notification remains present and the VPN stays connected.

## 2026-05-07 23:22:00 +03:00 - Proxy route follow-up
- [ ] Install debug APK and verify an old LOCAL_PROXY preference opens the app in FULL_TUNNEL mode.
- [ ] Confirm the routing settings screen exposes only the production-safe full tunnel route.
- [ ] Keep local proxy internals for debug/manual proxy-aware tests only; do not present it as browser/global routing.

## 2026-05-07 23:45:00 +03:00 - Advanced proxy QA follow-up
- [ ] Install debug APK and verify LOCAL_PROXY is visible as an advanced/manual mode with HTTP/SOCKS port guidance.
- [ ] Start FULL_TUNNEL, switch to LOCAL_PROXY, and confirm Xray remains running while tun2socks/TUN are stopped.
- [ ] Start LOCAL_PROXY, switch to FULL_TUNNEL, and confirm VPN interface plus tun2socks are restored without a process kill.
- [ ] Verify explicit HTTP proxy 127.0.0.1:10809 and SOCKS proxy 127.0.0.1:10808 with a proxy-aware client.
- [ ] Keep using FULL_TUNNEL for normal browser/global Android routing tests.

## 2026-05-19 - Fulfillment and managed node follow-up
- [ ] Live QA after deploy: pay or replay a fulfilled Basic/Premium order and confirm badge, email, and managed backend nodes appear without app restart.
- [ ] Confirm Android preserves an active imported config after purchase and only auto-selects backend fulfillment when no active server exists or backend selection is stale.
- [ ] Add UI polish in Plan/Quota/Fulfillment so assigned managed nodes are visibly selectable and tied to the active plan.
- [ ] Define cancel, expired access, and upgrade/downgrade rules before changing entitlement mutation logic.
- [ ] Design the bandit/IA scope after managed nodes are stable: latency, speed, availability, geography, deterministic fallback, and visible green IA state.
- [ ] Decide whether backend should safely fetch/expand supplier HTTP subscription URLs, with SSRF/capacity/rate limits, instead of relying only on direct or base64 configs.

## 2026-05-19 - Batch 4A IA QA follow-up
- [ ] Live QA on device: refresh managed nodes, wait for latency probes, and confirm the green IA chip appears only on the validated recommended server.
- [ ] Validate degraded cases: failed probe, missing ping, stale ping, configless node, and premium-blocked node must not display the IA chip.
- [ ] Define Batch 4B backend contract for speed, availability, and geography signals before expanding recommendation scoring beyond Android-local data.

## 2026-05-19 - Batch 4A review QA follow-up
- [ ] Live QA: create a probe failure case and confirm no IA badge is displayed for that node.
- [ ] Live QA: confirm the active server card shows IA only when the active server id matches the validated recommended server id.

## 2026-05-19 - Batch 4B follow-up
- [ ] Live QA after backend redeploy: confirm `/api/v1/servers` returns `load`, `trafficUsedBytes`, `trafficTotalBytes`, and `availabilityStatus` only for entitled active users.
- [ ] Add real speed/throughput measurement before scoring speed; do not infer speed from protocol, country, or provider name.
- [ ] Define user geography signal separately from supplier display names before using distance in the bandit.

## 2026-05-19 - Batch 4B review follow-up
- [ ] Live QA: verify `CONGESTED` backend nodes are not shown with the green IA badge when an available node has close latency.
- [ ] Live QA: verify imported nodes show neutral/unknown load rather than a misleading zero-load signal.

## 2026-05-19 - Batch 4C follow-up
- [ ] Live QA: open Profile after fulfilled purchase and confirm active managed config shows host, provider, availability, and load without text overlap.
- [ ] Live QA: confirm imported configs still show their provider/traffic metadata and do not display backend-only availability hints.

## 2026-05-19 - Access contract live QA follow-up
- [ ] Live QA: buy an upgrade while Basic is active and confirm Basic remains active until the new fulfillment succeeds.
- [ ] Live QA: after successful upgrade/downgrade, confirm only the new managed config/servers are exposed and older active assignments no longer appear.
- [ ] Live QA: cancel after multiple historical purchases and confirm the app returns to freemium with no premium servers from older assignments.
- [ ] Decide later whether product UX needs explicit prorata/refund messaging; backend currently treats upgrade/downgrade as replacement after successful new purchase.

## 2026-05-19 - Access contract review QA follow-up
- [ ] Live QA: activate trial on an account with active paid access should not revoke the paid assignment.
- [ ] Live QA: cancel on an account with more than one active historical assignment should leave no premium servers exposed.
- [ ] Live QA: cancel with multiple paid/pending fulfillment orders should cancel all pending orders.

## 2026-05-19 - Backend contract final QA follow-up
- [ ] Live QA: after deploy, replay/fulfill an older active paid assignment and confirm profile + `/servers` still expose it even if it is not among recent orders.
- [ ] Live QA: report usage from the active device and confirm backend records usage on the active paid assignment when paid and trial records coexist.
- [ ] Live QA: attempt trial activation on an already paid account and confirm the app receives a clean denial while paid access remains usable.
- [ ] Contract follow-up: keep Android usage reporting aligned with required `deviceId` if any legacy client build is still in circulation.

## 2026-05-19 - Backend access review QA follow-up
- [ ] Live QA: retry fulfillment on an older order while a newer paid order is active and confirm the newer paid access is not revoked.
- [ ] Live QA: if paid and trial coexist, confirm profile and `/servers` both expose the paid config/nodes.
- [ ] Live QA: inspect a customer with multiple assignments on one order and confirm the active assignment remains authoritative.

## 2026-05-19 - Trial contract live QA follow-up
- [ ] Live QA: attempt trial activation while a paid order is pending fulfillment and confirm it is refused cleanly.
- [ ] Live QA: buy paid during active trial and confirm trial remains usable until paid fulfillment succeeds.
- [ ] Live QA: after paid fulfillment succeeds, confirm profile and `/servers` expose paid config/nodes, not trial.
- [ ] Live QA: expired trial must enter freemium app shell and expose no backend premium config/nodes.

## 2026-05-19 - No-device trial simulation QA
- [x] Simulated gateway mapping: active paid and paid-pending trial denials return conflict semantics.
- [x] Simulated active trial plus paid pending: active trial remains authoritative until paid fulfillment succeeds.
- [x] Simulated paid-pending trial refusal: customer email/phone are not mutated by the rejected activation attempt.
- [x] Simulated entitlement/server replacement safety through backend policy suite.

## 2026-05-19 - Trial Store final QA follow-up
- [x] Simulated active Trial Store assignment exposed through `/servers` as selectable backend nodes.
- [x] Simulated paid-over-trial priority for profile and server exposure.
- [x] Simulated disabled/dead trial config denial so raw runtime config is not exposed.
- [x] Simulated supplier-expired trial config downgrading runtime exposure even when grant expiry is later.
- [x] Simulated pending recovery race loss without duplicate assignment.
- [x] Simulated supplier message parsing for embedded VMess runtime configs.
- [x] Simulated gateway rejection for non-string trial import expiry dates.
- [x] Simulated Android bounded refresh policy for pending trial fulfillment after trial activation.
- [x] Simulated Trial Store node load staying unknown when no measured backend signal exists.
- [ ] Live QA after redeploy: activate trial with imported Trial Store capacity and confirm badge, `/servers`, selectable nodes, and connect flow appear without app restart.
- [ ] Live QA after redeploy: import Trial Store capacity after a pending trial and confirm Android refresh picks up `ACTIVE_TRIAL`.
