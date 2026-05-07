type GatewayRequest = {
  method?: string;
  originalUrl?: string;
  path?: string;
  ip?: string;
  headers?: Record<string, string | string[] | undefined>;
  socket?: { remoteAddress?: string };
};

type GatewayResponse = {
  status: (code: number) => GatewayResponse;
  json: (body: unknown) => void;
};

type GatewayNext = () => void;

type Rule = {
  method: string;
  pattern: RegExp;
  windowMs: number;
  max: number;
  name: string;
};

const RULES: Rule[] = [
  {
    method: 'POST',
    pattern: /^\/api\/v1\/admin\/login\/?$/,
    windowMs: 10 * 60 * 1000,
    max: 8,
    name: 'admin_login',
  },
  {
    method: 'GET',
    pattern: /^\/api\/v1\/access\/[^/]+\/?$/,
    windowMs: 5 * 60 * 1000,
    max: 30,
    name: 'access_profile',
  },
  {
    method: 'POST',
    pattern: /^\/api\/v1\/subscription\/resolve-crypt\/?$/,
    windowMs: 5 * 60 * 1000,
    max: 20,
    name: 'crypt_resolve',
  },
];

const buckets = new Map<string, number[]>();

export function createGatewayRateLimitMiddleware(now = () => Date.now()) {
  return (req: GatewayRequest, res: GatewayResponse, next: GatewayNext) => {
    const rule = findRule(req);
    if (!rule) {
      next();
      return;
    }

    const key = `${rule.name}:${clientKey(req)}`;
    const current = now();
    const hits = (buckets.get(key) || []).filter((timestamp) => current - timestamp < rule.windowMs);

    if (hits.length >= rule.max) {
      buckets.set(key, hits);
      res.status(429).json({
        statusCode: 429,
        message: 'Too many requests. Please try again later.',
      });
      return;
    }

    hits.push(current);
    buckets.set(key, hits);
    next();
  };
}

export function clearGatewayRateLimitBuckets() {
  buckets.clear();
}

function findRule(req: GatewayRequest) {
  const method = (req.method || '').toUpperCase();
  const path = normalizePath(req.originalUrl || req.path || '');

  return RULES.find((rule) => rule.method === method && rule.pattern.test(path));
}

function normalizePath(value: string) {
  const withoutQuery = value.split('?')[0] || '/';
  return withoutQuery.startsWith('/') ? withoutQuery : `/${withoutQuery}`;
}

function clientKey(req: GatewayRequest) {
  const forwarded = firstHeader(req.headers?.['x-forwarded-for']);
  const realIp = firstHeader(req.headers?.['x-real-ip']);
  return (forwarded?.split(',')[0] || realIp || req.ip || req.socket?.remoteAddress || 'unknown').trim();
}

function firstHeader(value: string | string[] | undefined) {
  if (Array.isArray(value)) {
    return value[0];
  }

  return value;
}
