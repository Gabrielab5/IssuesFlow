import { useDroppable } from '@dnd-kit/core';
import { TicketCard } from './TicketCard';
import type { TicketResponse, UserResponse, TicketStatus } from '../../types/api';
import { cn } from '../../lib/utils';

interface KanbanColumnProps {
  id: TicketStatus;
  title: string;
  tickets: TicketResponse[];
  users: UserResponse[];
  color: string;
}

export function KanbanColumn({ id, title, tickets, users, color }: KanbanColumnProps) {
  const { setNodeRef, isOver } = useDroppable({ id });

  return (
    <div
      ref={setNodeRef}
      className={cn(
        'flex flex-col rounded-lg min-h-[500px] transition-colors',
        color,
        isOver && 'ring-2 ring-primary ring-offset-2'
      )}
    >
      <div className="flex items-center justify-between p-3 border-b bg-white/50 rounded-t-lg">
        <h3 className="font-semibold text-sm">{title}</h3>
        <span className="text-sm text-muted-foreground bg-white px-2 py-0.5 rounded-full">
          {tickets.length}
        </span>
      </div>

      <div className="flex-1 p-2 space-y-2 overflow-y-auto">
        {tickets.map((ticket) => (
          <TicketCard key={ticket.id} ticket={ticket} users={users} />
        ))}

        {tickets.length === 0 && (
          <div className="text-center text-sm text-muted-foreground py-8">
            No tickets
          </div>
        )}
      </div>
    </div>
  );
}
