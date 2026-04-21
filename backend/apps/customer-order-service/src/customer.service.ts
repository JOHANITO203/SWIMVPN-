import { Injectable, Inject } from '@nestjs/common';
import { ClientProxy } from '@nestjs/microservices';
import { PrismaService } from '@app/database';
import { firstValueFrom } from 'rxjs';
import { OrderStatus } from '@prisma/client';
import { StartTrialDto, CreateOrderDto } from '@app/contracts';

@Injectable()
export class CustomerService {
  constructor(
    private readonly prisma: PrismaService,
    @Inject('INVENTORY_SERVICE') private readonly inventoryClient: ClientProxy,
  ) {}

  async createOrder(data: CreateOrderDto) {
    let customer = await this.prisma.customer.findFirst({
      where: {
        OR: [
          { email: data.email || undefined },
          { phone: data.phone || undefined },
        ].filter(Boolean),
      },
    });

    if (!customer) {
      customer = await this.prisma.customer.create({
        data: {
          email: data.email,
          phone: data.phone,
        },
      });
    }

    const orderRef = `ORD-${Date.now()}-${Math.floor(Math.random() * 1000)}`;
    const order = await this.prisma.order.create({
      data: {
        order_ref: orderRef,
        customer_id: customer.id,
        plan_id: data.planId,
        amount_rub: data.amountRub,
        status: OrderStatus.PENDING,
      },
    });

    return order;
  }

  async startTrial(data: StartTrialDto) {
    // 1. Find or create customer by deviceId
    let customer = await this.prisma.customer.findUnique({
      where: { device_id: data.deviceId },
    });

    if (!customer) {
      customer = await this.prisma.customer.create({
        data: {
          device_id: data.deviceId,
        },
      });
    }

    // 2. Check if already has a trial
    const existingTrial = await this.prisma.order.findFirst({
      where: {
        customer_id: customer.id,
        plan: { code: 'WEEK' }, // Assuming WEEK is used for trials or we have a specific one
      },
    });

    if (existingTrial) {
      return this.getProfile(customer.public_id);
    }

    // 3. Find "WEEK" plan (serving as trial for now)
    const trialPlan = await this.prisma.plan.findFirst({
      where: { code: 'WEEK' },
    });

    if (!trialPlan) {
      throw new Error('Trial plan not configured in database');
    }

    // 4. Create a pre-paid order for the trial
    const order = await this.prisma.order.create({
      data: {
        order_ref: `TRIAL-${customer.public_id}`,
        customer_id: customer.id,
        plan_id: trialPlan.id,
        amount_rub: 0,
        status: OrderStatus.PENDING,
      },
    });

    // 5. Trigger fulfillment
    try {
      await firstValueFrom(
        this.inventoryClient.send({ cmd: 'fulfill_order' }, { orderId: order.id }),
      );
    } catch (e) {
      console.error('Fulfillment failed during trial start:', e);
      // In production, we'd handle this with a retry queue
    }

    return this.getProfile(customer.public_id);
  }

  async getProfile(userNumber: string) {
    const customer = await this.prisma.customer.findUnique({
      where: { public_id: userNumber },
      include: {
        orders: {
          where: { status: OrderStatus.FULFILLED },
          orderBy: { created_at: 'desc' },
          take: 1,
          include: {
            plan: true,
            assignments: {
              include: {
                inventory_item: true,
              },
            },
          },
        },
      },
    });

    if (!customer) {
      throw new Error('Customer not found');
    }

    const latestOrder = customer.orders[0];
    const assignment = latestOrder?.assignments[0];
    const inventoryItem = assignment?.inventory_item;

    return {
      userNumber: customer.public_id,
      email: customer.email,
      planType: latestOrder?.plan.code || 'NONE',
      status: latestOrder ? 'ACTIVE' : 'INACTIVE',
      trialStartedAt: latestOrder?.created_at.toISOString() || null,
      trialExpiresAt: null, // Logic to be refined based on plan duration
      subscriptionExpiresAt: latestOrder?.fulfilled_at ? new Date(latestOrder.fulfilled_at.getTime() + 7 * 24 * 60 * 60 * 1000).toISOString() : null,
      subscriptionUrl: inventoryItem?.raw_config || null,
      devicesAllowed: 1,
      dataLimitGB: latestOrder ? parseInt(latestOrder.plan.quota_label) : 0,
      dataUsedBytes: "0",
    };
  }

