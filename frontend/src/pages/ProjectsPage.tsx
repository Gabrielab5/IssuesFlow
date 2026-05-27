import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { projectsApi, usersApi } from '../api';
import { useToast } from '../contexts/ToastContext';
import { useAuth } from '../contexts/AuthContext';
import type { ProjectResponse, UserResponse, WorkloadEntry } from '../types/api';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Textarea } from '../components/ui/textarea';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../components/ui/card';
import { Select } from '../components/ui/select';
import { LoadingSpinner, PageLoader } from '../components/ui/loading';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '../components/ui/dialog';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';
import { Plus, CreditCard as Edit, Trash2, ExternalLink, RotateCcw, ChartBar as BarChart3 } from 'lucide-react';

const createProjectSchema = z.object({
  name: z.string().min(1, 'Project name is required').max(255, 'Name too long'),
  description: z.string().optional(),
  ownerId: z.number(),
});

type CreateProjectData = z.infer<typeof createProjectSchema>;

export function ProjectsPage() {
  const navigate = useNavigate();
  const { user: currentUser } = useAuth();
  const { showToast } = useToast();
  const [projects, setProjects] = React.useState<ProjectResponse[]>([]);
  const [deletedProjects, setDeletedProjects] = React.useState<ProjectResponse[]>([]);
  const [users, setUsers] = React.useState<UserResponse[]>([]);
  const [isLoading, setIsLoading] = React.useState(true);
  const [isSubmitting, setIsSubmitting] = React.useState(false);
  const [isDialogOpen, setIsDialogOpen] = React.useState(false);
  const [editingProject, setEditingProject] = React.useState<ProjectResponse | null>(null);
  const [workloadDialog, setWorkloadDialog] = React.useState<{
    open: boolean;
    projectId: number | null;
    workload: WorkloadEntry[];
  }>({ open: false, projectId: null, workload: [] });

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<CreateProjectData>({
    resolver: zodResolver(createProjectSchema),
  });

  const fetchData = React.useCallback(async () => {
    try {
      const [projectsData, deletedData, usersData] = await Promise.all([
        projectsApi.getAll(),
        projectsApi.getDeleted(),
        usersApi.getAll(),
      ]);
      setProjects(projectsData.filter(p => !p.deletedAt));
      setDeletedProjects(deletedData);
      setUsers(usersData);
    } catch (error) {
      showToast('Failed to load data', 'error');
    } finally {
      setIsLoading(false);
    }
  }, [showToast]);

  React.useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleOpenCreate = () => {
    setEditingProject(null);
    reset({
      name: '',
      description: '',
      ownerId: currentUser?.id || 0,
    });
    setIsDialogOpen(true);
  };

  const handleEdit = (project: ProjectResponse) => {
    setEditingProject(project);
    reset({
      name: project.name,
      description: project.description || '',
      ownerId: project.ownerId,
    });
    setIsDialogOpen(true);
  };

  const handleDelete = async (projectId: number, projectName: string) => {
    if (!window.confirm(`Are you sure you want to delete project "${projectName}"?`)) {
      return;
    }

    try {
      await projectsApi.delete(projectId);
      showToast('Project deleted successfully', 'success');
      fetchData();
    } catch (error) {
      showToast('Failed to delete project', 'error');
    }
  };

  const handleRestore = async (projectId: number, projectName: string) => {
    if (!window.confirm(`Are you sure you want to restore project "${projectName}"?`)) {
      return;
    }

    try {
      await projectsApi.restore(projectId);
      showToast('Project restored successfully', 'success');
      fetchData();
    } catch (error) {
      showToast('Failed to restore project', 'error');
    }
  };

  const handleViewWorkload = async (projectId: number) => {
    try {
      const workload = await projectsApi.getWorkload(projectId);
      setWorkloadDialog({ open: true, projectId, workload });
    } catch (error) {
      showToast('Failed to load workload', 'error');
    }
  };

  const onSubmit = async (data: CreateProjectData) => {
    setIsSubmitting(true);
    try {
      if (editingProject) {
        await projectsApi.update(editingProject.id, {
          name: data.name,
          description: data.description,
        });
        showToast('Project updated successfully', 'success');
      } else {
        await projectsApi.create(data);
        showToast('Project created successfully', 'success');
      }
      setIsDialogOpen(false);
      fetchData();
    } catch (error: any) {
      const message = error.response?.data?.message || 'Operation failed';
      showToast(message, 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoading) {
    return <PageLoader />;
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Projects</h1>
          <p className="text-muted-foreground">Manage your projects</p>
        </div>
        <Button onClick={handleOpenCreate}>
          <Plus className="h-4 w-4 mr-2" />
          New Project
        </Button>
      </div>

      {/* Active Projects */}
      <div>
        <h2 className="text-xl font-semibold mb-4">Active Projects</h2>
        {projects.length === 0 ? (
          <Card>
            <CardContent className="text-center py-12 text-muted-foreground">
              No projects found. Create your first project to get started.
            </CardContent>
          </Card>
        ) : (
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {projects.map((project) => (
              <Card key={project.id} className="hover:shadow-lg transition-shadow">
                <CardHeader>
                  <div className="flex items-start justify-between">
                    <div className="flex-1">
                      <CardTitle className="text-lg cursor-pointer hover:text-primary"
                        onClick={() => navigate(`/projects/${project.id}`)}>
                        {project.name}
                      </CardTitle>
                      <CardDescription className="mt-1">
                        Owner: {project.ownerUsername}
                      </CardDescription>
                    </div>
                    <div className="flex items-center gap-1">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => handleViewWorkload(project.id)}
                      >
                        <BarChart3 className="h-4 w-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => navigate(`/projects/${project.id}`)}
                      >
                        <ExternalLink className="h-4 w-4" />
                      </Button>
                    </div>
                  </div>
                </CardHeader>
                <CardContent>
                  {project.description && (
                    <p className="text-sm text-muted-foreground mb-4 line-clamp-2">
                      {project.description}
                    </p>
                  )}
                  <div className="flex items-center justify-between text-xs text-muted-foreground">
                    <span>Created {new Date(project.createdAt).toLocaleDateString()}</span>
                    <div className="flex items-center gap-2">
                      {currentUser?.role === 'ADMIN' && (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleEdit(project)}
                        >
                          <Edit className="h-3 w-3" />
                        </Button>
                      )}
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => handleDelete(project.id, project.name)}
                      >
                        <Trash2 className="h-3 w-3 text-destructive" />
                      </Button>
                    </div>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        )}
      </div>

      {/* Deleted Projects (Admin Only) */}
      {currentUser?.role === 'ADMIN' && deletedProjects.length > 0 && (
        <div>
          <h2 className="text-xl font-semibold mb-4">Deleted Projects</h2>
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {deletedProjects.map((project) => (
              <Card key={project.id} className="opacity-60">
                <CardHeader>
                  <div className="flex items-center justify-between">
                    <div>
                      <CardTitle className="text-lg">{project.name}</CardTitle>
                      <CardDescription>
                        Deleted: {new Date(project.deletedAt!).toLocaleDateString()}
                      </CardDescription>
                    </div>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => handleRestore(project.id, project.name)}
                    >
                      <RotateCcw className="h-4 w-4 mr-2" />
                      Restore
                    </Button>
                  </div>
                </CardHeader>
              </Card>
            ))}
          </div>
        </div>
      )}

      {/* Create/Edit Dialog */}
      <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>
              {editingProject ? 'Edit Project' : 'Create New Project'}
            </DialogTitle>
          </DialogHeader>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="name">Project Name</Label>
              <Input
                id="name"
                {...register('name')}
                disabled={isSubmitting}
              />
              {errors.name && (
                <p className="text-sm text-destructive">{errors.name.message}</p>
              )}
            </div>

            <div className="space-y-2">
              <Label htmlFor="description">Description</Label>
              <Textarea
                id="description"
                {...register('description')}
                disabled={isSubmitting}
                rows={3}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="ownerId">Owner</Label>
              <Select
                {...register('ownerId', { valueAsNumber: true })}
                disabled={!!editingProject || isSubmitting}
                options={users.map(u => ({ value: String(u.id), label: u.fullName }))}
              />
              {errors.ownerId && (
                <p className="text-sm text-destructive">{errors.ownerId.message}</p>
              )}
            </div>

            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setIsDialogOpen(false)}
                disabled={isSubmitting}
              >
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? <LoadingSpinner size="sm" /> : editingProject ? 'Update' : 'Create'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Workload Dialog */}
      <Dialog open={workloadDialog.open} onOpenChange={(open) => setWorkloadDialog({ ...workloadDialog, open })}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>Developer Workload</DialogTitle>
          </DialogHeader>
          <div className="h-80">
            {workloadDialog.workload.length === 0 ? (
              <div className="text-center py-12 text-muted-foreground">
                No workload data available
              </div>
            ) : (
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={workloadDialog.workload} layout="vertical">
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis type="number" />
                  <YAxis dataKey="username" type="category" width={100} />
                  <Tooltip />
                  <Bar dataKey="openTicketCount" fill="#3b82f6" name="Open Tickets" />
                </BarChart>
              </ResponsiveContainer>
            )}
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
