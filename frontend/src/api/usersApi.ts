import client from './client';
import type {
  UserResponse,
  CreateUserRequest,
  UpdateUserRequest,
  PagedResponseMentionResponse
} from '../types/api';

export const usersApi = {
  getAll: async (): Promise<UserResponse[]> => {
    const response = await client.get<UserResponse[]>('/users');
    return response.data;
  },

  getById: async (userId: number): Promise<UserResponse> => {
    const response = await client.get<UserResponse>(`/users/${userId}`);
    return response.data;
  },

  create: async (data: CreateUserRequest): Promise<UserResponse> => {
    const response = await client.post<UserResponse>('/users', data);
    return response.data;
  },

  update: async (userId: number, data: UpdateUserRequest): Promise<void> => {
    await client.post(`/users/update/${userId}`, data);
  },

  delete: async (userId: number): Promise<void> => {
    await client.delete(`/users/${userId}`);
  },

  getMentions: async (
    userId: number,
    page: number = 1,
    pageSize: number = 20
  ): Promise<PagedResponseMentionResponse> => {
    const response = await client.get<PagedResponseMentionResponse>(
      `/users/${userId}/mentions`,
      { params: { page, pageSize } }
    );
    return response.data;
  },
};
