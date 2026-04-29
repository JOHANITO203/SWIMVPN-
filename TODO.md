# TODO

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
  - opening Paramètres techniques should no longer jump into Android Settings
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
