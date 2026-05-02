# Resale Cap Final Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align backend capacity and Android subscription UI with the final product truth: every supplier config can be sold to at most two customer orders, and every plan displays up to two devices.

**Architecture:** Keep PostgreSQL/backend as source of truth. Split policy semantics between resale slot consumption (`1` per paid order) and public device allowance (`2` for every paid plan). Inventory fulfillment consumes resale slots only; Android displays the public device allowance.

**Tech Stack:** NestJS microservices, Prisma, TypeScript policy tests, Android Kotlin/Compose resources.

---

### Task 1: Backend Policy Semantics

**Files:**
- Modify: `backend/libs/contracts/src/plan-policy.ts`
- Test: `backend/apps/inventory-delivery-service/src/__tests__/supplier-capacity.policy.spec.ts`

- [ ] Write failing assertions:

```ts
assert(DEFAULT_RESALE_SLOT_CAP === 2, 'Supplier links should be resold to max 2 orders');
assert(getPlanResaleSlotCount(PlanCategory.WEEK) === 1, 'Basic should consume 1 resale slot');
assert(getPlanResaleSlotCount(PlanCategory.MONTH) === 1, 'Premium should consume 1 resale slot');
assert(getPlanResaleSlotCount(PlanCategory.QUARTER) === 1, 'Platinum should consume 1 resale slot');
assert(getPlanDeviceAllowance(PlanCategory.WEEK) === 2, 'Basic should display up to 2 devices');
assert(getPlanDeviceAllowance(PlanCategory.MONTH) === 2, 'Premium should display up to 2 devices');
assert(getPlanDeviceAllowance(PlanCategory.QUARTER) === 2, 'Platinum should display up to 2 devices');
```

- [ ] Run: `cd backend && npx ts-node -r tsconfig-paths/register apps/inventory-delivery-service/src/__tests__/supplier-capacity.policy.spec.ts`

Expected: FAIL because helper functions/default cap are not aligned.

- [ ] Implement policy helpers:

```ts
export const DEFAULT_RESALE_SLOT_CAP = 2;
export const DEFAULT_PLAN_DEVICE_ALLOWANCE = 2;

export function getPlanResaleSlotCount(code: PlanCategory): number {
  return 1;
}

export function getPlanDeviceAllowance(code: PlanCategory): number {
  return DEFAULT_PLAN_DEVICE_ALLOWANCE;
}

export function getPlanSlotCount(code: PlanCategory): number {
  return getPlanResaleSlotCount(code);
}
```

- [ ] Run the policy test again. Expected: PASS.

### Task 2: Fulfillment And Profile Mapping

**Files:**
- Modify: `backend/apps/inventory-delivery-service/src/inventory.service.ts`
- Modify: `backend/apps/customer-order-service/src/customer.service.ts`
- Modify: `backend/prisma/seed.ts`

- [ ] Update inventory import/fulfillment to use resale slot helper semantics.

```ts
import { getPlanResaleSlotCount } from '@app/contracts';
```

- [ ] Update profile `devicesAllowed` to use `getPlanDeviceAllowance` for paid plans and trial if desired by product.

```ts
devicesAllowed: latestOrder ? getPlanDeviceAllowance(latestOrder.plan.code) : 0,
```

- [ ] Update seed `slot_count` values to `1` for Basic/Premium/Platinum because this field now represents resale slot consumption.

- [ ] Run: `cd backend && npm run lint && npm run test:policy`.

Expected: PASS.

### Task 3: Android Subscription UI Copy

**Files:**
- Modify: `android/app/src/main/java/com/swimvpn/app/data/model/NetworkModels.kt`
- Modify: `android/app/src/main/java/com/swimvpn/app/ui/screens/SubscriptionScreen.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-fr/strings.xml`
- Modify: `android/app/src/main/res/values-ru/strings.xml`

- [ ] Add optional `slotCount` to plan DTO if needed for future display, but display should not depend on it for this batch.

```kotlin
@SerializedName("slot_count") val slotCount: Int? = null
```

- [ ] Add localized string:

```xml
<string name="plan_devices_up_to_two">Up to 2 devices</string>
```

French:

```xml
<string name="plan_devices_up_to_two">Jusqu’à 2 appareils</string>
```

Russian:

```xml
<string name="plan_devices_up_to_two">До 2 устройств</string>
```

- [ ] Update `PlanCard` to show the device row between duration and price.

- [ ] Run Android compile/resource checks:

```powershell
cd android
.\gradlew.bat :app:processDebugResources :app:compileDebugKotlin --no-daemon
```

Expected: PASS.

### Task 4: Documentation And Final Verification

**Files:**
- Modify: `WORKLOG.md`
- Modify: `TODO.md`

- [ ] Add worklog entry describing backend/UI alignment.
- [ ] Update TODO to move resale cap alignment out of pending if implementation succeeds.
- [ ] Run:

```powershell
git diff --check
git diff --stat
git status --short --branch
```

Expected: no whitespace errors; clear list of changed files.
