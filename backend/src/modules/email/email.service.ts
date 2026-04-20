import { Injectable, Logger } from '@nestjs/common';

@Injectable()
export class EmailService {
  private readonly logger = new Logger(EmailService.name);

  // Dans un cas réel, vous utiliseriez Nodemailer ou un SDK (SendGrid, Mailgun)
  // constructor() { ... initialisation du transporteur ... }

  async sendActivationEmail(
    to: string,
    planName: string,
    expirationDate: Date,
    userNumber: string,
    subscriptionUrl: string
  ) {
    this.logger.log(`Mocking Email Sending to: ${to}`);

    const emailBody = `
      <h1>Merci pour votre achat !</h1>
      <p>Votre forfait <strong>${planName}</strong> a été activé avec succès.</p>
      <p>Date d'expiration : ${expirationDate.toLocaleDateString()}</p>
      <p>Votre numéro d'utilisateur (à conserver pour le support) : <strong>${userNumber}</strong></p>
      <hr />
      <h2>Comment activer votre accès VPN :</h2>
      <ol>
        <li>Copiez le lien suivant : <code>${subscriptionUrl || 'Lien à générer ou disponible dans l\'app'}</code></li>
        <li>Ouvrez l'application SWIMVPN+</li>
        <li>Allez dans "Importer un accès" et collez le lien.</li>
      </ol>
      <p>Si vous avez déjà l'application ouverte, votre accès a été automatiquement mis à jour !</p>
    `;

    // mock d'envoi
    this.logger.debug('Email Content:', emailBody);

    return true;
  }
}
