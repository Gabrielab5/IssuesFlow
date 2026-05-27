export interface UserResponse {
  id: number;
  username: string;
  email: string;
  fullName: string;
  role: UserRole;
}

export interface CreateUserRequest {
  username: string;
  email: string;
  fullName: string;
  role: string;
  password: string;
}

export interface UpdateUserRequest {
  fullName: string;
  role: string;
}

export type UserRole = 'ADMIN' | 'DEVELOPER';

export interface TicketResponse {
  id: number;
  title: string;
  description?: string;
  status: TicketStatus;
  priority: TicketPriority;
  type: TicketType;
  projectId: number;
  assigneeId?: number;
  assigneeUsername?: string;
  dueDate?: string;
  overdue: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTicketRequest {
  title: string;
  description?: string;
  status: string;
  priority: string;
  type: string;
  projectId: number;
  assigneeId?: number;
  dueDate?: string;
}

export interface UpdateTicketRequest {
  title?: string;
  description?: string;
  status?: string;
  priority?: string;
  type?: string;
  assigneeId?: number;
  dueDate?: string;
}

export type TicketStatus = 'TODO' | 'IN_PROGRESS' | 'IN_REVIEW' | 'DONE';
export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type TicketType = 'BUG' | 'FEATURE' | 'TECHNICAL';

export interface CommentResponse {
  id: number;
  ticketId: number;
  authorId: number;
  authorUsername: string;
  content: string;
  version: number;
  createdAt: string;
  updatedAt: string;
  mentionedUsers: UserSummary[];
}

export interface CreateCommentRequest {
  content: string;
}

export interface UpdateCommentRequest {
  content: string;
  version: number;
}

export interface UserSummary {
  id: number;
  username: string;
  fullName: string;
}

export interface AttachmentResponse {
  id: number;
  ticketId: number;
  filename: string;
  contentType: string;
  sizeBytes: number;
  createdAt: string;
}

export interface ImportResult {
  created: number;
  failed: number;
  errors: RowError[];
}

export interface RowError {
  row: number;
  reason: string;
}

export interface ProjectResponse {
  id: number;
  name: string;
  description?: string;
  ownerId: number;
  ownerUsername: string;
  createdAt: string;
  updatedAt: string;
  deletedAt?: string;
}

export interface CreateProjectRequest {
  name: string;
  description?: string;
  ownerId: number;
}

export interface UpdateProjectRequest {
  name?: string;
  description?: string;
}

export interface WorkloadEntry {
  userId: number;
  username: string;
  openTicketCount: number;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface AuthTokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface CurrentUserResponse {
  id: number;
  username: string;
  email: string;
  fullName: string;
  role: UserRole;
}

export interface MentionResponse {
  id: number;
  commentId: number;
  ticketId: number;
  authorId: number;
  authorUsername: string;
  commentContent: string;
  mentionedAt: string;
}

export interface PagedResponseMentionResponse {
  data: MentionResponse[];
  total: number;
  page: number;
}

export type AuditAction = 'CREATE' | 'UPDATE' | 'DELETE' | 'RESTORE' | 'AUTO_ASSIGN' | 'AUTO_ESCALATE' | 'LOGIN' | 'LOGOUT';
export type AuditActor = 'USER' | 'SYSTEM';

export interface AuditLogResponse {
  id: number;
  action: AuditAction;
  entityType: string;
  entityId: number;
  performedBy: number;
  actor: AuditActor;
  payload?: string;
  timestamp: string;
  createdAt: string;
}

export interface PagedResponseAuditLogResponse {
  data: AuditLogResponse[];
  total: number;
  page: number;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  details?: string[];
}
