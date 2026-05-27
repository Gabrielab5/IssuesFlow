import React from 'react';
import { useSearchParams } from 'react-router-dom';
import { projectsApi, ticketsApi, usersApi } from '../api';
import { useToast } from '../contexts/ToastContext';
import type { ProjectResponse, TicketResponse, UserResponse } from '../types/api';
import { PageLoader } from '../components/ui/loading';
import { KanbanBoard } from '../components/tickets/KanbanBoard';
import { TicketList } from '../components/tickets/TicketList';
import { Button } from '../components/ui/button';
import { Select } from '../components/ui/select';
import { Card, CardContent } from '../components/ui/card';
import { LayoutGrid, List, Plus, RefreshCw } from 'lucide-react';
import { CreateTicketDialog } from '../components/tickets/CreateTicketDialog';

export function TicketsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const { showToast } = useToast();
  const [projects, setProjects] = React.useState<ProjectResponse[]>([]);
  const [tickets, setTickets] = React.useState<TicketResponse[]>([]);
  const [users, setUsers] = React.useState<UserResponse[]>([]);
  const [isLoading, setIsLoading] = React.useState(true);
  const [selectedProjectId, setSelectedProjectId] = React.useState<number | null>(
    searchParams.get('projectId') ? Number(searchParams.get('projectId')) : null
  );
  const [viewMode, setViewMode] = React.useState<'kanban' | 'list'>('kanban');
  const [isCreateDialogOpen, setIsCreateDialogOpen] = React.useState(false);

  const fetchData = React.useCallback(async () => {
    setIsLoading(true);
    try {
      const [projectsData, usersData] = await Promise.all([
        projectsApi.getAll(),
        usersApi.getAll(),
      ]);
      const activeProjects = projectsData.filter(p => !p.deletedAt);
      setProjects(activeProjects);
      setUsers(usersData);

      const projectId = selectedProjectId || (activeProjects[0]?.id ?? null);
      if (projectId) {
        const ticketsData = await ticketsApi.getAll(projectId);
        setTickets(ticketsData);
        if (!selectedProjectId) {
          setSelectedProjectId(projectId);
        }
      }
    } catch (error) {
      showToast('Failed to load data', 'error');
    } finally {
      setIsLoading(false);
    }
  }, [selectedProjectId, showToast]);

  React.useEffect(() => {
    fetchData();
  }, []);

  React.useEffect(() => {
    if (selectedProjectId) {
      setSearchParams({ projectId: String(selectedProjectId) });
      ticketsApi.getAll(selectedProjectId)
        .then(setTickets)
        .catch(() => showToast('Failed to load tickets', 'error'));
    }
  }, [selectedProjectId, setSearchParams, showToast]);

  const handleTicketUpdate = async (ticketId: number, updates: Partial<TicketResponse>) => {
    try {
      const updated = await ticketsApi.update(ticketId, updates);
      setTickets(prev => prev.map(t => t.id === ticketId ? updated : t));
      showToast('Ticket updated', 'success');
    } catch (error: any) {
      const message = error.response?.data?.message || 'Failed to update ticket';
      showToast(message, 'error');
    }
  };

  const handleTicketCreated = (ticket: TicketResponse) => {
    setTickets(prev => [...prev, ticket]);
    setIsCreateDialogOpen(false);
  };

  if (isLoading) {
    return <PageLoader />;
  }

  if (projects.length === 0) {
    return (
      <div className="text-center py-12">
        <p className="text-muted-foreground mb-4">No projects available. Create a project first.</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Tickets</h1>
          <p className="text-muted-foreground">Manage project tickets</p>
        </div>
        <Button onClick={() => setIsCreateDialogOpen(true)} disabled={!selectedProjectId}>
          <Plus className="h-4 w-4 mr-2" />
          New Ticket
        </Button>
      </div>

      <Card>
        <CardContent className="py-4">
          <div className="flex items-center gap-4 justify-between">
            <div className="flex items-center gap-4">
              <div className="w-64">
                <Select
                  value={String(selectedProjectId || '')}
                  onChange={(e) => setSelectedProjectId(Number(e.target.value))}
                  options={projects.map(p => ({ value: String(p.id), label: p.name }))}
                />
              </div>

              <div className="flex items-center border rounded-md">
                <Button
                  variant={viewMode === 'kanban' ? 'secondary' : 'ghost'}
                  size="sm"
                  onClick={() => setViewMode('kanban')}
                  className="rounded-r-none"
                >
                  <LayoutGrid className="h-4 w-4" />
                </Button>
                <Button
                  variant={viewMode === 'list' ? 'secondary' : 'ghost'}
                  size="sm"
                  onClick={() => setViewMode('list')}
                  className="rounded-l-none"
                >
                  <List className="h-4 w-4" />
                </Button>
              </div>
            </div>

            <Button variant="outline" size="sm" onClick={fetchData}>
              <RefreshCw className="h-4 w-4 mr-2" />
              Refresh
            </Button>
          </div>
        </CardContent>
      </Card>

      {selectedProjectId ? (
        viewMode === 'kanban' ? (
          <KanbanBoard
            tickets={tickets}
            users={users}
            onTicketUpdate={handleTicketUpdate}
          />
        ) : (
          <TicketList
            tickets={tickets}
            users={users}
            onTicketUpdate={handleTicketUpdate}
          />
        )
      ) : (
        <div className="text-center py-12 text-muted-foreground">
          Select a project to view tickets
        </div>
      )}

      <CreateTicketDialog
        open={isCreateDialogOpen}
        onOpenChange={setIsCreateDialogOpen}
        projectId={selectedProjectId!}
        users={users}
        onCreated={handleTicketCreated}
      />
    </div>
  );
}
