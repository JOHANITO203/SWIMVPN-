AGENTIC DEPLOYMENT RULES — STRICT EXECUTION MODE

You are not only fixing bugs. You are preparing the project for a safe production deployment.

Your mission is to move the project toward a deployable, testable, production-safe state without creating new instability.

GLOBAL RULES

1. Do not make blind changes.
Before modifying code, inspect the existing architecture, list the responsible files, and explain the current flow.

2. Prefer minimal targeted fixes.
Do not rewrite large modules unless the current structure is clearly broken and you explain why.

3. Do not change unrelated systems.
If the task is trial/access logic, do not modify landing page, globe, Docker, APK build, payment, VPN engine, or styling unless directly required.

4. Preserve production behavior.
Existing working flows must remain working:
- active trial access
- active subscription access
- expired user freemium access
- imported custom configs
- APK download
- backend health endpoints
- Docker deployment compatibility

5. Do not hardcode success.
Never hardcode users as premium.
Never bypass entitlement checks.
Never return fake ACTIVE status.
Never disable security checks just to make the UI work.

6. Backend is the source of truth.
Frontend may improve UX, but backend must enforce premium access rules.
Expired users must not receive premium servers/configs from API calls.

7. Freemium must remain open.
Expired users must enter the app shell, navigate, see subscription offers, and use free/imported configs.
Expired users must not be trapped in a dead screen.

8. No full-app lockout except unauthenticated/profile-incomplete cases.
Trial expiration is not account blocking.
Subscription expiration is not account blocking.

9. Avoid destructive changes.
Do not delete files, migrations, tables, env variables, or config unless you prove they are unused and risky.

10. Respect current deployment stack.
Do not alter Docker, Dokploy, Traefik, Postgres, domains, or env behavior unless the deployment failure is directly traced to them.

DEPLOYMENT SAFETY CHECKS

Before finishing, run the strongest available checks for this repo:

Backend:
- install/build check if available
- TypeScript compile if available
- Prisma generate if Prisma exists
- migration validation if safe
- unit tests if available

Android:
- Gradle build check
- assembleDebug or assembleRelease depending on task
- check Kotlin compile errors
- do not update Android dependencies broadly unless necessary

Frontend/Landing:
- npm build
- TypeScript check
- lint if available

If a command cannot be run, explain exactly why.

GIT RULES

1. Before changes:
Run:
- git status
- identify current branch
- list uncommitted files

2. During changes:
Modify only files required for the task.

3. After changes:
Run:
- git diff --stat
- git diff summary
- relevant tests/builds
- git status

4. Do not commit unless explicitly asked.
If asked to commit, use a clean message explaining the fix.

5. Never hide failed tests.
If tests fail, report:
- command run
- failure summary
- suspected cause
- whether failure is related to your changes

CODE REVIEW RULES

Look for contradictions across:
- backend status values
- Android enum/model values
- API JSON field names
- route guards
- connect button logic
- server list access
- config delivery access
- trial activation persistence
- subscription expiration
- local cache / DataStore state
- gateway response mapping

For every contradiction found, output:
- file
- function
- current behavior
- expected behavior
- risk level
- proposed fix

DEPLOYMENT READINESS OUTPUT

At the end, provide a deployment readiness report:

1. Files inspected
2. Files modified
3. Root causes found
4. Fixes implemented
5. Tests/builds run
6. Tests/builds passed
7. Tests/builds failed
8. Remaining risks
9. Manual QA checklist
10. Whether this is safe to deploy now: YES / NO / PARTIAL

MANUAL QA CHECKLIST

Verify these flows manually after deployment:

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
14. No stale local cache blocking a valid active user

SECURITY / BUSINESS RULES

The correct access model is:

ACTIVE_TRIAL:
- premium servers allowed
- premium config allowed
- connect allowed

ACTIVE_SUBSCRIPTION:
- premium servers allowed
- premium config allowed
- connect allowed

EXPIRED_TRIAL:
- app shell allowed
- freemium mode allowed
- imported configs allowed
- premium servers denied
- premium config denied
- paywall shown for premium actions

EXPIRED_SUBSCRIPTION:
- same as EXPIRED_TRIAL

TRIAL_AVAILABLE:
- trial activation allowed
- app must not deadlock
- if freemium mode exists, user should be able to continue free

PROFILE_INCOMPLETE:
- onboarding/profile completion required
- must not be confused with expired trial

FINAL INSTRUCTION

Be strict, skeptical, and deployment-focused.
Do not optimize for pretty code first.
Optimize for correct production behavior, no broken access states, no security bypass, and safe deployability.