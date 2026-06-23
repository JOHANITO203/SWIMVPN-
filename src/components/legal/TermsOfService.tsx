import React from 'react';
import { LegalLayout } from './LegalLayout';
import { OPERATOR } from './operator';

export default function TermsOfService() {
  return (
    <LegalLayout title="Terms of Service">
      <p className="text-lg text-[#3F3C36] mb-8 font-medium">Last Updated: {new Date().toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })}</p>

      <section className="mb-10">
        <h2 className="text-2xl text-[#14130F] mb-4">1. Acceptance of Terms</h2>
        <p className="text-[#3F3C36] leading-relaxed">
          By downloading, installing, or using the SwimVPN+ application and associated services, you agree to be bound by these Terms of Service. If you do not agree to these terms, do not use the service.
        </p>
      </section>

      <section className="mb-10">
        <h2 className="text-2xl text-[#14130F] mb-4">2. Freemium Model & Premium Access</h2>
        <p className="text-[#3F3C36] mb-4 leading-relaxed">
          SwimVPN+ operates on a freemium model. You are entitled to use the application and its free-tier features without charge, indefinitely. We do not force purchases, nor do we lock you out of the application entirely if a trial or subscription expires.
        </p>
        <p className="text-[#3F3C36] leading-relaxed">
          Premium nodes and higher bandwidth limits require an active subscription or trial. Once a premium subscription or trial expires, your access will automatically revert to the free-tier limitations until you choose to renew.
        </p>
      </section>

      <section className="mb-10">
        <h2 className="text-2xl text-[#14130F] mb-4">3. Fair Usage & Abuse Prevention</h2>
        <p className="text-[#3F3C36] mb-4 leading-relaxed">
          We offer a one-time 3-day trial for new users to test our premium infrastructure. To prevent abuse, we use device and contact metadata for trial validation. Attempting to bypass trial restrictions via spoofing, emulators, or reverse engineering violates these terms.
        </p>
        <p className="text-[#3F3C36] leading-relaxed">
          SwimVPN+ is designed for personal privacy and security. You agree not to use our infrastructure for:
        </p>
        <ul className="list-disc pl-6 text-[#3F3C36] mt-4 space-y-2 marker:text-[#7B57E8]">
          <li>Distributing malware, viruses, or participating in DDoS attacks.</li>
          <li>Engaging in illegal activities under your local jurisdiction or international law.</li>
          <li>Automated scraping or massive bandwidth exploitation that degrades server health for other users.</li>
        </ul>
      </section>

      <section className="mb-10">
        <h2 className="text-2xl text-[#14130F] mb-4">4. Limitation of Liability</h2>
        <p className="text-[#3F3C36] leading-relaxed">
          SwimVPN+ provides a secure routing protocol "as-is". While we utilize advanced encryption and resilient networking techniques, we cannot guarantee 100% uptime or invulnerability against highly sophisticated state-level blockades. We are not liable for any direct or indirect damages, data loss, or connectivity issues resulting from the use of our software.
        </p>
      </section>

      <section className="mb-10">
        <h2 className="text-2xl text-[#14130F] mb-4">5. Payments &amp; Refunds</h2>
        <p className="text-[#3F3C36] leading-relaxed">
          Premium access is sold as a digital service delivered immediately after payment. Refunds and
          subscription cancellations are governed by our <a href="#refund">Refund &amp; Cancellation Policy</a>,
          which provides a 48-hour refund if the service does not work for you.
        </p>
      </section>

      <section className="mb-10">
        <h2 className="text-2xl text-[#14130F] mb-4">6. Operator &amp; Governing Law</h2>
        <p className="text-[#3F3C36] leading-relaxed">
          SWIMVPN is operated by {OPERATOR.name}, {OPERATOR.type}, established in {OPERATOR.country}. These Terms
          are governed by the laws of {OPERATOR.country}. For any question, contact{' '}
          <a href={`mailto:${OPERATOR.supportEmail}`}>{OPERATOR.supportEmail}</a>.
        </p>
      </section>

      <section className="mb-10">
        <h2 className="text-2xl text-[#14130F] mb-4">7. Amendments</h2>
        <p className="text-[#3F3C36] leading-relaxed">
          We reserve the right to update these terms at any time. Changes will be reflected on this page. Continued use of SwimVPN+ following modifications indicates your acceptance of the updated terms.
        </p>
      </section>
    </LegalLayout>
  );
}
