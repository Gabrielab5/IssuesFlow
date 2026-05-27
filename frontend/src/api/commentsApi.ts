import client from './client';
import type {
  CommentResponse,
  CreateCommentRequest,
  UpdateCommentRequest
} from '../types/api';

export const commentsApi = {
  getByTicketId: async (ticketId: number): Promise<CommentResponse[]> => {
    const response = await client.get<CommentResponse[]>(`/tickets/${ticketId}/comments`);
    return response.data;
  },

  create: async (ticketId: number, data: CreateCommentRequest): Promise<CommentResponse> => {
    const response = await client.post<CommentResponse>(`/tickets/${ticketId}/comments`, data);
    return response.data;
  },

  update: async (ticketId: number, commentId: number, data: UpdateCommentRequest): Promise<CommentResponse> => {
    const response = await client.patch<CommentResponse>(
      `/tickets/${ticketId}/comments/${commentId}`,
      data
    );
    return response.data;
  },

  delete: async (ticketId: number, commentId: number): Promise<void> => {
    await client.delete(`/tickets/${ticketId}/comments/${commentId}`);
  },
};
