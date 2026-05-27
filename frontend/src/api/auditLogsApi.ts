import client from './client';
import type { PagedResponseAuditLogResponse } from '../types/api';

export interface AuditLogFilters {
  entityType?: string;
  entityId?: number;
  action?: string;
  actor?: string;
  performedBy?: number;
  from?: string;
  to?: string;
  page?: number;
  pageSize?: number;
}

export const auditLogsApi = {
  getAll: async (filters?: AuditLogFilters): Promise<PagedResponseAuditLogResponse> => {
    const response = await client.get<PagedResponseAuditLogResponse>('/audit-logs', {
      params: filters
    });
    return response.data;
  },
};
