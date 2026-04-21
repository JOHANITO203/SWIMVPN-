import { DeliveryTemplateService } from '../templates/delivery-template.service';

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

const service = new DeliveryTemplateService();

const payload = {
  orderRef: 'SW12345',
  customerEmail: 'customer@example.com',
  customerPhone: '+79990000000',
  planCode: 'MONTH',
  planLabel: '1 Month',
  vpnLink: 'vless://example',
  expiryLabel: '30 days',
};

const ru = service.renderEmail(payload as any, undefined);
assert(ru.language === 'ru', 'Default language must be RU');
assert(ru.subject === '??? ?????? SWIMVPN+', 'RU subject mismatch');

const en = service.renderEmail(payload as any, 'en');
assert(en.language === 'en', 'EN language mismatch');
assert(en.subject === 'Your SWIMVPN+ Access', 'EN subject mismatch');

console.log('notification template tests passed');
