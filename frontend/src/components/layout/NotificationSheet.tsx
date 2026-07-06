/**
 * NotificationSheet.tsx — Bottom-sheet notification inbox (Phase 9 — NOTIF-03, D-08)
 *
 * Opens from the AppHeader bell. Fetches the notification list on open (lazy — enabled:false
 * in useNotifications so the list only fetches when the inbox is actually opened).
 * All notifications are marked as read by the parent (AppHeader) on open (D-08).
 *
 * Icon map per notification type (UI-SPEC section 2):
 *   FRIEND_REQUEST       → UserPlus
 *   FRIEND_ACCEPTED      → UserCheck
 *   FRIEND_FINISHED_BOOK → BookOpen
 *   REVIEW_LIKED         → Heart
 *
 * Copy strategy: text composed from type + actorDisplayName only.
 * Note: NotificationDto does not carry a book title (entityId is a UUID, not a readable name).
 * FRIEND_FINISHED_BOOK and REVIEW_LIKED use shortened copy ("finished a book" / "liked your review")
 * rather than the full UI-SPEC "[name] finished [book title]" strings — tracked as a known stub
 * until the backend DTO is extended with an entityTitle field.
 *
 * Security notes:
 * - T-09-21: All user-provided strings rendered via JSX text interpolation only — no dangerouslySetInnerHTML.
 */
import { useEffect } from 'react';
import { Bell, Loader2, UserPlus, UserCheck, BookOpen, Heart } from 'lucide-react';
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from '../ui/sheet';
import { useNotifications } from '../../hooks/useNotifications';
import { formatRelativeDate } from '../../lib/utils';
import type { NotificationType, NotificationDto } from '../../types/api.types';

/** Per-type Lucide icon (UI-SPEC section 2 icon map) */
const TYPE_ICON: Record<NotificationType, React.ComponentType<{ className?: string; 'aria-hidden'?: boolean | 'true' | 'false' }>> = {
  FRIEND_REQUEST: UserPlus,
  FRIEND_ACCEPTED: UserCheck,
  FRIEND_FINISHED_BOOK: BookOpen,
  REVIEW_LIKED: Heart,
};

/**
 * Compose the human-readable notification text from type + actorDisplayName.
 * UI-SPEC Copywriting Contract strings (adapted — no book title in DTO).
 */
function getNotificationText(notif: NotificationDto): string {
  switch (notif.type) {
    case 'FRIEND_REQUEST':
      return `${notif.actorDisplayName} sent you a friend request`;
    case 'FRIEND_ACCEPTED':
      return `${notif.actorDisplayName} accepted your friend request`;
    case 'FRIEND_FINISHED_BOOK':
      // DTO has no book title; simplified from "[name] finished [book title]"
      return `${notif.actorDisplayName} finished a book`;
    case 'REVIEW_LIKED':
      // DTO has no book title; simplified from "[name] liked your review of [book title]"
      return `${notif.actorDisplayName} liked your review`;
    default:
      return `${notif.actorDisplayName} sent you a notification`;
  }
}

interface NotificationSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function NotificationSheet({ open, onOpenChange }: NotificationSheetProps) {
  const { data: notifications = [], isFetching, error, refetch } = useNotifications();

  // Trigger a fetch whenever the sheet opens. refetchQueries from markAllRead.onSuccess
  // fires before this hook is mounted/registered on first open, so we need a local trigger.
  useEffect(() => {
    if (open) refetch();
  }, [open, refetch]);

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="bottom" className="max-h-[75vh] flex flex-col px-0 pb-4">
        <SheetHeader className="px-4 pb-2 border-b">
          <SheetTitle>Notifications</SheetTitle>
        </SheetHeader>

        <div className="flex-1 overflow-y-auto">
          {isFetching && notifications.length === 0 ? (
            /* Loading state — shown on first open while the GET is in flight */
            <div className="flex items-center justify-center py-8 px-4">
              <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" aria-label="Loading notifications" />
            </div>
          ) : error && notifications.length === 0 ? (
            /* Error state — shown when the fetch failed and there is no cached data */
            <div className="flex flex-col items-center justify-center py-8 px-4 gap-2">
              <p className="text-sm text-muted-foreground text-center">Could not load notifications.</p>
              <button
                onClick={() => refetch()}
                className="text-xs text-primary underline"
              >
                Retry
              </button>
            </div>
          ) : notifications.length === 0 ? (
            /* Empty state (UI-SPEC section 2) */
            <div className="flex items-center justify-center py-8 px-4">
              <p className="text-sm text-muted-foreground text-center">No notifications yet.</p>
            </div>
          ) : (
            notifications.map((notif) => {
              const Icon = TYPE_ICON[notif.type] ?? Bell;
              return (
                <div
                  key={notif.id}
                  className="flex items-start gap-3 px-4 py-3 border-b last:border-b-0"
                >
                  {/* Per-type icon in a circular muted chip */}
                  <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-muted text-muted-foreground">
                    <Icon className="h-4 w-4" aria-hidden="true" />
                  </div>

                  {/* Notification text + relative timestamp */}
                  <div className="flex flex-col gap-1 min-w-0">
                    <p className="text-sm text-foreground">{getNotificationText(notif)}</p>
                    <p className="text-xs text-muted-foreground">
                      {formatRelativeDate(notif.createdAt)}
                    </p>
                  </div>
                </div>
              );
            })
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}
