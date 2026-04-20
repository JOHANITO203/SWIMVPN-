/**
 * Shared Data Models for SWIMVPN+
 */

export interface UserAccess {
  id: string;
  email: string | null;
  userNumber: string;
  planType: 'TRIAL' | 'PREMIUM';
  status: 'ACTIVE' | 'EXPIRED' | 'PENDING';
  trialStartedAt: string | null;
  trialExpiresAt: string | null;
  subscriptionExpiresAt: string | null;
  devicesAllowed: number;
  subscriptionUrl: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Plan {
  id: string;
  name: string;
  durationDays: number;
  devicesAllowed: number;
  features: string[];
  isTrial: boolean;
  isActive: boolean;
  price?: number;
  currency?: string;
}

export interface ServerNode {
  id: string;
  country: string;
  city: string;
  host: string;
  port: number;
  protocol: 'VLESS' | 'VMESS' | 'SHADOWSOCKS' | 'TROJAN';
  tags: string[];
  isActive: boolean;
  latency?: number;
}

export interface PaymentOrder {
  id: string;
  userAccessId: string;
  planId: string;
  amount: number;
  currency: string;
  status: 'PENDING' | 'PAID' | 'FAILED';
  provider: string;
  paymentUrl: string;
  createdAt: string;
  paidAt: string | null;
}
