import React from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ticketsApi, commentsApi, attachmentsApi, usersApi, projectsApi } from '../api';
import { useToast } from '../contexts/ToastContext';
import { useAuth } from '../contexts/AuthContext';
import type { TicketResponse, CommentResponse, AttachmentResponse, UserResponse, ProjectResponse } from '../types/api';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Textarea } from '../components/ui/textarea';
import { Select } from '../components/ui/select';
import { Badge } from '../components/ui/badge';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card';
import { PageLoader } from '../components/ui/loading';
import DatePicker from 'react-datepicker';
import 'react-datepicker/dist/react-datepicker.css';
import { format, parseISO } from 'date-fns';
import { ArrowLeft, CreditCard as Edit, Save, X, User, Calendar, FileText, Download, Upload, Trash2, Send } from 'lucide-react';
import { cn } from '../lib/utils';

export function TicketDetailPage() {
  const { ticketId } = useParams<{ ticketId: string }>();
  const navigate = useNavigate();
  const { user: currentUser } = useAuth();
  const { showToast } = useToast();

  const [ticket, setTicket] = React.useState<TicketResponse | null>(null);
  const [project, setProject] = React.useState<ProjectResponse | null>(null);
  const [comments, setComments] = React.useState<CommentResponse[]>([]);
  const [attachments, setAttachments] = React.useState<AttachmentResponse[]>([]);
  const [users, setUsers] = React.useState<UserResponse[]>([]);
  const [isLoading, setIsLoading] = React.useState(true);

  const [isEditing, setIsEditing] = React.useState(false);
  const [editData, setEditData] = React.useState({
    title: '',
    description: '',
    status: '',
    priority: '',
    type: '',
    assigneeId: undefined as number | undefined,
    dueDate: null as Date | null,
  });

  const [newComment, setNewComment] = React.useState('');
  const [isSubmittingComment, setIsSubmittingComment] = React.useState(false);

  const fetchData = React.useCallback(async () => {
    if (!ticketId) return;

    try {
      const [ticketData, commentsData, attachmentsData, usersData] = await Promise.all([
        ticketsApi.getById(Number(ticketId)),
        commentsApi.getByTicketId(Number(ticketId)),
        attachmentsApi.getByTicketId(Number(ticketId)),
        usersApi.getAll(),
      ]);

      setTicket(ticketData);
      setComments(commentsData);
      setAttachments(attachmentsData);
      setUsers(usersData);

      const projectData = await projectsApi.getById(ticketData.projectId);
      setProject(projectData);

      setEditData({
        title: ticketData.title,
        description: ticketData.description || '',
        status: ticketData.status,
        priority: ticketData.priority,
        type: ticketData.type,
        assigneeId: ticketData.assigneeId,
        dueDate: ticketData.dueDate ? new Date(ticketData.dueDate) : null,
      });
    } catch (error) {
      showToast('Failed to load ticket', 'error');
    } finally {
      setIsLoading(false);
    }
  }, [ticketId, showToast]);

  React.useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleSaveEdit = async () => {
    if (!ticket) return;

    try {
      const updated = await ticketsApi.update(ticket.id, {
        title: editData.title,
        description: editData.description || undefined,
        status: editData.status,
        priority: editData.priority,
        type: editData.type,
        assigneeId: editData.assigneeId,
        dueDate: editData.dueDate ? editData.dueDate.toISOString() : undefined,
      });

      setTicket(updated);
      setIsEditing(false);
      showToast('Ticket updated', 'success');
    } catch (error: any) {
      const message = error.response?.data?.message || 'Failed to update ticket';
      showToast(message, 'error');
    }
  };

  const handleAddComment = async () => {
    if (!ticket || !newComment.trim()) return;

    setIsSubmittingComment(true);
    try {
      const comment = await commentsApi.create(ticket.id, { content: newComment });
      setComments(prev => [...prev, comment]);
      setNewComment('');
      showToast('Comment added', 'success');
    } catch (error) {
      showToast('Failed to add comment', 'error');
    } finally {
      setIsSubmittingComment(false);
    }
  };

  const handleDeleteComment = async (commentId: number) => {
    if (!ticket || !window.confirm('Delete this comment?')) return;

    try {
      await commentsApi.delete(ticket.id, commentId);
      setComments(prev => prev.filter(c => c.id !== commentId));
      showToast('Comment deleted', 'success');
    } catch (error) {
      showToast('Failed to delete comment', 'error');
    }
  };

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!ticket || !file) return;

    if (file.size > 10 * 1024 * 1024) {
      showToast('File must be 10MB or smaller', 'error');
      return;
    }

    try {
      const attachment = await attachmentsApi.upload(ticket.id, file);
      setAttachments(prev => [...prev, attachment]);
      showToast('File uploaded', 'success');
    } catch (error) {
      showToast('Failed to upload file', 'error');
    }
  };

  const handleDownload = async (attachmentId: number, filename: string) => {
    if (!ticket) return;

    try {
      const blob = await attachmentsApi.download(ticket.id, attachmentId);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      a.click();
      window.URL.revokeObjectURL(url);
    } catch (error) {
      showToast('Failed to download file', 'error');
    }
  };

  const handleDeleteAttachment = async (attachmentId: number) => {
    if (!ticket || !window.confirm('Delete this attachment?')) return;

    try {
      await attachmentsApi.delete(ticket.id, attachmentId);
      setAttachments(prev => prev.filter(a => a.id !== attachmentId));
      showToast('Attachment deleted', 'success');
    } catch (error) {
      showToast('Failed to delete attachment', 'error');
    }
  };

  if (isLoading || !ticket) {
    return <PageLoader />;
  }

  const getAssignee = () => users.find(u => u.id === ticket.assigneeId);

  const formatFileSize = (bytes: number) => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <Button variant="ghost" onClick={() => navigate(-1)}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          Back
        </Button>
        {!isEditing && (
          <Button onClick={() => setIsEditing(true)}>
            <Edit className="h-4 w-4 mr-2" />
            Edit
          </Button>
        )}
      </div>

      <Card>
        <CardHeader>
          <div className="flex items-start justify-between">
            {isEditing ? (
              <div className="flex-1 space-y-2">
                <Input
                  value={editData.title}
                  onChange={(e) => setEditData({ ...editData, title: e.target.value })}
                  className="text-2xl font-bold"
                />
              </div>
            ) : (
              <div>
                <CardTitle className="text-2xl">{ticket.title}</CardTitle>
                <div className="flex items-center gap-2 mt-2 text-sm text-muted-foreground">
                  <span>#{ticket.id}</span>
                  <span>-</span>
                  <span>{project?.name}</span>
                  {ticket.overdue && (
                    <Badge variant="destructive">Overdue</Badge>
                  )}
                </div>
              </div>
            )}
            {isEditing && (
              <div className="flex gap-2 ml-4">
                <Button variant="outline" onClick={() => setIsEditing(false)}>
                  <X className="h-4 w-4 mr-2" />
                  Cancel
                </Button>
                <Button onClick={handleSaveEdit}>
                  <Save className="h-4 w-4 mr-2" />
                  Save
                </Button>
              </div>
            )}
          </div>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="lg:col-span-2 space-y-6">
              <div>
                <h3 className="font-semibold mb-2">Description</h3>
                {isEditing ? (
                  <Textarea
                    value={editData.description}
                    onChange={(e) => setEditData({ ...editData, description: e.target.value })}
                    rows={6}
                  />
                ) : (
                  <div className="prose prose-sm max-w-none">
                    {ticket.description ? (
                      <p className="whitespace-pre-wrap">{ticket.description}</p>
                    ) : (
                      <p className="text-muted-foreground italic">No description</p>
                    )}
                  </div>
                )}
              </div>

              <div>
                <h3 className="font-semibold mb-4">Comments ({comments.length})</h3>
                <div className="space-y-4">
                  {comments.map((comment) => (
                    <div key={comment.id} className="border rounded-lg p-4">
                      <div className="flex items-start justify-between mb-2">
                        <div className="flex items-center gap-2">
                          <div className="h-8 w-8 rounded-full bg-primary/10 flex items-center justify-center">
                            <User className="h-4 w-4 text-primary" />
                          </div>
                          <div>
                            <p className="font-medium text-sm">{comment.authorUsername}</p>
                            <p className="text-xs text-muted-foreground">
                              {format(parseISO(comment.createdAt), 'MMM d, yyyy h:mm a')}
                            </p>
                          </div>
                        </div>
                        {comment.authorId === currentUser?.id && (
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => handleDeleteComment(comment.id)}
                          >
                            <Trash2 className="h-4 w-3 text-destructive" />
                          </Button>
                        )}
                      </div>
                      <p className="text-sm whitespace-pre-wrap">{comment.content}</p>
                      {comment.mentionedUsers && comment.mentionedUsers.length > 0 && (
                        <div className="flex items-center gap-1 mt-2 flex-wrap">
                          <span className="text-xs text-muted-foreground">Mentions:</span>
                          {comment.mentionedUsers.map((u) => (
                            <Badge key={u.id} variant="outline" className="text-xs">
                              @{u.username}
                            </Badge>
                          ))}
                        </div>
                      )}
                    </div>
                  ))}

                  <div className="border rounded-lg p-4">
                    <Textarea
                      placeholder="Add a comment... Use @username to mention users"
                      value={newComment}
                      onChange={(e) => setNewComment(e.target.value)}
                      rows={3}
                    />
                    <div className="flex justify-end mt-2">
                      <Button
                        onClick={handleAddComment}
                        disabled={!newComment.trim() || isSubmittingComment}
                      >
                        <Send className="h-4 w-4 mr-2" />
                        Add Comment
                      </Button>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div className="space-y-6">
              <Card>
                <CardContent className="pt-6 space-y-4">
                  <div className="space-y-2">
                    <label className="text-sm font-medium">Status</label>
                    {isEditing ? (
                      <Select
                        value={editData.status}
                        onChange={(e) => setEditData({ ...editData, status: e.target.value })}
                        options={[
                          { value: 'TODO', label: 'To Do' },
                          { value: 'IN_PROGRESS', label: 'In Progress' },
                          { value: 'IN_REVIEW', label: 'In Review' },
                          { value: 'DONE', label: 'Done' },
                        ]}
                      />
                    ) : (
                      <Badge
                        variant={ticket.status.toLowerCase() as any}
                        className="text-sm"
                      >
                        {ticket.status.replace('_', ' ')}
                      </Badge>
                    )}
                  </div>

                  <div className="space-y-2">
                    <label className="text-sm font-medium">Priority</label>
                    {isEditing ? (
                      <Select
                        value={editData.priority}
                        onChange={(e) => setEditData({ ...editData, priority: e.target.value })}
                        options={[
                          { value: 'LOW', label: 'Low' },
                          { value: 'MEDIUM', label: 'Medium' },
                          { value: 'HIGH', label: 'High' },
                          { value: 'CRITICAL', label: 'Critical' },
                        ]}
                      />
                    ) : (
                      <Badge variant={ticket.priority.toLowerCase() as any} className="text-sm">
                        {ticket.priority}
                      </Badge>
                    )}
                  </div>

                  <div className="space-y-2">
                    <label className="text-sm font-medium">Type</label>
                    {isEditing ? (
                      <Select
                        value={editData.type}
                        onChange={(e) => setEditData({ ...editData, type: e.target.value })}
                        options={[
                          { value: 'BUG', label: 'Bug' },
                          { value: 'FEATURE', label: 'Feature' },
                          { value: 'TECHNICAL', label: 'Technical' },
                        ]}
                      />
                    ) : (
                      <Badge variant={ticket.type.toLowerCase() as any} className="text-sm">
                        {ticket.type}
                      </Badge>
                    )}
                  </div>

                  <div className="space-y-2">
                    <label className="text-sm font-medium">Assignee</label>
                    {isEditing ? (
                      <Select
                        value={editData.assigneeId ? String(editData.assigneeId) : ''}
                        onChange={(e) => setEditData({ ...editData, assigneeId: e.target.value ? Number(e.target.value) : undefined })}
                        options={[
                          { value: '', label: 'Unassigned' },
                          ...users.map((u) => ({ value: String(u.id), label: u.fullName })),
                        ]}
                      />
                    ) : (
                      <div className="flex items-center gap-2">
                        {getAssignee() ? (
                          <>
                            <div className="h-8 w-8 rounded-full bg-primary/10 flex items-center justify-center">
                              <User className="h-4 w-4 text-primary" />
                            </div>
                            <div>
                              <p className="text-sm font-medium">{getAssignee()?.fullName}</p>
                              <p className="text-xs text-muted-foreground">
                                @{getAssignee()?.username}
                              </p>
                            </div>
                          </>
                        ) : (
                          <span className="text-muted-foreground text-sm">Unassigned</span>
                        )}
                      </div>
                    )}
                  </div>

                  <div className="space-y-2">
                    <label className="text-sm font-medium flex items-center gap-2">
                      <Calendar className="h-4 w-4" />
                      Due Date
                    </label>
                    {isEditing ? (
                      <DatePicker
                        selected={editData.dueDate}
                        onChange={(date: Date | null) => setEditData({ ...editData, dueDate: date })}
                        className="w-full flex h-10 rounded-md border border-input bg-background px-3 py-2 text-sm"
                        dateFormat="yyyy-MM-dd"
                        placeholderText="No due date"
                      />
                    ) : (
                      <div className={cn('text-sm', ticket.overdue && 'text-destructive')}>
                        {ticket.dueDate
                          ? format(parseISO(ticket.dueDate), 'MMM d, yyyy')
                          : 'No due date'}
                      </div>
                    )}
                  </div>

                  <div className="pt-4 border-t text-xs text-muted-foreground space-y-1">
                    <p>Created: {format(parseISO(ticket.createdAt), 'MMM d, yyyy h:mm a')}</p>
                    <p>Updated: {format(parseISO(ticket.updatedAt), 'MMM d, yyyy h:mm a')}</p>
                    <p>Version: {ticket.version}</p>
                  </div>
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle className="text-sm font-medium flex items-center justify-between">
                    <span>Attachments ({attachments.length})</span>
                    <label className="cursor-pointer">
                      <input
                        type="file"
                        className="hidden"
                        onChange={handleFileUpload}
                      />
                      <Button variant="outline" size="sm" asChild>
                        <span>
                          <Upload className="h-3 w-3 mr-1" />
                          Upload
                        </span>
                      </Button>
                    </label>
                  </CardTitle>
                </CardHeader>
                <CardContent className="space-y-2">
                  {attachments.length === 0 ? (
                    <p className="text-sm text-muted-foreground text-center py-4">
                      No attachments
                    </p>
                  ) : (
                    attachments.map((attachment) => (
                      <div
                        key={attachment.id}
                        className="flex items-center justify-between p-2 border rounded"
                      >
                        <div className="flex items-center gap-2 flex-1 min-w-0">
                          <FileText className="h-4 w-4 flex-shrink-0" />
                          <div className="min-w-0">
                            <p className="text-sm font-medium truncate">{attachment.filename}</p>
                            <p className="text-xs text-muted-foreground">
                              {formatFileSize(attachment.sizeBytes)}
                            </p>
                          </div>
                        </div>
                        <div className="flex items-center gap-1">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => handleDownload(attachment.id, attachment.filename)}
                          >
                            <Download className="h-3 w-3" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => handleDeleteAttachment(attachment.id)}
                          >
                            <Trash2 className="h-3 w-3 text-destructive" />
                          </Button>
                        </div>
                      </div>
                    ))
                  )}
                </CardContent>
              </Card>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
