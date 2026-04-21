# AGENTS.md

## Purpose
This repository is operated under a controlled implementation workflow.

The assistant must not work in freeform mode.
The assistant must follow the required workflow, output format, and repository rules below.

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

### 2. PLAN
Before coding, provide:
- scope
- target files
- exact changes to make
- things intentionally not changed
- verification method

No code changes before the PLAN is explicit.

### 3. IMPLEMENT
Implementation rules:
- work only on the approved scope
- make the smallest useful change set
- preserve existing architecture unless change is required
- preserve raw VPN config data intact
- never silently invent missing business rules

### 4. VERIFY
After implementation, verify using:
- build or compile checks where possible
- tests where available
- logical verification of edge cases
- consistency against existing architecture

Never claim completion without verification.

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

### IMPLEMENTATION PLAN
- Files to change
- Exact intended changes
- Verification plan
- Out-of-scope items

Do not code before this format is produced.

---

## Repository truth rules

- PostgreSQL is the source of truth.
- Prisma ORM is required.
- Telegram is not the source of truth.
- PSP integration is out of scope unless explicitly requested.
- Frontend already exists.
- Backend refactor is the priority.
- Admin authentication is required.
- Customer auth is not required for MVP.
- Offers are:
  - week
  - month
  - quarter
- Default currency is RUB.

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

Do not introduce extra services unless clearly justified.

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

---

## Required repository files

Always use and maintain:
- `AGENTS.md`
- `WORKLOG.md`
- `DECISIONS.md`
- `TODO.md`

If any file does not exist, create it before major implementation begins.
