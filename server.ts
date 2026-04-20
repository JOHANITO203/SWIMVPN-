import express from 'express';
import cors from 'cors';
import { createServer as createViteServer } from 'vite';
import path from 'path';
import { fileURLToPath } from 'url';
import { UserAccess, ServerNode, Plan } from './src/types';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

async function startServer() {
  const app = express();
  const PORT = 3000;

  app.use(cors());
  app.use(express.json());

  // In-memory "database" for V1
  const users: Map<string, UserAccess> = new Map();
  const servers: ServerNode[] = [
    { id: '1', country: 'Germany', city: 'Frankfurt', host: 'de1.swimvpn.com', port: 443, protocol: 'VLESS', tags: ['Fast', 'Stable', 'Recommended'], isActive: true },
    { id: '2', country: 'Netherlands', city: 'Amsterdam', host: 'nl1.swimvpn.com', port: 443, protocol: 'VLESS', tags: ['Streaming', 'Gaming'], isActive: true },
    { id: '3', country: 'Finland', city: 'Helsinki', host: 'fi1.swimvpn.com', port: 443, protocol: 'VLESS', tags: ['Stable'], isActive: true },
    { id: '4', country: 'Kazakhstan', city: 'Almaty', host: 'kz1.swimvpn.com', port: 443, protocol: 'VLESS', tags: ['Regional'], isActive: true },
  ];

  const plans: Plan[] = [
    { id: 'trial', name: '7 Days Trial', durationDays: 7, devicesAllowed: 1, features: ['All Servers', 'High Speed'], isTrial: true, isActive: true },
    { id: 'premium_month', name: 'Premium Monthly', durationDays: 30, devicesAllowed: 3, features: ['All Servers', 'High Speed', 'Multi-device'], isTrial: false, isActive: true, price: 500, currency: 'RUB' },
  ];

  // Helper to generate user number
  const generateUserNumber = () => Math.random().toString(36).substring(2, 10).toUpperCase();

  // API Routes
  app.post('/api/trial/start', (req, res) => {
    const { deviceId } = req.body;
    // For V1, we'll just create a new user every time for simplicity in preview
    const userNumber = generateUserNumber();
    const now = new Date();
    const expires = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000);

    const newUser: UserAccess = {
      id: Math.random().toString(36).substr(2, 9),
      email: null,
      userNumber,
      planType: 'TRIAL',
      status: 'ACTIVE',
      trialStartedAt: now.toISOString(),
      trialExpiresAt: expires.toISOString(),
      subscriptionExpiresAt: expires.toISOString(),
      devicesAllowed: 1,
      subscriptionUrl: `swimvpn://activate?code=${userNumber}`,
      createdAt: now.toISOString(),
      updatedAt: now.toISOString()
    };

    users.set(userNumber, newUser);
    res.json({
      userNumber,
      planType: newUser.planType,
      trialExpiresAt: newUser.trialExpiresAt,
      subscriptionStatus: newUser.status,
      subscriptionUrl: newUser.subscriptionUrl,
      availableServers: servers
    });
  });

  app.post('/api/subscription/import', (req, res) => {
    const { userNumber, subscriptionUrl } = req.body;
    // In V1, we just simulate activation
    const user = users.get(userNumber);
    if (!user) return res.status(404).json({ error: 'User not found' });

    user.status = 'ACTIVE';
    user.planType = 'PREMIUM';
    const now = new Date();
    user.subscriptionExpiresAt = new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000).toISOString();
    user.updatedAt = now.toISOString();

    res.json({
      status: user.status,
      expiresAt: user.subscriptionExpiresAt,
      availableServers: servers
    });
  });

  app.get('/api/access/:userNumber', (req, res) => {
    const { userNumber } = req.params;
    const user = users.get(userNumber);
    if (!user) return res.status(404).json({ error: 'User not found' });
    res.json(user);
  });

  app.get('/api/servers', (req, res) => {
    const { userNumber } = req.query;
    // Filter servers based on user access if needed
    res.json(servers);
  });

  app.get('/api/app/content/:type', (req, res) => {
    const { type } = req.params;
    if (type === 'onboarding') {
      res.json([
        { title: 'Welcome to SWIMVPN+', description: 'Fast and secure access to your favorite services.' },
        { title: '7 Days Free', description: 'Start your trial and explore the web without limits.' },
        { title: 'Easy Import', description: 'Just paste a link or scan a QR code to activate.' }
      ]);
    } else if (type === 'support') {
      res.json({
        faq: [
          { q: 'How to connect?', a: 'Just tap the big button on the Home screen.' },
          { q: 'How to renew?', a: 'Go to Profile and tap Renew.' }
        ],
        telegram: '@SwimVpnSupport',
        email: 'support@swimvpn.com'
      });
    } else {
      res.json({});
    }
  });

  // Vite middleware setup
  if (process.env.NODE_ENV !== 'production') {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa'
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), 'dist');
    app.use(express.static(distPath));
    app.get('*', (req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`Server running on http://localhost:${PORT}`);
  });
}

startServer();
