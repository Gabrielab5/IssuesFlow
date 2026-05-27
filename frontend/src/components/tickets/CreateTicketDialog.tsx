import React from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { ticketsApi } from '../../api';
import { useToast } from '../../contexts/ToastContext';
import type { TicketResponse, UserResponse } from '../../types/api';
import { Button } from '../ui/button';
import { Input } from '../ui/input';
import { Label } from '../ui/label';
import { Textarea } from '../ui/textarea';
import { Select } from '../ui/select';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '../ui/dialog';
import { LoadingSpinner } from '../ui/loading';
import DatePicker from 'react-datepicker';
import 'react-datepicker/dist/react-datepicker.css';

const createTicketSchema = z.object({
  title: z.string().min(1, 'Title is required').max(255, 'Title too long'),
  description: z.string().optional(),
  status: z.enum(['TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE']),
  priority: z.enum(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']),
  type: z.enum(['BUG', 'FEATURE', 'TECHNICAL']),
  assigneeId: z.number().optional().nullable(),
  dueDate: z.date().optional().nullable(),
});

type CreateTicketData = z.infer<typeof createTicketSchema>;

interface CreateTicketDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  projectId: number;
  users: UserResponse[];
  onCreated: (ticket: TicketResponse) => void;
}

export function CreateTicketDialog({
  open,
  onOpenChange,
  projectId,
  users,
  onCreated,
}: CreateTicketDialogProps) {
  const { showToast } = useToast();
  const [isSubmitting, setIsSubmitting] = React.useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors },
  } = useForm<CreateTicketData>({
    resolver: zodResolver(createTicketSchema),
    defaultValues: {
      status: 'TODO',
      priority: 'MEDIUM',
      type: 'FEATURE',
    },
  });

  const dueDate = watch('dueDate');

  React.useEffect(() => {
    if (!open) {
      reset({
        title: '',
        description: '',
        status: 'TODO',
        priority: 'MEDIUM',
        type: 'FEATURE',
        assigneeId: undefined,
        dueDate: undefined,
      });
    }
  }, [open, reset]);

  const onSubmit = async (data: CreateTicketData) => {
    setIsSubmitting(true);
    try {
      const payload = {
        title: data.title,
        description: data.description,
        status: data.status,
        priority: data.priority,
        type: data.type,
        projectId,
        assigneeId: data.assigneeId || undefined,
        dueDate: data.dueDate ? new Date(data.dueDate).toISOString() : undefined,
      };

      const ticket = await ticketsApi.create(payload);
      showToast('Ticket created successfully', 'success');
      onCreated(ticket);
    } catch (error: any) {
      const message = error.response?.data?.message || 'Failed to create ticket';
      showToast(message, 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Create New Ticket</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="title">Title</Label>
            <Input
              id="title"
              {...register('title')}
              disabled={isSubmitting}
            />
            {errors.title && (
              <p className="text-sm text-destructive">{errors.title.message}</p>
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

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label>Status</Label>
              <Select
                {...register('status')}
                disabled={isSubmitting}
                options={[
                  { value: 'TODO', label: 'To Do' },
                  { value: 'IN_PROGRESS', label: 'In Progress' },
                  { value: 'IN_REVIEW', label: 'In Review' },
                  { value: 'DONE', label: 'Done' },
                ]}
              />
            </div>

            <div className="space-y-2">
              <Label>Priority</Label>
              <Select
                {...register('priority')}
                disabled={isSubmitting}
                options={[
                  { value: 'LOW', label: 'Low' },
                  { value: 'MEDIUM', label: 'Medium' },
                  { value: 'HIGH', label: 'High' },
                  { value: 'CRITICAL', label: 'Critical' },
                ]}
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label>Type</Label>
              <Select
                {...register('type')}
                disabled={isSubmitting}
                options={[
                  { value: 'BUG', label: 'Bug' },
                  { value: 'FEATURE', label: 'Feature' },
                  { value: 'TECHNICAL', label: 'Technical' },
                ]}
              />
            </div>

            <div className="space-y-2">
              <Label>Assignee</Label>
              <Select
                {...register('assigneeId', { valueAsNumber: true })}
                disabled={isSubmitting}
                options={[
                  { value: '', label: 'Auto-assign' },
                  ...users.map(u => ({ value: String(u.id), label: u.fullName })),
                ]}
              />
            </div>
          </div>

          <div className="space-y-2">
            <Label>Due Date</Label>
            <DatePicker
              selected={dueDate}
              onChange={(date: Date | null) => setValue('dueDate', date)}
              className="w-full flex h-10 rounded-md border border-input bg-background px-3 py-2 text-sm"
              dateFormat="yyyy-MM-dd"
              placeholderText="Select due date"
            />
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={isSubmitting}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? <LoadingSpinner size="sm" /> : 'Create'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
