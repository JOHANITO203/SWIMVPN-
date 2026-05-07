import { strict as assert } from 'assert';
import {
  clearGatewayRateLimitBuckets,
  createGatewayRateLimitMiddleware,
} from '../security/gateway-rate-limit';

function createResponse() {
  return {
    code: 200,
    body: undefined as unknown,
    status(value: number) {
      this.code = value;
      return this;
    },
    json(value: unknown) {
      this.body = value;
    },
  };
}

async function main() {
  clearGatewayRateLimitBuckets();
  let timestamp = 1_000;
  const middleware = createGatewayRateLimitMiddleware(() => timestamp);

  let passed = 0;
  for (let i = 0; i < 8; i += 1) {
    const res = createResponse();
    middleware(
      {
        method: 'POST',
        originalUrl: '/api/v1/admin/login',
        headers: { 'x-forwarded-for': '203.0.113.10' },
      },
      res,
      () => {
        passed += 1;
      },
    );
    assert.equal(res.code, 200, 'allowed login attempts must not return an error');
  }

  const blocked = createResponse();
  middleware(
    {
      method: 'POST',
      originalUrl: '/api/v1/admin/login',
      headers: { 'x-forwarded-for': '203.0.113.10' },
    },
    blocked,
    () => {
      passed += 1;
    },
  );

  assert.equal(passed, 8, 'ninth login attempt must not pass to the controller');
  assert.equal(blocked.code, 429, 'ninth login attempt must be rate limited');

  const unrelated = createResponse();
  middleware(
    {
      method: 'GET',
      originalUrl: '/api/v1/health',
      headers: { 'x-forwarded-for': '203.0.113.10' },
    },
    unrelated,
    () => {
      passed += 1;
    },
  );
  assert.equal(unrelated.code, 200, 'unrelated endpoints must not be blocked by admin login limits');

  timestamp += 10 * 60 * 1000 + 1;
  const afterWindow = createResponse();
  middleware(
    {
      method: 'POST',
      originalUrl: '/api/v1/admin/login',
      headers: { 'x-forwarded-for': '203.0.113.10' },
    },
    afterWindow,
    () => {
      passed += 1;
    },
  );
  assert.equal(afterWindow.code, 200, 'login attempts must recover after the rate window');

  clearGatewayRateLimitBuckets();
  console.log('gateway-rate-limit.spec.ts passed');
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
