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

  async importSubscription(data: any) {
    return this.getProfile(data.userNumber);
  }

  async activateCode(data: any) {
    return this.getProfile(data.userNumber);
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