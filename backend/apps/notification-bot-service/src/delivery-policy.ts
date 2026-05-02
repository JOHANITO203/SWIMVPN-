export function buildActiveDeliverableAssignmentInclude() {
  return {
    where: {
      access_status: 'ACTIVE' as const,
      inventory_item_id: { not: null },
    },
    orderBy: { assigned_at: 'desc' as const },
    take: 1,
    include: { inventory_item: true },
  };
}

export function selectLatestDeliverableAssignment(assignments: any[]) {
  return assignments
    .filter((assignment) =>
      assignment?.access_status === 'ACTIVE' &&
      assignment?.inventory_item?.raw_config,
    )
    .sort((left, right) =>
      new Date(right.assigned_at || 0).getTime() - new Date(left.assigned_at || 0).getTime(),
    )[0];
}

export function parseDeliveryNotes(notes?: string | null) {
  if (!notes) return null;

  try {
    const parsed = JSON.parse(notes);
    if (!parsed || typeof parsed !== 'object') return null;

    return {
      status: typeof parsed.status === 'string' ? parsed.status : null,
      error: typeof parsed.error === 'string' ? parsed.error : null,
      updatedAt: typeof parsed.updatedAt === 'string' ? parsed.updatedAt : null,
    };
  } catch {
    return null;
  }
}
