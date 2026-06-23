import React from 'react';
import { LegalLayout } from './LegalLayout';
import { OPERATOR } from './operator';

export default function RefundPolicy() {
  return (
    <LegalLayout title="Refund & Cancellation Policy">
      <p className="text-lg text-[#3F3C36] mb-8 font-medium">Last Updated: {new Date().toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })}</p>

      <section className="mb-10">
        <h2 className="text-2xl text-[#14130F] mb-4">1. Nature of the Service</h2>
        <p className="text-[#3F3C36] leading-relaxed">
          SWIMVPN is a digital service. Access (your VPN configuration and server access) is delivered
          electronically and is available for use immediately after a successful payment. Because the
          service is delivered instantly, the conditions below apply to refunds.
        </p>
      </section>

      <section className="mb-10">
        <h2 className="text-2xl text-[#14130F] mb-4">2. 48-Hour Refund If the Service Does Not Work</h2>
        <p className="text-[#3F3C36] mb-4 leading-relaxed">
          If the service does not work for you, you may request a full refund within <strong>48 hours</strong> of
          your purchase, provided that:
        </p>
        <ul className="list-disc pl-6 text-[#3F3C36] mb-4 space-y-2 marker:text-[#7B57E8]">
          <li>you contacted our support so we could attempt to resolve the problem, and</li>
          <li>the issue could not be resolved (for example: the tunnel cannot connect on your network and no available server works for you).</li>
        </ul>
        <p className="text-[#3F3C36] leading-relaxed">
          Refunds are issued to the original payment method where technically possible. For cryptocurrency
          payments, refunds are issued in the equivalent amount of the same cryptocurrency.
        </p>
      </section>

      <section className="mb-10">
        <h2 className="text-2xl text-[#14130F] mb-4">3. When a Refund Does Not Apply</h2>
        <ul className="list-disc pl-6 text-[#3F3C36] space-y-2 marker:text-[#7B57E8]">
          <li>Requests made more than 48 hours after purchase.</li>
          <li>Substantial or manifest usage of the service (significant bandwidth consumed or extended active use), which indicates the service worked as intended.</li>
          <li>Change of mind after the service has been used successfully.</li>
          <li>Suspension or termination caused by a violation of our <a href="#terms">Terms of Service</a>.</li>
        </ul>
      </section>

      <section className="mb-10">
        <h2 className="text-2xl text-[#14130F] mb-4">4. Cancellation of Renewals</h2>
        <p className="text-[#3F3C36] leading-relaxed">
          You can stop a subscription from renewing at any time. Cancelling stops future charges; it does not
          retroactively refund the current period once it has been used beyond the 48-hour window described
          above. Your access remains active until the end of the period already paid for.
        </p>
      </section>

      <section className="mb-10">
        <h2 className="text-2xl text-[#14130F] mb-4">5. How to Request a Refund or Cancel</h2>
        <p className="text-[#3F3C36] leading-relaxed">
          Email <a href={`mailto:${OPERATOR.supportEmail}`}>{OPERATOR.supportEmail}</a> within the 48-hour window
          with your order reference and a short description of the problem. We aim to respond within 48 hours.
          The service is operated by {OPERATOR.name}, {OPERATOR.type}.
        </p>
      </section>
    </LegalLayout>
  );
}
