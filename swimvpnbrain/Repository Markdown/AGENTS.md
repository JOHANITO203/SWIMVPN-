# AGENTS.md

## Purpose

This repository is operated under a controlled agentic implementation workflow.

The assistant must not work in freeform mode.
The assistant must follow the required workflow, output format, repository rules, deployment rules, and verification rules below.

This repository contains SWIMVPN, a production-oriented VPN product with:
- backend microservices
- Android app
- landing page
- PostgreSQL/Prisma database
- VPN config handling
- freemium/trial/subscription logic

The main objective is safe, production-ready implementation.

---

## Mandatory workflow

For every task, always execute in this exact order:

1. AUDIT
2. PLAN
3. IMPLEMENT
4. VERIFY
5. NOTE

Do not skip a step.
Do not merge steps.
Do not start coding before AUDIT and PLAN are explicit.

---

## Step definitions

### 1. AUDIT

Before making any change:
- inspect the existing code
- identify impacted files
- identify dependencies
- identify risks
- identify what already exists
- identify what must not be touched
- identify contradictory logic
- identify duplicated business rules
- identify source-of-truth conflicts

### 2. PLAN

Before coding, provide:
- scope
- target files
- exact changes to make
- things intentionally not changed
- verification method
- expected risk level

No code changes before the PLAN is explicit.

### 3. IMPLEMENT

Implementation rules:
- work only on the approved scope
- make the smallest useful change set
- preserve existing architecture unless change is required
- preserve raw VPN config data intact
- never silently invent missing business rules
- never hardcode fake success states
- never bypass entitlement/security checks
- never rewrite unrelated modules

### 4. VERIFY

After implementation, verify using:
- build or compile checks where possible
- tests where available
- logical verification of edge cases
- consistency against existing architecture
- backend/frontend contract checks when relevant
- deployment safety checks when relevant

Never claim completion without verification.
Never hide failed tests.

### 5. NOTE

After each meaningful batch:
- update `WORKLOG.md`
- update `DECISIONS.md` if an architectural choice was made
- update `TODO.md` if next actions changed

---

## Output format required before coding

Before modifying code, always output:

### TASK AUDIT
- Goal
- Relevant files
- Current state
- Risks
- Missing information
- Existing source of truth
- Contradictions found, if any

### IMPLEMENTATION PLAN
- Files to change
- Exact intended changes
- Verification plan
- Out-of-scope items
- Risk level

Do not code before this format is produced.

---

## Output format required after coding

After implementation, always output:

### IMPLEMENTATION REPORT
- Files changed
- Exact fixes made
- Business rules affected
- Tests/builds run
- Tests/builds passed
- Tests/builds failed
- Remaining risks
- Manual QA checklist
- Deploy readiness: YES / NO / PARTIAL

If a command cannot be run, explain exactly why.

---

## Global agentic deployment rules

The assistant is preparing the project for safe production deployment.

Rules:

1. Do not make blind changes.
   Always inspect the existing architecture first.

2. Prefer minimal targeted fixes.
   Do not rewrite large modules unless the current structure is clearly broken and the reason is explained.

3. Do not change unrelated systems.
   Stay inside the requested scope.

4. Preserve production behavior.
   Existing working flows must remain working.

5. Do not hardcode success.
   Never hardcode users as premium.
   Never bypass entitlement checks.
   Never return fake ACTIVE status.
   Never disable security checks just to make the UI work.

6. Backend is the source of truth.
   Frontend may improve UX, but backend must enforce premium access rules.

7. Freemium must remain open.
   Expired users must enter the app shell, navigate, see subscription offers, and use free/imported configs.
   Expired users must not be trapped in a dead screen.

8. No full-app lockout except unauthenticated/profile-incomplete cases.
   Trial expiration is not account blocking.
   Subscription expiration is not account blocking.

9. Avoid destructive changes.
   Do not delete files, migrations, tables, env variables, or config unless proven unused and risky.

10. Respect current deployment stack.
   Do not alter Docker, Dokploy, Traefik, Postgres, domains, or env behavior unless the deployment failure is directly traced to them.

---

## Repository truth rules

- PostgreSQL is the source of truth.
- Prisma ORM is required.
- Telegram is not the source of truth.
- PSP integration is out of scope unless explicitly requested.
- Frontend already exists.
- Backend refactor is the priority unless the task explicitly targets Android or landing.
- Admin authentication is required.
- Customer auth is not required for MVP unless explicitly requested.
- Offers are:
  - Basic
  - Premium
  - Platinum
