import { resolveSupportedCurrencies } from '../payment-capability';
import { strict as assert } from 'assert';

assert.deepEqual(resolveSupportedCurrencies(undefined), ['RUB', 'XOF', 'USD']);  // default = SwimPay live rails
assert.deepEqual(resolveSupportedCurrencies('usd, rub ,xof'), ['USD', 'RUB', 'XOF']); // parse/upper/trim/dedupe
assert.deepEqual(resolveSupportedCurrencies('rub,zzz,'), ['RUB']);               // junk dropped, keeps valid
assert.deepEqual(resolveSupportedCurrencies(''), ['RUB', 'XOF', 'USD']);         // empty -> default
console.log('payment-capability OK');
