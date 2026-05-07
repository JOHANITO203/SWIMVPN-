import React from 'react';
import { LegalLayout } from './LegalLayout';

export default function PrivacyPolicy() {
  return (
    <LegalLayout title="Privacy Policy">
      <p className="text-lg text-slate-400 mb-8 font-medium">Last Updated: {new Date().toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })}</p>

      <section className="mb-10">
        <h2 className="text-2xl text-white mb-4">1. Strict No-Logs Policy</h2>
        <p className="text-slate-400 mb-4 leading-relaxed">
          At SwimVPN+, your privacy is our primary engineering directive. We operate a strict zero-logs policy for your network activity. When you connect to our VPN servers, we <strong>do not store or monitor</strong> any of the following:
        </p>
        <ul className="list-disc pl-6 text-slate-400 mb-4 space-y-2 marker:text-cyan-500">
          <li>Your browsing history.</li>
          <li>Your traffic destinations or accessed websites.</li>
          <li>The content of your data.</li>
          <li>Your DNS queries.</li>
          <li>Your originating IP address.</li>
        </ul>
        <p className="text-slate-400 leading-relaxed">
          Our backend infrastructure is built to route traffic ephemerally. What you do through the tunnel remains completely unknown to us.
        </p>
      </section>

      <section className="mb-10">
        <h2 className="text-2xl text-white mb-4">2. Device Fingerprinting & Trial Management</h2>
        <p className="text-slate-400 mb-4 leading-relaxed">
          SwimVPN+ offers an initial 3-day trial. To prevent abuse of this trial system and ensure fair network access, the Android app sends the device identifier provided by Android for trial validation and account continuity.
        </p>
        <p className="text-slate-400 mb-4 leading-relaxed">
          This identifier is used to answer limited operational questions such as: <em>"Has this device already consumed a trial?"</em> and <em>"Is this device authorized to access this assigned premium configuration?"</em> It is not displayed publicly and is not used to monitor browsing activity or VPN traffic destinations.
        </p>
        <p className="text-slate-400 leading-relaxed">
          We do not collect your name, physical address, or identity documents. If you choose to provide an email address or phone number for account recovery or payment receipts, it is kept heavily secured and completely decoupled from your VPN traffic data.
        </p>
      </section>

      <section className="mb-10">
        <h2 className="text-2xl text-white mb-4">3. Freemium Autonomy</h2>
        <p className="text-slate-400 leading-relaxed">
          We believe in unrestricted access to information. SwimVPN+ provides a free tier alongside our premium nodes. You are never forced to purchase a subscription to use the core application. Your data is not sold to advertisers, regardless of whether you are a free or premium user. Our revenue is generated strictly through voluntary premium subscriptions.
        </p>
      </section>

      <section className="mb-10">
        <h2 className="text-2xl text-white mb-4">4. Third-Party Services</h2>
        <p className="text-slate-400 leading-relaxed">
          Payments are handled by secure, external payment gateways (such as Crypto payment providers or localized card processors). We do not store your full credit card numbers or wallet private keys on our servers. Payment status is only tracked via anonymized order reference IDs.
        </p>
      </section>

      <section className="mb-10">
        <h2 className="text-2xl text-white mb-4">5. Contact</h2>
        <p className="text-slate-400 leading-relaxed">
          For any privacy-related inquiries or data deletion requests regarding your account metadata, contact our security team at <a href="mailto:privacy@swimvpn.com">privacy@swimvpn.com</a>.
        </p>
      </section>
    </LegalLayout>
  );
}