- Default currency is RUB.
- Backend entitlement logic must be authoritative.
- Android UI must not invent access state independently from backend truth.

---

## Current architecture target

The backend is microservice-oriented.

Current target services:
- gateway-service
- customer-order-service
- inventory-delivery-service
- admin-control-service
- vpn-config-engine-service
- store-engine-service
- landing page service

Do not introduce extra services unless clearly justified or asked.

---

## Trial / subscription / freemium rules

The correct access model is:

### ACTIVE_TRIAL

- premium servers for trial allowed
- premium config for trial allowed
- connect allowed
- no paywall required
- trial servers and config are not yet gave 

### ACTIVE_SUBSCRIPTION

- premium servers allowed
- premium config allowed
- connect allowed
- no paywall required

### EXPIRED_TRIAL

- app shell allowed
- freemium mode allowed
- imported configs allowed
- premium servers denied
- premium config denied
- paywall shown for premium actions
- no full app lockout

### EXPIRED_SUBSCRIPTION

Same as EXPIRED_TRIAL.

### TRIAL_AVAILABLE

- trial activation allowed
- app must not deadlock
- if freemium mode exists, user should be able to continue free
- must not be treated as blocked

### PROFILE_INCOMPLETE

- onboarding/profile completion required
- must not be confused with expired trial
- must not be confused with subscription expiration

---

## Trial/access/freemium audit rules

When working on trial, subscription, paywall, premium access, or app bootstrap, inspect a wide enough scope.

Backend areas:
- customer-order-service
- store-engine-service
- vpn-config-engine-service
- gateway-service
- Prisma schema and migrations
- seed logic
- entitlement/trial/subscription logic
- server list logic
- config generation/config delivery
- access bootstrap endpoints
- payment/subscription status logic if relevant

Android areas:
- MainActivity
- MainViewModel
- navigation graph
- onboarding/trial activation screens
- subscription/paywall screens
- imported config/free mode logic
- Retrofit API models
- local persistence/DataStore/SharedPreferences
- VPN connection button logic
- server selection logic
- app bootstrap flow

Look for contradictions such as:
- backend says trial is active, frontend routes to blocked state
- frontend expects status names backend does not send
- backend returns profile but no config
- backend returns config but no servers
- trial activation creates records but bootstrap endpoint does not read them
- gateway maps responses incorrectly
- Android model field names do not match API JSON
- userNumber/phone/identifier mismatch
- timezone/date expiration bug
- trial is created but assigned server/config is missing
- active trial exists but server list endpoint returns empty
- premium config requires subscription but should also allow active trial
- local cached expired state overrides fresh backend state
- app navigates to wrong screen after trial activation
- backend returns 401/403 where frontend expects 200 with status
- stale API base URL or wrong environment

For every contradiction found, output:
- file
- function
- current behavior
- expected behavior
- risk level
- proposed fix

---

## Trial/access task required flow map

For trial/access work, always map this flow before coding:

1. New user opens app
2. User creates/has profile
3. User activates trial
4. Backend persists trial
5. App bootstraps access
6. App receives entitlement
7. App receives servers/config
8. User connects
9. Trial expires
10. User becomes freemium

For each step, identify:
- files involved
- endpoints involved
- data model involved
- expected status
- actual code behavior

---

## Backend security boundary rules

Frontend paywall routing is not enough.
Backend must enforce entitlement.

Rules:
- ACTIVE_TRIAL users may receive premium servers/configs.
- ACTIVE_SUBSCRIPTION users may receive premium servers/configs.
- EXPIRED users must not receive premium backend servers/configs.
- EXPIRED users may receive their profile and enter the app shell.
- EXPIRED users may use imported/custom configs.
- Premium-only API endpoints must return a clear denial such as `PREMIUM_REQUIRED` or equivalent.
- Never expose premium IPs, ports, configs, or subscription URLs to expired users.
- Never rely only on Android UI guards for business security.

---

## VPN config handling rules

The system must not describe config handling vaguely as “decryption”.

Config handling must be treated as:
- ingest
- parse
- validate
- normalize
- classify
- preview
- prepare runtime payload

Raw config must always be preserved intact.

Priority config ecosystem:
- VLESS
- VLESS Reality
- TCP
- VMess
- Trojan
- Shadowsocks
- JSON Xray
- JSON V2Ray

Do not corrupt, reformat, or partially rewrite raw VPN configs unless explicitly required.

---

## Inventory rules

Inventory must stay simple and useful.

Configs are categorized by:
- week
- month
- quarter

Minimal inventory statuses:
- available
- reserved
- assigned
- dead

Do not over-model supplier logic in the MVP.

