import client from './client';
import type {
  ProjectResponse,
  CreateProjectRequest,
  UpdateProjectRequest,
  WorkloadEntry
} from '../types/api';

export const projectsApi = {
  getAll: async (): Promise<ProjectResponse[]> => {
    const response = await client.get<ProjectResponse[]>('/projects');
    return response.data;
  },

  getById: async (projectId: number): Promise<ProjectResponse> => {
    const response = await client.get<ProjectResponse>(`/projects/${projectId}`);
    return response.data;
  },

  create: async (data: CreateProjectRequest): Promise<ProjectResponse> => {
    const response = await client.post<ProjectResponse>('/projects', data);
    return response.data;
  },

  update: async (projectId: number, data: UpdateProjectRequest): Promise<ProjectResponse> => {
    const response = await client.patch<ProjectResponse>(`/projects/${projectId}`, data);
    return response.data;
  },

  delete: async (projectId: number): Promise<void> => {
    await client.delete(`/projects/${projectId}`);
  },

  getDeleted: async (): Promise<ProjectResponse[]> => {
    const response = await client.get<ProjectResponse[]>('/projects/deleted');
    return response.data;
  },

  restore: async (projectId: number): Promise<ProjectResponse> => {
    const response = await client.post<ProjectResponse>(`/projects/${projectId}/restore`);
    return response.data;
  },

  getWorkload: async (projectId: number): Promise<WorkloadEntry[]> => {
    const response = await client.get<WorkloadEntry[]>(`/projects/${projectId}/workload`);
    return response.data;
  },
};
