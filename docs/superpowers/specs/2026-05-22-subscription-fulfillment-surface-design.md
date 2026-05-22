# Subscription Fulfillment Surface Design

Date: 2026-05-22

## Scope

This spec covers one surface only: a paid subscription purchase becoming usable inside the Android app.

The flow is:

1. User buys a subscription through SwimPay.
2. SwimPay confirms payment.
3. Backend marks the order as paid.
4. Backend assigns supplier inventory.
5. Backend resolves runtime nodes for the assigned supplier config.
6. Android Premium Servers displays the resolved destinations.
7. Post-purchase email is sent as a backup delivery channel.
8. Profile displays the purchased plan badge.
9. The active plan cannot be bought again while active.
10. Upgrade to a higher plan is allowed and becomes the new profile truth after paid fulfillment.

This spec does not cover retroactive repair for older orders, full admin redesign, payment provider changes, Android VPN tunnel logic, or imported/free config logic.

## Terminology

The project must stop using one ambiguous idea of "device allowance" for both public product copy and backend inventory economics.

Use these concepts instead:

- `publicDeviceAllowance`: what the customer is told the plan supports.
- `supplierUserCapacity`: maximum SWIMVPN customers allowed to share one paid supplier config.
- `supplierCapacityUnitsPerUser`: internal capacity cost consumed by one customer based on their plan.
- `usedSupplierCapacityUnits`: current consumed capacity units on a supplier config.
- `maxSupplierCapacityUnits`: total internal capacity units available on a supplier config.
- `maxTrialDeviceAssignments`: maximum trial devices allowed on one trial supplier config.
- `usedTrialDeviceAssignments`: current trial device assignments on one trial supplier config.

Public plan display:

| Plan | Public device allowance |
| --- | ---: |
| Basic | 1 |
| Premium | 2 |
| Platinum | 3 |

Paid supplier inventory strategy:

| Plan | Capacity units per customer |
| --- | ---: |
| Basic | 1 |
| Premium | 2 |
| Platinum | 3 |

Default paid supplier strategy:

- `supplierUserCapacity = 2`
- `maxSupplierCapacityUnits = supplierUserCapacity * plan capacity units`

This means the same supplier config can support up to two SWIMVPN customers, while each customer still sees only the public device allowance of their own plan.

Trial supplier inventory strategy:

- A trial supplier config accepts up to `maxTrialDeviceAssignments = 5`.
- The sixth trial device must be denied or routed to another available trial config.
- When no trial config has available capacity, the trial capacity is out of stock.

## Purchase Fulfillment Rules

SwimPay confirmation is the trigger for fulfillment.

When a `payment.confirmed` webhook is accepted:

1. Validate the order and SwimPay session.
2. Mark the order as `PAID`.
3. Run fulfillment.
4. Assign a compatible supplier config.
5. Create or update an active `OrderAssignment`.
6. Mark the order `FULFILLED` only after assignment succeeds.
7. Send post-purchase email after fulfillment success.
8. Emit/admin-log fulfillment events.

Email delivery is a side effect, not the source of truth.

The source of truth is the database:

- `Order`
- `OrderAssignment`
- `InventoryItem`
- `Customer`

## Upgrade And Same-Plan Rules

If the user has an active Basic plan:

- Premium and Platinum remain purchasable.
- Basic is not purchasable again while Basic is active.

If the user has an active Premium plan:

- Platinum remains purchasable.
- Premium is not purchasable again while Premium is active.
- Basic should not be offered as a normal downgrade purchase in this surface.

If the user has an active Platinum plan:

- Platinum is not purchasable again while active.
- Lower plans should not be offered as normal downgrade purchases in this surface.

When an upgrade payment is confirmed and fulfilled:

- the new paid assignment becomes the current profile truth;
- older active paid assignments for that customer are revoked/replaced through the existing inventory revocation path;
- profile badge, entitlement, and `/servers` reflect the upgraded plan.

If upgrade payment is pending fulfillment:

- the current active plan remains usable until the new plan is fulfilled;
- the app may show pending fulfillment state, but must not remove usable access prematurely.

## Profile Badge And Subscription Card State

Profile badge should display the purchased plan identity:

- Basic
- Premium
- Platinum

It should replace a generic active-access badge when a paid plan exists.

Subscription screen behavior:

- active same plan: disabled purchase state plus cancel action;
- higher plan: purchasable upgrade action;
- lower plan: not treated as a normal purchase path in this surface;
- expired plan: plan can be bought again through normal checkout.

The Android UI must not invent active status. It displays backend profile truth.

## Backend Supplier URL Resolution

Raw supplier config must be preserved exactly as ingested, regardless of its shape:

- direct VLESS/VMess/Trojan/Shadowsocks line;
- multi-line config list;
- base64 subscription payload;
- HTTP(S) supplier subscription URL;
- SwimVPN encrypted link if explicitly supported.

The backend must be able to resolve HTTP(S) supplier URLs to runtime nodes before exposing Premium Servers. Android should not need supplier URLs to build the Premium Servers list.

The safe backend pipeline is:

1. ingest raw supplier config;
2. if HTTP(S), fetch with strict network controls;
3. parse;
4. normalize;
5. extract runtime nodes;
6. filter user-visible metadata;
7. expose only app-safe runtime node data.

Mandatory HTTP(S) controls:

- short timeout;
- maximum response size;
- no localhost;
- no private/link-local/reserved IPs;
- no unsafe redirects;
- accepted content is treated as subscription payload only;
- raw supplier URL is not exposed to Android users;
- raw runtime node config may be exposed only when the user is entitled and needs it to connect.

Admin preview follows the same rule:

- show node count;
- show countries/destinations;
- show parse status;
- do not expose supplier URL, host, UUID, port, provider internals, or raw metadata in public UI.

## Premium Servers Exposure

A premium node is considered installed when:

- the customer has an active paid `OrderAssignment`;
- that assignment points to an inventory item;
- `/servers` resolves runtime nodes from that inventory item;
- Android receives backend nodes with `source = backend`.

The Premium Servers surface should show only useful user-choice data:

- destination/country/city label when available;
- latency/ping;
- selected state;
- optional availability badge;
- no supplier metadata overload.

The deterministic AI agent may recommend a node, but should not choose without user agency.

## Trial Config Capacity Rule

Trial config capacity must enforce:

- one trial supplier config can serve up to five devices;
- each accepted trial device creates a trial assignment;
- the sixth device is denied for that config or routed to another available trial config;
- when all trial configs are full, expired, dead, or disabled, trial stock is unavailable;
- expired trial users remain in freemium app shell.

This must be enforced in backend code, not only Android UI.

## Implementation Workflow

Each implementation step must follow this loop:

1. detailed audit of the specific surface;
2. targeted implementation;
3. implementation review;
4. fix all findings at the source;
5. review the corrected implementation against the goal;
6. test;
7. if accepted, close that step and move to the next;
8. if not accepted, correct the failing element directly instead of masking it with a patch.

Do not combine multiple independent surfaces in one implementation pass.

## Acceptance Criteria

The surface is accepted only if:

- paid SwimPay confirmation can produce usable premium backend nodes;
- post-purchase email still sends after fulfillment success;
- active plan badge reflects Basic/Premium/Platinum;
- same active plan cannot be bought again;
- higher-plan upgrade is allowed and becomes profile truth after fulfillment;
- backend resolves HTTP(S) supplier URLs safely;
- Android does not receive supplier URLs or unnecessary supplier metadata for Premium Servers;
- trial supplier configs enforce five devices max;
- existing VPN tunnel logic, parser raw preservation, and freemium access are not broken.
