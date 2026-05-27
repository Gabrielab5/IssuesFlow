import React from 'react';
import { auditLogsApi } from '../api';
import { useToast } from '../contexts/ToastContext';
import type { AuditLogResponse, PagedResponseAuditLogResponse, AuditAction, AuditActor } from '../types/api';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Select } from '../components/ui/select';
import { Badge } from '../components/ui/badge';
import { PageLoader } from '../components/ui/loading';
import { format, parseISO } from 'date-fns';
import { RefreshCw, ChevronLeft, ChevronRight, ChevronDown, ChevronUp } from 'lucide-react';

const ENTITY_TYPES = [
  { value: 'TICKET', label: 'Ticket' },
  { value: 'PROJECT', label: 'Project' },
  { value: 'USER', label: 'User' },
  { value: 'COMMENT', label: 'Comment' },
  { value: 'ATTACHMENT', label: 'Attachment' },
];

const ACTIONS: { value: AuditAction; label: string }[] = [
  { value: 'CREATE', label: 'Create' },
  { value: 'UPDATE', label: 'Update' },
  { value: 'DELETE', label: 'Delete' },
  { value: 'RESTORE', label: 'Restore' },
  { value: 'AUTO_ASSIGN', label: 'Auto Assign' },
  { value: 'AUTO_ESCALATE', label: 'Auto Escalate' },
  { value: 'LOGIN', label: 'Login' },
  { value: 'LOGOUT', label: 'Logout' },
];

const ACTORS: { value: AuditActor; label: string }[] = [
  { value: 'USER', label: 'User' },
  { value: 'SYSTEM', label: 'System' },
];

