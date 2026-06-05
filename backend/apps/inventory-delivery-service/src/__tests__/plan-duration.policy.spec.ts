import { PLAN_DURATION_MS, TRIAL_DURATION_MS, planDurationMs } from '@app/contracts';

function assert(condition: boolean, message: string) {
  if (!condition) throw new Error(message);
}

assert(PLAN_DURATION_MS.WEEK === 7 * 24 * 60 * 60 * 1000, 'WEEK = 7d');
assert(PLAN_DURATION_MS.MONTH === 30 * 24 * 60 * 60 * 1000, 'MONTH = 30d');
assert(PLAN_DURATION_MS.QUARTER === 90 * 24 * 60 * 60 * 1000, 'QUARTER = 90d');
assert(TRIAL_DURATION_MS === 3 * 24 * 60 * 60 * 1000, 'TRIAL = 3d');

assert(planDurationMs('MONTH', false) === PLAN_DURATION_MS.MONTH, 'MONTH paid');
assert(planDurationMs('QUARTER', false) === PLAN_DURATION_MS.QUARTER, 'QUARTER paid');
assert(planDurationMs('WEEK', false) === PLAN_DURATION_MS.WEEK, 'WEEK paid');
assert(planDurationMs('NOPE', false) === PLAN_DURATION_MS.WEEK, 'unknown -> WEEK');
assert(planDurationMs('MONTH', true) === TRIAL_DURATION_MS, 'trial overrides to TRIAL');

console.log('plan-duration.policy.spec.ts passed');