---

## Order rules

Orders are critical.
Every paid order must remain traceable to:
- customer
- plan
- assigned config
- delivery status

Do not bypass order tracking.

---

## Delivery rules

MVP delivery is intentionally simple:
- Telegram admin notification
- email can be manual or semi-automated

Do not build advanced delivery orchestration unless explicitly requested.
Do not add cron/job systems unless explicitly needed.

---

## Admin rules

Admin must exist in two forms:
- backend/admin panel
- Telegram admin control layer

Telegram is only an admin control and notification channel.
It never replaces database truth.

Admin authentication is required.

---

## Android release/build rules

When working on Android build, APK release, ProGuard, or Gradle:

- Do not blindly update all dependencies.
- Do not change compileSdk/targetSdk unless requested or required.
- Prefer stable release build over broad modernization.
- Keep native VPN/JNI rules safe.
- Do not break debug builds while fixing release builds.
- Run the strongest available Gradle build check.
- Report warnings separately from blocking errors.

For release APK:
- prefer signed release build
- minify/shrink only if runtime remains stable
- keep required VPN/native classes
- preserve Retrofit/Gson models required for API parsing

---

## Landing page / globe rules

The landing page already exists.

When working on the globe:
- modify only globe-related files unless explicitly allowed
- do not redesign the full landing page
- do not modify Docker/backend/API/Android
- keep the canvas transparent
- do not trap mobile scroll
- do not overlap hero headline or CTA
- preserve visual grammar of the page
- avoid heavy labels, panels, or chaotic sci-fi clutter
- prioritize a clean premium VPN/cybersecurity Earth visualization

Main globe files:
- `src/components/landing/InteractivePixelGlobe.tsx`
- `src/components/landing/globe/GlobeComponents.tsx`
- `src/components/landing/globe/globeUtils.ts`

If another file affects globe positioning, list it first before modifying.

---

## Deployment safety checks

Before finishing any deployment-sensitive task, run the strongest available checks for the touched area.

Backend:
- install/build check if available
- TypeScript compile if available
- Prisma generate if Prisma exists
- migration validation if safe
- unit tests if available

Android:
- Gradle build check
- assembleDebug or assembleRelease depending on task
- Kotlin compile check
- do not update Android dependencies broadly unless necessary

Frontend/Landing:
- npm build
- TypeScript check
- lint if available

If a command cannot be run, explain exactly why.

---

## Git rules

Before changes:
- run `git status`
- identify current branch
- list uncommitted files

During changes:
- modify only files required for the task

After changes:
- run `git diff --stat`
- summarize diff
- run relevant tests/builds
- run `git status`

Do not commit unless explicitly asked.
If asked to commit, use a clean message explaining the fix.

Never hide failed tests.
If tests fail, report:
- command run
- failure summary
- suspected cause
- whether failure is related to your changes

---

## Manual QA checklist

Use relevant items depending on the task:

1. New user activates trial
2. Active trial user can connect
3. Active trial user relaunches app
4. Expired trial user enters freemium app
5. Expired trial user is pushed to subscription only for premium actions
6. Active paid user can connect
7. Expired paid user enters freemium app
8. Imported custom config works for free/expired user
9. Premium servers are not exposed to expired users
10. APK download still works
11. Backend health endpoints respond
12. App does not crash on cold start
13. No infinite navigation loop
14. No stale local cache blocks a valid active user
15. Android release build works if build files were touched
16. Landing page still builds if landing files were touched
17. Docker/Dokploy compatibility remains intact if deployment files were touched

---

## Forbidden behavior

Do not:
- code before audit
- invent APIs without checking existing code
- rewrite unrelated files
- silently refactor large areas
- create duplicate systems
- mark unverified code as complete
- ignore existing repository conventions
- skip documentation updates after implementation
- hardcode premium access
- bypass entitlement checks
- expose premium servers/configs to expired users
- confuse expired trial with blocked account
- confuse profile incomplete with expired trial
- mutate raw VPN configs unnecessarily
- alter Docker/deployment unless directly required
- update dependencies broadly without a clear reason
- claim a deployment is safe without checks

---

## Required repository files

Always use and maintain:
- `AGENTS.md`
- `WORKLOG.md`
- `DECISIONS.md`
- `TODO.md`

If any file does not exist, create it before major implementation begins.

---

## Final instruction

Be strict, skeptical, and deployment-focused.

Do not optimize for pretty code first.
Optimize for:
- correct production behavior
- no broken access states
- no contradictory trial/subscription logic
- no security bypass
- safe deployability
- minimal targeted changes