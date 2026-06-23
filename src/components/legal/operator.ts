// Single source of truth for the legal operator identity shown on the legal pages.
//
// ⚠️ `country` MUST match the country declared on the payment-processor (2Checkout)
// merchant account AND the operator's government ID. Confirm France vs Côte d'Ivoire
// before submitting the 2Checkout application, then update this one constant.
export const OPERATOR = {
  name: 'Johane Arthur Oyaraht',
  type: 'an individual sole operator',
  country: "Côte d'Ivoire",
  supportEmail: 'support@swimvpn.pro',
  privacyEmail: 'privacy@swimvpn.pro',
} as const;
