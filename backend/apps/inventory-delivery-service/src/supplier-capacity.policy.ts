export type SupplierAllocatableHealth =
  | 'HEALTHY'
  | 'DEGRADED'
  | 'FULL'
  | 'EXPIRED'
  | 'DISABLED';

export function canAllocateSupplierConfig(input: {
  healthStatus: SupplierAllocatableHealth;
  usedResaleSlots: number;
  maxResaleSlots: number;
  requiredSlots: number;
}) {
  if (input.healthStatus !== 'HEALTHY') {
    return false;
  }

  if (input.requiredSlots <= 0 || input.maxResaleSlots <= 0) {
    return false;
  }

  return input.usedResaleSlots + input.requiredSlots <= input.maxResaleSlots;
}
