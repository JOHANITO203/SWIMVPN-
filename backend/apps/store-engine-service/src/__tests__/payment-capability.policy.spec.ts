import { resolveSupportedCurrencies } from '../payment-capability';
import { strict as assert } from 'assert';

assert.deepEqual(resolveSupportedCurrencies(undefined), ['RUB']);                 // default = SwimPay today
assert.deepEqual(resolveSupportedCurrencies('usd, rub ,xof'), ['USD', 'RUB', 'XOF']); // parse/upper/trim/dedupe
assert.deepEqual(resolveSupportedCurrencies('rub,zzz,'), ['RUB']);               // junk dropped
assert.deepEqual(resolveSupportedCurrencies(''), ['RUB']);                       // empty -> default
console.log('payment-capability OK');