export function AuditLogsPage() {
  const { showToast } = useToast();
  const [auditLogs, setAuditLogs] = React.useState<PagedResponseAuditLogResponse | null>(null);
  const [isLoading, setIsLoading] = React.useState(true);
  const [expandedLogId, setExpandedLogId] = React.useState<number | null>(null);

  const [filters, setFilters] = React.useState({
    entityType: '',
    action: '',
    actor: '',
    performedBy: '',
    from: '',
    to: '',
    page: 1,
    pageSize: 20,
  });

  const fetchAuditLogs = React.useCallback(async () => {
    setIsLoading(true);
    try {
      const params: any = {};

      if (filters.entityType) params.entityType = filters.entityType;
      if (filters.action) params.action = filters.action;
      if (filters.actor) params.actor = filters.actor;
      if (filters.performedBy) params.performedBy = Number(filters.performedBy);
      if (filters.from) params.from = new Date(filters.from).toISOString();
      if (filters.to) params.to = new Date(filters.to).toISOString();
      params.page = filters.page;
      params.pageSize = filters.pageSize;

      const data = await auditLogsApi.getAll(params);
      setAuditLogs(data);
    } catch (error) {
      showToast('Failed to load audit logs', 'error');
    } finally {
      setIsLoading(false);
    }
  }, [filters, showToast]);

  React.useEffect(() => {
    fetchAuditLogs();
  }, [filters]);

  const handleFilterChange = (key: string, value: string | number) => {
    setFilters((prev) => ({ ...prev, [key]: value, page: 1 }));
  };

  const handleReset = () => {
    setFilters({
      entityType: '',
      action: '',
      actor: '',
      performedBy: '',
      from: '',
      to: '',
      page: 1,
      pageSize: 20,
    });
  };

  if (isLoading && !auditLogs) {
    return <PageLoader />;
  }

  const totalPages = auditLogs ? Math.ceil(auditLogs.total / filters.pageSize) : 0;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold">Audit Logs</h1>
        <p className="text-muted-foreground">System activity and changes</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Filters</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            <div className="space-y-2">
              <label className="text-sm font-medium">Entity Type</label>
              <Select
                value={filters.entityType}
                onChange={(e) => handleFilterChange('entityType', e.target.value)}
                options={[{ value: '', label: 'All' }, ...ENTITY_TYPES]}
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium">Action</label>
              <Select
                value={filters.action}
                onChange={(e) => handleFilterChange('action', e.target.value)}
                options={[{ value: '', label: 'All' }, ...ACTIONS]}
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium">Actor</label>
              <Select
                value={filters.actor}
                onChange={(e) => handleFilterChange('actor', e.target.value)}
                options={[{ value: '', label: 'All' }, ...ACTORS]}
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium">Performed By (User ID)</label>
              <Input
                type="number"
                value={filters.performedBy}
                onChange={(e) => handleFilterChange('performedBy', e.target.value)}
                placeholder="User ID"
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium">From Date</label>
              <Input
                type="datetime-local"
                value={filters.from}
                onChange={(e) => handleFilterChange('from', e.target.value)}
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium">To Date</label>
              <Input
                type="datetime-local"
                value={filters.to}
                onChange={(e) => handleFilterChange('to', e.target.value)}
              />
            </div>
          </div>

          <div className="flex items-center gap-2 mt-4">
            <Button variant="outline" onClick={handleReset}>
              Reset Filters
            </Button>
            <Button variant="outline" onClick={fetchAuditLogs}>
              <RefreshCw className="h-4 w-4 mr-2" />
              Refresh
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="pt-6">
          {!auditLogs || auditLogs.data.length === 0 ? (
            <div className="text-center py-12 text-muted-foreground">
              No audit logs found
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b">
                    <th className="text-left py-3 px-4 font-medium">ID</th>
                    <th className="text-left py-3 px-4 font-medium">Timestamp</th>
                    <th className="text-left py-3 px-4 font-medium">Action</th>
                    <th className="text-left py-3 px-4 font-medium">Entity</th>
                    <th className="text-left py-3 px-4 font-medium">Actor</th>
                    <th className="text-left py-3 px-4 font-medium">Performed By</th>
                    <th className="text-left py-3 px-4 font-medium w-12"></th>
                  </tr>
                </thead>
                <tbody>
                  {auditLogs.data.map((log) => (
                    <AuditLogRow
                      key={log.id}
                      log={log}
                      isExpanded={expandedLogId === log.id}
                      onToggle={() => setExpandedLogId(expandedLogId === log.id ? null : log.id)}
                    />
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      {auditLogs && totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">
            Page {filters.page} of {totalPages} ({auditLogs.total} total)
          </p>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={filters.page === 1}
              onClick={() => handleFilterChange('page', filters.page - 1)}
            >
              <ChevronLeft className="h-4 w-4" />
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={filters.page === totalPages}
              onClick={() => handleFilterChange('page', filters.page + 1)}
            >
              Next
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}

function AuditLogRow({
  log,
  isExpanded,
  onToggle,
}: {
  log: AuditLogResponse;
  isExpanded: boolean;
  onToggle: () => void;
}) {
  const getActionVariant = (action: string) => {
    const map: Record<string, string> = {
      CREATE: 'create',
      UPDATE: 'update',
      DELETE: 'delete',
      RESTORE: 'restore',
      AUTO_ASSIGN: 'update',
      AUTO_ESCALATE: 'warning',
      LOGIN: 'success',
      LOGOUT: 'secondary',
    };
    return map[action] || 'default';
  };

  return (
    <>
      <tr className="border-b last:border-0 hover:bg-muted/50">
        <td className="py-3 px-4 text-muted-foreground">{log.id}</td>
        <td className="py-3 px-4">
          <div className="text-sm">{format(parseISO(log.timestamp), 'MMM d, yyyy')}</div>
          <div className="text-xs text-muted-foreground">
            {format(parseISO(log.timestamp), 'h:mm:ss a')}
          </div>
        </td>
        <td className="py-3 px-4">
          <Badge variant={getActionVariant(log.action) as any}>{log.action}</Badge>
        </td>
        <td className="py-3 px-4">
          <div className="text-sm font-medium">{log.entityType}</div>
          <div className="text-xs text-muted-foreground">ID: {log.entityId}</div>
        </td>
        <td className="py-3 px-4">
          <Badge variant={log.actor.toLowerCase() as any}>{log.actor}</Badge>
        </td>
        <td className="py-3 px-4 text-muted-foreground">
          {log.performedBy || '-'}
        </td>
        <td className="py-3 px-4">
          <Button variant="ghost" size="sm" onClick={onToggle}>
            {isExpanded ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
          </Button>
        </td>
      </tr>
      {isExpanded && log.payload && (
        <tr>
          <td colSpan={7} className="px-4 py-4 bg-muted/30">
            <div className="text-sm font-medium mb-2">Payload:</div>
            <pre className="text-xs bg-muted p-4 rounded overflow-x-auto">
              {JSON.stringify(JSON.parse(log.payload), null, 2)}
            </pre>
          </td>
        </tr>
      )}
    </>
  );
}
