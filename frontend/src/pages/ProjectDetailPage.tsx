import React from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { projectsApi, ticketsApi, usersApi } from '../api';
import { useToast } from '../contexts/ToastContext';
import { useAuth } from '../contexts/AuthContext';
import type { ProjectResponse, TicketResponse, UserResponse, WorkloadEntry, ImportResult } from '../types/api';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Textarea } from '../components/ui/textarea';
import { Badge } from '../components/ui/badge';
import { LoadingSpinner, PageLoader } from '../components/ui/loading';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
} from 'recharts';
import { format, parseISO } from 'date-fns';
import {
  ArrowLeft,
  Edit,
  Save,
  X,
  Download,
  Upload,
  BarChart3,
  Ticket,
  User,
} from 'lucide-react';

const STATUS_COLORS: Record<string, string> = {
  TODO: '#6b7280',
  IN_PROGRESS: '#3b82f6',
  IN_REVIEW: '#8b5cf6',
  DONE: '#10b981',
};

export function ProjectDetailPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();
  const { user: currentUser } = useAuth();
  const { showToast } = useToast();

  const [project, setProject] = React.useState<ProjectResponse | null>(null);
  const [tickets, setTickets] = React.useState<TicketResponse[]>([]);
  const [workload, setWorkload] = React.useState<WorkloadEntry[]>([]);
  const [users, setUsers] = React.useState<UserResponse[]>([]);
  const [isLoading, setIsLoading] = React.useState(true);
  const [isEditing, setIsEditing] = React.useState(false);
  const [isSubmitting, setIsSubmitting] = React.useState(false);

  const [editData, setEditData] = React.useState({
    name: '',
    description: '',
  });

  const [importing, setImporting] = React.useState(false);
  const [importResult, setImportResult] = React.useState<ImportResult | null>(null);

  const fetchData = React.useCallback(async () => {
    if (!projectId) return;

    try {
      const [projectData, ticketsData, workloadData, usersData] = await Promise.all([
        projectsApi.getById(Number(projectId)),
        ticketsApi.getAll(Number(projectId)),
        projectsApi.getWorkload(Number(projectId)),
        usersApi.getAll(),
      ]);

      setProject(projectData);
      setTickets(ticketsData);
      setWorkload(workloadData);
      setUsers(usersData);

      setEditData({
        name: projectData.name,
        description: projectData.description || '',
      });
    } catch (error) {
      showToast('Failed to load project', 'error');
    } finally {
      setIsLoading(false);
    }
  }, [projectId, showToast]);

  React.useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleSaveEdit = async () => {
    if (!project) return;

    setIsSubmitting(true);
    try {
      const updated = await projectsApi.update(project.id, {
        name: editData.name,
        description: editData.description || undefined,
      });
      setProject(updated);
      setIsEditing(false);
      showToast('Project updated', 'success');
    } catch (error: any) {
      const message = error.response?.data?.message || 'Failed to update project';
      showToast(message, 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleExportCsv = async () => {
    if (!projectId) return;

    try {
      const blob = await ticketsApi.exportCsv(Number(projectId));
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `tickets-project-${projectId}.csv`;
      a.click();
      window.URL.revokeObjectURL(url);
      showToast('CSV exported successfully', 'success');
    } catch (error) {
      showToast('Failed to export CSV', 'error');
    }
  };

  const handleImportCsv = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!projectId || !file) return;

    setImporting(true);
    try {
      const result = await ticketsApi.importCsv(Number(projectId), file);
      setImportResult(result);
      fetchData();
      if (result.created > 0) {
        showToast(`Imported ${result.created} tickets successfully`, 'success');
      }
      if (result.failed > 0) {
        showToast(`${result.failed} rows failed to import`, 'warning');
      }
    } catch (error) {
      showToast('Failed to import CSV', 'error');
    } finally {
      setImporting(false);
    }
  };

  if (isLoading || !project) {
    return <PageLoader />;
  }

  const owner = users.find(u => u.id === project.ownerId);

  const statusData = Object.entries(
    tickets.reduce((acc, t) => {
      acc[t.status] = (acc[t.status] || 0) + 1;
      return acc;
    }, {} as Record<string, number>)
  ).map(([name, value]) => ({ name, value }));

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <Button variant="ghost" onClick={() => navigate('/projects')}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          Back to Projects
        </Button>
        {!isEditing && currentUser?.role === 'ADMIN' && !project.deletedAt && (
          <Button onClick={() => setIsEditing(true)}>
            <Edit className="h-4 w-4 mr-2" />
            Edit Project
          </Button>
        )}
      </div>

      <Card>
        <CardHeader>
          <div className="flex items-start justify-between">
            {isEditing ? (
              <div className="flex-1 space-y-4">
                <Input
                  value={editData.name}
                  onChange={(e) => setEditData({ ...editData, name: e.target.value })}
                  className="text-2xl font-bold"
                />
                <Textarea
                  value={editData.description}
                  onChange={(e) => setEditData({ ...editData, description: e.target.value })}
                  rows={3}
                  placeholder="Project description..."
                />
              </div>
            ) : (
              <div>
                <CardTitle className="text-2xl">{project.name}</CardTitle>
                {project.description && (
                  <CardDescription className="mt-2">{project.description}</CardDescription>
                )}
              </div>
            )}
            {isEditing && (
              <div className="flex gap-2 ml-4">
                <Button variant="outline" onClick={() => setIsEditing(false)}>
                  <X className="h-4 w-4 mr-2" />
                  Cancel
                </Button>
                <Button onClick={handleSaveEdit} disabled={isSubmitting}>
                  {isSubmitting ? <LoadingSpinner size="sm" /> : <Save className="h-4 w-4 mr-2" />}
                  Save
                </Button>
              </div>
            )}
          </div>
        </CardHeader>
        <CardContent>
          <div className="flex items-center gap-4 text-sm text-muted-foreground mb-6">
            <div className="flex items-center gap-2">
              <User className="h-4 w-4" />
              <span>Owner: {owner?.fullName || 'Unknown'}</span>
            </div>
            <span>Created: {format(parseISO(project.createdAt), 'MMM d, yyyy')}</span>
            {project.deletedAt && (
              <Badge variant="destructive">Deleted</Badge>
            )}
          </div>

          {/* Quick Actions */}
          <div className="flex items-center gap-2 mb-6">
            <Button onClick={() => navigate(`/tickets?projectId=${project.id}`)}>
              <Ticket className="h-4 w-4 mr-2" />
              View Tickets
            </Button>
            <Button onClick={handleExportCsv} variant="outline">
              <Download className="h-4 w-4 mr-2" />
              Export CSV
            </Button>
            <label className="cursor-pointer">
              <input
                type="file"
                accept=".csv"
                className="hidden"
                onChange={handleImportCsv}
                disabled={importing}
              />
              <Button variant="outline" asChild disabled={importing}>
                <span>
                  <Upload className="h-4 w-4 mr-2" />
                  Import CSV
                </span>
              </Button>
            </label>
          </div>

          {/* Import Results */}
          {importResult && (
            <Card className="mb-6 border-warning">
              <CardHeader>
                <CardTitle className="text-lg">Import Results</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-2 gap-4 mb-4">
                  <div className="p-4 bg-success/10 rounded-lg">
                    <div className="text-2xl font-bold text-success">{importResult.created}</div>
                    <div className="text-sm text-muted-foreground">Created</div>
                  </div>
                  <div className="p-4 bg-destructive/10 rounded-lg">
                    <div className="text-2xl font-bold text-destructive">{importResult.failed}</div>
                    <div className="text-sm text-muted-foreground">Failed</div>
                  </div>
                </div>
                {importResult.errors && importResult.errors.length > 0 && (
                  <div>
                    <h4 className="font-medium mb-2">Errors:</h4>
                    <div className="max-h-48 overflow-y-auto space-y-1">
                      {importResult.errors.map((error, idx) => (
                        <div key={idx} className="text-sm bg-muted p-2 rounded">
                          <span className="font-medium">Row {error.row}:</span> {error.reason}
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </CardContent>
            </Card>
          )}

          {/* Metrics */}
          <div className="grid gap-4 md:grid-cols-3 mb-6">
            <Card>
              <CardContent className="pt-6">
                <div className="text-2xl font-bold">{tickets.length}</div>
                <div className="text-sm text-muted-foreground">Total Tickets</div>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="pt-6">
                <div className="text-2xl font-bold">
                  {tickets.filter(t => t.status !== 'DONE').length}
                </div>
                <div className="text-sm text-muted-foreground">Open Tickets</div>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="pt-6">
                <div className="text-2xl font-bold text-destructive">
                  {tickets.filter(t => t.overdue).length}
                </div>
                <div className="text-sm text-muted-foreground">Overdue</div>
              </CardContent>
            </Card>
          </div>

          {/* Charts */}
          <div className="grid gap-4 md:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle className="text-lg">Tickets by Status</CardTitle>
              </CardHeader>
              <CardContent>
                {tickets.length === 0 ? (
                  <div className="text-center py-12 text-muted-foreground">No tickets</div>
                ) : (
                  <ResponsiveContainer width="100%" height={250}>
                    <PieChart>
                      <Pie
                        data={statusData}
                        cx="50%"
                        cy="50%"
                        labelLine={false}
                        label={({ name, percent }) => `${name} (${((percent || 0) * 100).toFixed(0)}%)`}
                        outerRadius={80}
                        dataKey="value"
                      >
                        {statusData.map((entry, index) => (
                          <Cell key={`cell-${index}`} fill={STATUS_COLORS[entry.name] || '#888'} />
                        ))}
                      </Pie>
                      <Tooltip />
                    </PieChart>
                  </ResponsiveContainer>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-lg flex items-center gap-2">
                  <BarChart3 className="h-4 w-4" />
                  Developer Workload
                </CardTitle>
              </CardHeader>
              <CardContent>
                {workload.length === 0 ? (
                  <div className="text-center py-12 text-muted-foreground">No workload data</div>
                ) : (
                  <ResponsiveContainer width="100%" height={250}>
                    <BarChart data={workload} layout="vertical">
                      <CartesianGrid strokeDasharray="3 3" />
                      <XAxis type="number" />
                      <YAxis dataKey="username" type="category" width={80} />
                      <Tooltip />
                      <Bar dataKey="openTicketCount" fill="#3b82f6" name="Open Tickets" />
                    </BarChart>
                  </ResponsiveContainer>
                )}
              </CardContent>
            </Card>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
