import client from './client';
import type {
  TicketResponse,
  CreateTicketRequest,
  UpdateTicketRequest,
  ImportResult
} from '../types/api';

export const ticketsApi = {
  getAll: async (projectId: number): Promise<TicketResponse[]> => {
    const response = await client.get<TicketResponse[]>('/tickets', {
      params: { projectId }
    });
    return response.data;
  },

  getById: async (ticketId: number): Promise<TicketResponse> => {
    const response = await client.get<TicketResponse>(`/tickets/${ticketId}`);
    return response.data;
  },

  create: async (data: CreateTicketRequest): Promise<TicketResponse> => {
    const response = await client.post<TicketResponse>('/tickets', data);
    return response.data;
  },

  update: async (ticketId: number, data: UpdateTicketRequest): Promise<TicketResponse> => {
    const response = await client.patch<TicketResponse>(`/tickets/${ticketId}`, data);
    return response.data;
  },

  delete: async (ticketId: number): Promise<void> => {
    await client.delete(`/tickets/${ticketId}`);
  },

  exportCsv: async (projectId: number): Promise<Blob> => {
    const response = await client.get('/tickets/export', {
      params: { projectId },
      responseType: 'blob'
    });
    return response.data;
  },

  importCsv: async (projectId: number, file: File): Promise<ImportResult> => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await client.post<ImportResult>('/tickets/import', formData, {
      params: { projectId },
      headers: { 'Content-Type': 'multipart/form-data' }
    });
    return response.data;
  },
};
