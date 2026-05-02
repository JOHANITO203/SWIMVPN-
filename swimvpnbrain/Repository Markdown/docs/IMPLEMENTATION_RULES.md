# IMPLEMENTATION RULES - SWIMVPN+

## Mandatory Execution Order
For each engineering task:
1. Audit
2. Plan
3. Implement
4. Verify
5. Note

Do not skip or merge stages.

## Audit Rules
Before changes:
- inspect existing code and docs
- identify impacted files and dependencies
- identify risks and protected areas
- confirm what already exists vs what is missing

## Planning Rules
Before coding:
- declare scope
- list files to change
- define exact intended changes
- define out-of-scope items
- define verification method

## Implementation Rules
- Apply smallest useful change set.
- Preserve current architecture unless change is required.
- Do not invent business rules not present in source documentation.
- Do not rewrite unrelated areas.
- Do not claim future-state design as already implemented reality.
- Do not create fantasy architecture entries in `docker-compose`.

## Verification Rules
Completion requires verification:
- build/compile checks where available
- tests where available
- logical edge-case review
- architecture consistency review

Never mark unverified work as complete.

## Documentation Note Rules
After meaningful change batches:
- update `WORKLOG.md`
- update `DECISIONS.md` when architectural decisions are made
- update `TODO.md` when next actions change

## Data and Domain Guardrails
- PostgreSQL remains source of truth.
- Prisma is required.
- Telegram is admin control only.
- Raw VPN config data must be preserved intact.
- Config handling vocabulary must remain explicit:
  - ingest
  - parse
  - validate
  - normalize
  - classify
  - preview
  - prepare runtime payload

## MVP Scope Guardrails
- No customer auth requirement in MVP.
- Admin auth is mandatory.
- PSP integration is postponed.
- No unnecessary service expansion beyond target service list.