import client from './client';
import type {
  LoginRequest,
  AuthTokenResponse,
  CurrentUserResponse
} from '../types/api';

export const authApi = {
  login: async (credentials: LoginRequest): Promise<AuthTokenResponse> => {
    const response = await client.post<AuthTokenResponse>('/auth/login', credentials);
    return response.data;
  },

  logout: async (): Promise<void> => {
    await client.post('/auth/logout');
  },

  getCurrentUser: async (): Promise<CurrentUserResponse> => {
    const response = await client.get<CurrentUserResponse>('/auth/me');
    return response.data;
  },
};
