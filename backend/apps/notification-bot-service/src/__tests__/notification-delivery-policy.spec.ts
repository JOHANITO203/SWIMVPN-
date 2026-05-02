import {
  buildActiveDeliverableAssignmentInclude,
  parseDeliveryNotes,
  selectLatestDeliverableAssignment,
} from '../delivery-policy';

function assert(condition: boolean, message: string) {
  if (!condition) {
    throw new Error(message);
  }
}

const orderWithMixedAssignments = {
  order_ref: 'SW12345',
  customer: { email: 'customer@example.com', phone: '+70000000000' },
  plan: { code: 'PREMIUM', name: 'Premium', duration_label: 'month' },
  assignments: [
    {
      access_status: 'REVOKED',
      assigned_at: new Date('2026-01-01T00:00:00Z'),
      inventory_item: { raw_config: 'vless://revoked' },
    },
    {
      access_status: 'ACTIVE',
      assigned_at: new Date('2026-01-02T00:00:00Z'),
      inventory_item: null,
    },
    {
      access_status: 'ACTIVE',
      assigned_at: new Date('2026-01-03T00:00:00Z'),
      inventory_item: { raw_config: 'vless://latest-active' },
    },
  ],
  deliveries: [],
};

const assignment = selectLatestDeliverableAssignment(orderWithMixedAssignments.assignments);
const include = buildActiveDeliverableAssignmentInclude();

  assert(
    assignment.inventory_item.raw_config === 'vless://latest-active',
    'resend must use latest ACTIVE assignment with non-null inventory item',
  );

  assert(
    include.where.access_status === 'ACTIVE',
    'resend query must filter assignments to ACTIVE status',
  );

  assert(
    include.where.inventory_item_id.not === null,
    'resend query must filter out assignments without inventory item',
  );

  assert(
    include.orderBy.assigned_at === 'desc',
    'resend query must order assignments by newest assigned_at first',
  );

const notes = parseDeliveryNotes(JSON.stringify({
  status: 'EMAIL_FAILED',
  error: 'SMTP unavailable',
  updatedAt: '2026-05-02T00:00:00.000Z',
}));

assert(notes?.status === 'EMAIL_FAILED', 'delivery status must expose EMAIL_FAILED from notes');
assert(notes?.error === 'SMTP unavailable', 'delivery status must expose last delivery error');
assert(
  notes?.updatedAt === '2026-05-02T00:00:00.000Z',
  'delivery status must expose notes updatedAt',
);

console.log('notification delivery policy tests passed');
