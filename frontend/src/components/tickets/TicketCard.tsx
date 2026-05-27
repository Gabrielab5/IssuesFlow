import { useDraggable } from '@dnd-kit/core';
import { useNavigate } from 'react-router-dom';
import type { TicketResponse, UserResponse, TicketPriority } from '../../types/api';
import { Badge } from '../ui/badge';
import { cn } from '../../lib/utils';
import { AlertCircle, Clock, User } from 'lucide-react';
import { format, parseISO } from 'date-fns';

interface TicketCardProps {
  ticket: TicketResponse;
  users: UserResponse[];
  isDragging?: boolean;
}

const priorityVariants: Record<TicketPriority, 'low' | 'medium' | 'high' | 'critical'> = {
  LOW: 'low',
  MEDIUM: 'medium',
  HIGH: 'high',
  CRITICAL: 'critical',
};

export function TicketCard({ ticket, users, isDragging }: TicketCardProps) {
  const navigate = useNavigate();
  const { attributes, listeners, setNodeRef, transform } = useDraggable({
    id: ticket.id,
    data: ticket,
  });

  const style = transform
    ? {
        transform: `translate3d(${transform.x}px, ${transform.y}px, 0)`,
      }
    : undefined;

  const assignee = users.find(u => u.id === ticket.assigneeId);

  return (
    <div
      ref={setNodeRef}
      style={style}
      {...listeners}
      {...attributes}
      onClick={() => navigate(`/tickets/${ticket.id}`)}
      className={cn(
        'bg-white p-3 rounded-lg shadow-sm border cursor-pointer hover:shadow-md transition-shadow',
        isDragging && 'shadow-xl rotate-2 scale-105',
        ticket.overdue && 'border-l-4 border-l-destructive'
      )}
    >
      <div className="flex items-start justify-between gap-2 mb-2">
        <h4 className="font-medium text-sm line-clamp-2 flex-1">{ticket.title}</h4>
        {ticket.overdue && (
          <AlertCircle className="h-4 w-4 text-destructive flex-shrink-0" />
        )}
      </div>

      <div className="flex items-center gap-2 flex-wrap mb-2">
        <Badge variant={priorityVariants[ticket.priority]} className="text-xs">
          {ticket.priority}
        </Badge>
        <Badge variant="outline" className="text-xs">
          {ticket.type}
        </Badge>
      </div>

      <div className="flex items-center justify-between text-xs text-muted-foreground">
        <div className="flex items-center gap-2">
          {assignee ? (
            <div className="flex items-center gap-1">
              <div className="h-5 w-5 rounded-full bg-primary/10 flex items-center justify-center">
                <User className="h-3 w-3 text-primary" />
              </div>
              <span className="truncate max-w-[80px]">{assignee.username}</span>
            </div>
          ) : (
            <span className="text-muted-foreground/50">Unassigned</span>
          )}
        </div>

        {ticket.dueDate && (
          <div className={cn(
            'flex items-center gap-1',
            ticket.overdue && 'text-destructive'
          )}>
            <Clock className="h-3 w-3" />
            <span>{format(parseISO(ticket.dueDate), 'MMM d')}</span>
          </div>
        )}
      </div>

      {ticket.description && (
        <p className="text-xs text-muted-foreground mt-2 line-clamp-2">
          {ticket.description}
        </p>
      )}
    </div>
  );
}
