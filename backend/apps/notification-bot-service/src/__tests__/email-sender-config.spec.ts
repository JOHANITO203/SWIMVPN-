import { EmailSenderService } from '../email-sender.service';

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

function createConfig(values: Record<string, string | undefined>) {
  return {
    get<T = string>(key: string, defaultValue?: T): T | undefined {
      return (values[key] as T | undefined) ?? defaultValue;
    },
  } as any;
}

const missing = new EmailSenderService(createConfig({}));
const missingStatus = missing.getTransportStatus();
assert(missingStatus.apiKeyConfigured === false, 'missing API key must report unconfigured mailer');
assert(missingStatus.ready === false, 'missing API key must not be ready');
assert(missingStatus.provider === 'resend', 'mailer provider must be resend');
assert(missingStatus.fromEmail === 'support@swimvpn.pro', 'default from email mismatch');
assert(missingStatus.fromEmailPresent === true, 'default from email must be present');
assert(missingStatus.fromEmailLooksValid === true, 'default from email must look valid');

const invalidFrom = new EmailSenderService(createConfig({
  RESEND_API_KEY: 'rs_test_key',
  MAILER_FROM_EMAIL: 'not-an-email',
}));
const invalidFromStatus = invalidFrom.getTransportStatus();
assert(invalidFromStatus.apiKeyConfigured === true, 'API key must be detected');
assert(invalidFromStatus.fromEmailLooksValid === false, 'invalid from email must be visible in diagnostics');
assert(invalidFromStatus.ready === false, 'invalid from email must not report ready mailer');

const configured = new EmailSenderService(createConfig({
  RESEND_API_KEY: '  rs_test_key  ',
  MAILER_FROM_EMAIL: 'billing@swimvpn.pro',
  MAILER_FROM_NAME: 'SWIMVPN Billing',
}));
const configuredStatus = configured.getTransportStatus();
assert(configuredStatus.apiKeyConfigured === true, 'trimmed API key must report configured mailer');
assert(configuredStatus.ready === true, 'valid API key and sender must report ready mailer');
assert(configuredStatus.fromEmail === 'billing@swimvpn.pro', 'configured from email mismatch');
assert(configuredStatus.fromName === 'SWIMVPN Billing', 'configured from name mismatch');

console.log('email sender config tests passed');
