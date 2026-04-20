import { Injectable, NotFoundException, BadRequestException } from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service';
import { CreatePurchaseDto } from './dto/create-purchase.dto';

@Injectable()
export class PaymentService {
  constructor(private prisma: PrismaService) {}

  async createPurchase(dto: CreatePurchaseDto) {
    // 1. Vérifier l'utilisateur
    const user = await this.prisma.userAccess.findUnique({
      where: { userNumber: dto.userNumber }
    });

    if (!user) throw new NotFoundException('User not found');

    // Mettre à jour l'email de l'utilisateur si c'est la première fois ou s'il a changé
    if (user.email !== dto.email) {
       await this.prisma.userAccess.update({
         where: { id: user.id },
         data: { email: dto.email }
       });
    }

    // 2. Vérifier le plan
    const plan = await this.prisma.plan.findUnique({
      where: { id: dto.planId }
    });

    if (!plan || !plan.isActive || plan.isTrial) {
      throw new BadRequestException('Invalid plan selected');
    }

    // 3. Créer la commande (Order)
    // NB: 'price' n'existe pas dans le modèle Plan initial, on simule un montant fixe ou on le rajoute au modèle.
    // Pour rester fidèle au modèle initial, on va supposer que le montant est fixé par le backend ou récupéré ailleurs.
    // Idéalement, le modèle Plan devrait avoir un champ `price`. Faisons l'hypothèse d'un prix par défaut pour l'exemple.
    const mockAmount = plan.durationDays * 10; // Exemple: 10 RUB par jour

    const order = await this.prisma.paymentOrder.create({
      data: {
        userAccessId: user.id,
        planId: plan.id,
        amount: mockAmount,
        currency: 'RUB',
        provider: 'YooKassa', // Provider par défaut pour la Russie
        status: 'PENDING',
        paymentUrl: `https://mock-payment-gateway.ru/pay/${Math.random().toString(36).substring(7)}` // Mock URL
      }
    });

    return {
      orderId: order.id,
      paymentUrl: order.paymentUrl,
      amount: order.amount,
      currency: order.currency,
      status: order.status
    };
  }

  // Exemple simplifié d'un webhook
  async handleWebhook(payload: any) {
    // 1. Validation de la signature du provider (mockée ici)
    if (!payload || !payload.orderId || payload.status !== 'succeeded') {
       throw new BadRequestException('Invalid webhook payload');
    }

    const order = await this.prisma.paymentOrder.findUnique({
      where: { id: payload.orderId },
      include: { plan: true, user: true }
    });

    if (!order) throw new NotFoundException('Order not found');
    if (order.status === 'PAID') return { status: 'already_paid' };

    const now = new Date();
    const currentExpiration = order.user.subscriptionExpiresAt && order.user.subscriptionExpiresAt > now
      ? order.user.subscriptionExpiresAt
      : now;

    const newExpirationDate = new Date(currentExpiration.getTime() + order.plan.durationDays * 24 * 60 * 60 * 1000);

    // 2. Mettre à jour l'Order et le UserAccess en transaction
    await this.prisma.$transaction([
      this.prisma.paymentOrder.update({
        where: { id: order.id },
        data: {
          status: 'PAID',
          paidAt: now
        }
      }),
      this.prisma.userAccess.update({
        where: { id: order.user.id },
        data: {
          planType: 'PREMIUM',
          subscriptionExpiresAt: newExpirationDate,
          devicesAllowed: order.plan.devicesAllowed
        }
      })
    ]);

    // 3. Déclencher l'envoi de l'email (à implémenter via un EventEmitter ou appel direct au EmailService)
    // this.emailService.sendPaymentSuccessEmail(order.user.email, order.plan.name, newExpirationDate);

    return { status: 'success' };
  }
}
