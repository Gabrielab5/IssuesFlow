import React from 'react';
import { useParams } from 'react-router-dom';
import { usersApi } from '../api';
import { useToast } from '../contexts/ToastContext';
import type { MentionResponse, PagedResponseMentionResponse } from '../types/api';
import { Card, CardContent } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { PageLoader } from '../components/ui/loading';
import { format, parseISO } from 'date-fns';
import { Bell, ChevronLeft, ChevronRight, ArrowRight } from 'lucide-react';

export function MentionsPage() {
  const { userId } = useParams<{ userId: string }>();
  const { showToast } = useToast();

  const [mentions, setMentions] = React.useState<PagedResponseMentionResponse | null>(null);
  const [isLoading, setIsLoading] = React.useState(true);
  const [page, setPage] = React.useState(1);
  const pageSize = 20;

  const fetchMentions = React.useCallback(async () => {
    if (!userId) return;

    try {
      const data = await usersApi.getMentions(Number(userId), page, pageSize);
      setMentions(data);
    } catch (error) {
      showToast('Failed to load mentions', 'error');
    } finally {
      setIsLoading(false);
    }
  }, [userId, page, showToast]);

  React.useEffect(() => {
    fetchMentions();
  }, [fetchMentions]);

  if (isLoading) {
    return <PageLoader />;
  }

  const totalPages = mentions ? Math.ceil(mentions.total / pageSize) : 0;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold flex items-center gap-2">
          <Bell className="h-8 w-8" />
          Mentions
        </h1>
        <p className="text-muted-foreground">Comments where you were mentioned</p>
      </div>

      <Card>
        <CardContent className="pt-6">
          {!mentions || mentions.data.length === 0 ? (
            <div className="text-center py-12 text-muted-foreground">
              <Bell className="h-12 w-12 mx-auto mb-4 opacity-50" />
              <p>No mentions yet</p>
            </div>
          ) : (
            <div className="space-y-4">
              {mentions.data.map((mention) => (
                <MentionCard key={mention.id} mention={mention} />
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      {mentions && totalPages > 1 && (
        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">
            Page {page} of {totalPages} ({mentions.total} total)
          </p>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={page === 1}
              onClick={() => setPage(page - 1)}
            >
              <ChevronLeft className="h-4 w-4" />
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={page === totalPages}
              onClick={() => setPage(page + 1)}
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

function MentionCard({ mention }: { mention: MentionResponse }) {
  return (
    <div className="border rounded-lg p-4 hover:shadow-sm transition-shadow">
      <div className="flex items-start justify-between mb-2">
        <div className="flex items-center gap-2 text-sm">
          <span className="font-medium">{mention.authorUsername}</span>
          <span className="text-muted-foreground">mentioned you in</span>
          <a
            href={`/tickets/${mention.ticketId}`}
            className="text-primary hover:underline flex items-center gap-1"
          >
            Ticket #{mention.ticketId}
            <ArrowRight className="h-3 w-3" />
          </a>
        </div>
        <span className="text-xs text-muted-foreground">
          {format(parseISO(mention.mentionedAt), 'MMM d, yyyy h:mm a')}
        </span>
      </div>
      <p className="text-sm bg-muted p-3 rounded line-clamp-3">
        {mention.commentContent}
      </p>
    </div>
  );
}
