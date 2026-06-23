import React from 'react';
import { LegalLayout } from './LegalLayout';
import { OPERATOR } from './operator';

export default function Contact() {
  return (
    <LegalLayout title="Contact & Operator">
      <section className="mb-10">
        <h2 className="text-2xl text-[#14130F] mb-4">Who operates SWIMVPN</h2>
        <p className="text-[#3F3C36] leading-relaxed">
          SWIMVPN is operated by <strong>{OPERATOR.name}</strong>, {OPERATOR.type}, established in {OPERATOR.country}.
        </p>
      </section>

      <section className="mb-10">
        <h2 className="text-2xl text-[#14130F] mb-4">Customer support</h2>
        <p className="text-[#3F3C36] mb-4 leading-relaxed">
          For help with installation, connectivity, payments, refunds, or any question about your account,
          contact us by email:
        </p>
        <ul className="list-disc pl-6 text-[#3F3C36] space-y-2 marker:text-[#7B57E8]">
          <li>Support: <a href={`mailto:${OPERATOR.supportEmail}`}>{OPERATOR.supportEmail}</a></li>
          <li>Privacy &amp; data requests: <a href={`mailto:${OPERATOR.privacyEmail}`}>{OPERATOR.privacyEmail}</a></li>
        </ul>
        <p className="text-[#3F3C36] mt-4 leading-relaxed">
          We aim to respond to all requests within 48 hours.
        </p>
      </section>

      <section className="mb-10">
        <h2 className="text-2xl text-[#14130F] mb-4">Related documents</h2>
        <ul className="list-disc pl-6 text-[#3F3C36] space-y-2 marker:text-[#7B57E8]">
          <li><a href="#terms">Terms of Service</a></li>
          <li><a href="#privacy">Privacy Policy</a></li>
          <li><a href="#refund">Refund &amp; Cancellation Policy</a></li>
        </ul>
      </section>
    </LegalLayout>
  );
}