  async importSubscription(data: { userNumber: string; subscriptionUrl: string }) {
    const customer = await this.prisma.customer.findUnique({
      where: { public_id: data.userNumber },
    });

    if (!customer) {
      throw new Error('Customer not found');
    }

    // In MVP, "import" means manually setting the raw_config for the customer's current valid assignment.
    // Or creating a manual order/assignment if one doesn't exist.
    // For now, let's find their latest fulfilled order and update the raw_config.

    const latestOrder = await this.prisma.order.findFirst({
      where: {
        customer_id: customer.id,
        status: OrderStatus.FULFILLED,
      },
      orderBy: { created_at: 'desc' },
      include: { assignments: true },
    });

    if (latestOrder && latestOrder.assignments.length > 0) {
      const assignment = latestOrder.assignments[0];
      await this.prisma.inventoryItem.update({
        where: { id: assignment.inventory_item_id },
        data: { raw_config: data.subscriptionUrl },
      });
    } else {
      // If no order exists, this is a "manual import" which might need a different handling in the future.
      // For now, we'll just log it or return the existing profile.
      console.log(`Manual config import for ${customer.public_id}: ${data.subscriptionUrl}`);
    }

    return this.getProfile(customer.public_id);
  }

  async activateCode(data: { userNumber: string; code: string }) {
    // 1. Find the coupon/code in the database
    // For MVP, we'll assume any code starting with "SWIM-" is valid for a MONTH plan
    if (!data.code.startsWith('SWIM-')) {
      throw new Error('Invalid coupon code');
    }

    const customer = await this.prisma.customer.findUnique({
      where: { public_id: data.userNumber },
    });

    if (!customer) throw new Error('Customer not found');

    const plan = await this.prisma.plan.findFirst({
      where: { code: 'MONTH' },
    });

    if (!plan) throw new Error('Plan not found');

    // 2. Create a PAID order
    const order = await this.prisma.order.create({
      data: {
        order_ref: `CODE-${data.code}-${Date.now()}`,
        customer_id: customer.id,
        plan_id: plan.id,
        amount_rub: 0,
        status: OrderStatus.PAID,
        payment_ref: `COUPON:${data.code}`,
      },
    });

    // 3. Fulfill
    try {
      await firstValueFrom(
        this.inventoryClient.send({ cmd: 'fulfill_order' }, { orderId: order.id }),
      );
    } catch (e) {
      console.error('Fulfillment failed during code activation:', e);
    }

    return this.getProfile(customer.public_id);
  }

  async handleStripeWebhook(data: any) {
    // Basic Stripe Webhook Logic
    // In production: verify signature
    if (data.type === 'checkout.session.completed') {
      const session = data.data.object;
      const orderRef = session.client_reference_id;
      return this.fulfillOrderByRef(orderRef, session.id);
    }
    return { received: true };
  }

  async handleYookassaWebhook(data: any) {
    // Basic YooKassa Webhook Logic
    if (data.event === 'payment.succeeded') {
      const payment = data.object;
      const orderRef = payment.metadata?.order_ref;
      return this.fulfillOrderByRef(orderRef, payment.id);
    }
    return { received: true };
  }

  private async fulfillOrderByRef(orderRef: string, paymentRef: string) {
    if (!orderRef) return { success: false, error: 'No order ref' };

    const order = await this.prisma.order.findUnique({
      where: { order_ref: orderRef },
    });

    if (!order) return { success: false, error: 'Order not found' };
    if (order.status !== OrderStatus.PENDING) return { success: true, alreadyProcessed: true };

    // 1. Mark as PAID
    await this.prisma.order.update({
      where: { id: order.id },
      data: {
        status: OrderStatus.PAID,
        paid_at: new Date(),
        payment_ref: paymentRef,
      },
    });

    // 2. Trigger fulfillment via Inventory Service
    try {
      await firstValueFrom(
        this.inventoryClient.send({ cmd: 'fulfill_order' }, { orderId: order.id }),
      );
      return { success: true };
    } catch (e) {
      console.error(`Fulfillment failed for order ${order.id}:`, e);
      return { success: false, error: 'Fulfillment triggered but failed' };
    }
  }
}