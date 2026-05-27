import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { TicketResponse, UserResponse } from '../../types/api';
import { Card, CardContent } from '../ui/card';
import { Badge } from '../ui/badge';
import { Select } from '../ui/select';
import { Input } from '../ui/input';
import { format, parseISO } from 'date-fns';
import { Search, Clock, User } from 'lucide-react';
import { cn } from '../../lib/utils';

interface TicketListProps {
  tickets: TicketResponse[];
  users: UserResponse[];
  onTicketUpdate: (ticketId: number, updates: Partial<TicketResponse>) => Promise<void>;
}

const priorityOptions = [
  { value: 'LOW', label: 'Low' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'HIGH', label: 'High' },
  { value: 'CRITICAL', label: 'Critical' },
];

const statusOptions = [
  { value: 'TODO', label: 'To Do' },
  { value: 'IN_PROGRESS', label: 'In Progress' },
  { value: 'IN_REVIEW', label: 'In Review' },
  { value: 'DONE', label: 'Done' },
];

export function TicketList({ tickets, users, onTicketUpdate }: TicketListProps) {
  const navigate = useNavigate();
  const [searchQuery, setSearchQuery] = useState('');

  const filteredTickets = tickets.filter(t =>
    t.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
    t.description?.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const handlePriorityChange = async (ticketId: number, priority: string) => {
    await onTicketUpdate(ticketId, { priority } as Partial<TicketResponse>);
  };

  const handleStatusChange = async (ticketId: number, status: string) => {
    await onTicketUpdate(ticketId, { status } as Partial<TicketResponse>);
  };

  const getAssignee = (assigneeId?: number) => users.find(u => u.id === assigneeId);

  return (
    <div className="space-y-4">
      <div className="relative max-w-sm">
        <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-muted-foreground" />
        <Input
          placeholder="Search tickets..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="pl-10"
        />
      </div>

      {filteredTickets.length === 0 ? (
        <Card>
          <CardContent className="text-center py-12 text-muted-foreground">
            No tickets found
          </CardContent>
        </Card>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b">
                <th className="text-left py-3 px-4 font-medium">ID</th>
                <th className="text-left py-3 px-4 font-medium">Title</th>
                <th className="text-left py-3 px-4 font-medium">Status</th>
                <th className="text-left py-3 px-4 font-medium">Priority</th>
                <th className="text-left py-3 px-4 font-medium">Type</th>
                <th className="text-left py-3 px-4 font-medium">Assignee</th>
                <th className="text-left py-3 px-4 font-medium">Due Date</th>
              </tr>
            </thead>
            <tbody>
              {filteredTickets.map((ticket) => (
                <tr
                  key={ticket.id}
                  className="border-b last:border-0 hover:bg-muted/50 cursor-pointer"
                  onClick={() => navigate(`/tickets/${ticket.id}`)}
                >
                  <td className="py-3 px-4 text-muted-foreground">#{ticket.id}</td>
                  <td className="py-3 px-4">
                    <div className="font-medium line-clamp-1">{ticket.title}</div>
                  </td>
                  <td className="py-3 px-4" onClick={(e) => e.stopPropagation()}>
                    <Select
                      value={ticket.status}
                      onChange={(e) => handleStatusChange(ticket.id, e.target.value)}
                      options={statusOptions}
                      className="w-32"
                    />
                  </td>
                  <td className="py-3 px-4" onClick={(e) => e.stopPropagation()}>
                    <Select
                      value={ticket.priority}
                      onChange={(e) => handlePriorityChange(ticket.id, e.target.value)}
                      options={priorityOptions}
                      className="w-32"
                    />
                  </td>
                  <td className="py-3 px-4">
                    <Badge variant="outline">{ticket.type}</Badge>
                  </td>
                  <td className="py-3 px-4">
                    {ticket.assigneeId ? (
                      <div className="flex items-center gap-2">
                        <div className="h-6 w-6 rounded-full bg-primary/10 flex items-center justify-center">
                          <User className="h-3 w-3 text-primary" />
                        </div>
                        <span className="text-sm">{getAssignee(ticket.assigneeId)?.username}</span>
                      </div>
                    ) : (
                      <span className="text-muted-foreground text-sm">Unassigned</span>
                    )}
                  </td>
                  <td className="py-3 px-4">
                    {ticket.dueDate && (
                      <div className={cn(
                        'flex items-center gap-1 text-sm',
                        ticket.overdue && 'text-destructive'
                      )}>
                        <Clock className="h-3 w-3" />
                        <span>{format(parseISO(ticket.dueDate), 'MMM d, yyyy')}</span>
                        {ticket.overdue && <span className="text-danger">(Overdue)</span>}
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
