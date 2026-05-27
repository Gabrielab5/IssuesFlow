import client from './client';
import type { AttachmentResponse } from '../types/api';

export const attachmentsApi = {
  getByTicketId: async (ticketId: number): Promise<AttachmentResponse[]> => {
    const response = await client.get<AttachmentResponse[]>(`/tickets/${ticketId}/attachments`);
    return response.data;
  },

  upload: async (ticketId: number, file: File): Promise<AttachmentResponse> => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await client.post<AttachmentResponse>(
      `/tickets/${ticketId}/attachments`,
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    );
    return response.data;
  },

  delete: async (ticketId: number, attachmentId: number): Promise<void> => {
    await client.delete(`/tickets/${ticketId}/attachments/${attachmentId}`);
  },

  download: async (ticketId: number, attachmentId: number): Promise<Blob> => {
    const response = await client.get(
      `/tickets/${ticketId}/attachments/${attachmentId}/download`,
      { responseType: 'blob' }
    );
    return response.data;
  },
};
