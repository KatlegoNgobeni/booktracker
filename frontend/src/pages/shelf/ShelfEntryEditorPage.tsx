/**
 * ShelfEntryEditorPage.tsx — Full shelf entry editor (D-11)
 *
 * Groups:
 *   1. Status + current page (top — most frequent edit)
 *   2. Rating (interactive StarRating, 44px targets) + review (textarea)
 *   3. Date started / date finished (bottom — rarely edited manually)
 *
 * Two separate save actions keep the backend endpoint split clean:
 *   - "Save Changes"    → PATCH /shelf/:id   (metadata: status, rating, review, dates)
 *   - "Update Progress" → PATCH /shelf/:id/progress (only currentPage)
 *
 * Security:
 *   T-06-08: 403 from GET /shelf/:id → renders access-denied UI, not a crash
 *   T-06-09: metadata PATCH body NEVER includes currentPage (mass-assignment guard)
 *   T-06-10: review rendered as React text node — no dangerouslySetInnerHTML
 *   T-06-11: remove confirmation uses shadcn <Dialog>, NOT window.confirm
 */
import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  useShelfEntry,
  useUpdateShelfMetadata,
  useUpdateProgress,
  useRemoveShelfEntry,
} from '../../hooks/useShelf';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { Separator } from '../../components/ui/separator';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '../../components/ui/dialog';
import { StarRating } from '../../components/shared/StarRating';
import type { ShelfStatus } from '../../types/api.types';

// ────────────────────────────────────────────────────────
// Form schema (PATTERNS.md shelfEditSchema)
// ────────────────────────────────────────────────────────

const shelfEditSchema = z.object({
  status: z.enum(['WANT_TO_READ', 'CURRENTLY_READING', 'READ']),
  // valueAsNumber in register() coerces the string to number before zod sees it
  currentPage: z.number().min(0).optional(),
  rating: z.number().min(1).max(5).nullable().optional(),
  review: z.string().max(2000).optional(),
  dateStarted: z.string().optional(),
  dateFinished: z.string().optional(),
});

type ShelfEditForm = z.infer<typeof shelfEditSchema>;

// ────────────────────────────────────────────────────────
// ShelfEntryEditorPage
// ────────────────────────────────────────────────────────

export function ShelfEntryEditorPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [removeOpen, setRemoveOpen] = useState(false);

  const entryQuery = useShelfEntry(id ?? '');
  const updateMetadata = useUpdateShelfMetadata(id ?? '');
  const updateProgress = useUpdateProgress(id ?? '');
  const removeEntry = useRemoveShelfEntry(id ?? '');

  // ──── 403 / load error ────────────────────────────────
  if (entryQuery.isError) {
    const status = (entryQuery.error as { response?: { status?: number } })
      ?.response?.status;

    if (status === 403) {
      return (
        <div className="flex flex-col items-center gap-4 px-4 pt-16 text-center">
          <p className="text-base font-semibold">
            You don&apos;t have access to this entry.
          </p>
          <Button variant="outline" onClick={() => navigate(-1)}>
            Back
          </Button>
        </div>
      );
    }

    // Generic load error
    return (
      <div className="flex flex-col items-center gap-3 px-4 pt-16 text-center">
        <p className="text-sm text-muted-foreground">
          Couldn&apos;t load this entry. Try again.
        </p>
        <Button variant="outline" onClick={() => entryQuery.refetch()}>
          Retry
        </Button>
      </div>
    );
  }

  // ──── Skeleton while loading ──────────────────────────
  if (entryQuery.isPending) {
    return (
      <div className="flex flex-col gap-4 px-4 pt-4">
        {[1, 2, 3, 4].map((i) => (
          <div key={i} className="h-10 rounded bg-muted animate-pulse" />
        ))}
      </div>
    );
  }

  const entry = entryQuery.data;

  return (
    <EditorForm
      entry={entry}
      updateMetadata={updateMetadata}
      updateProgress={updateProgress}
      removeEntry={removeEntry}
      removeOpen={removeOpen}
      setRemoveOpen={setRemoveOpen}
      navigate={navigate}
    />
  );
}

// ────────────────────────────────────────────────────────
// EditorForm — isolated so hooks are called before entry is
// available (avoids conditional hook calls)
// ────────────────────────────────────────────────────────

interface EditorFormProps {
  entry: {
    title: string;
    authors: string | null;
    status: ShelfStatus;
    currentPage: number | null;
    pageCount?: number | null;
    rating: number | null;
    review: string | null;
    dateStarted: string | null;
    dateFinished: string | null;
  };
  updateMetadata: ReturnType<typeof useUpdateShelfMetadata>;
  updateProgress: ReturnType<typeof useUpdateProgress>;
  removeEntry: ReturnType<typeof useRemoveShelfEntry>;
  removeOpen: boolean;
  setRemoveOpen: (open: boolean) => void;
  navigate: ReturnType<typeof useNavigate>;
}

function EditorForm({
  entry,
  updateMetadata,
  updateProgress,
  removeEntry,
  removeOpen,
  setRemoveOpen,
  navigate,
}: EditorFormProps) {
  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors },
  } = useForm<ShelfEditForm>({
    resolver: zodResolver(shelfEditSchema),
    defaultValues: {
      status: entry.status,
      currentPage: entry.currentPage ?? undefined,
      rating: entry.rating,
      review: entry.review ?? '',
      dateStarted: entry.dateStarted ?? '',
      dateFinished: entry.dateFinished ?? '',
    },
  });

  const ratingValue = watch('rating') ?? null;
  const statusValue = watch('status');

  // ── Metadata save (no currentPage) ──────────────────
  function onSaveMetadata(data: ShelfEditForm) {
    updateMetadata.mutate(
      {
        status: data.status,
        rating: data.rating ?? null,
        review: data.review ?? null,
        dateStarted: data.dateStarted ?? null,
        dateFinished: data.dateFinished ?? null,
      },
      {
        onSuccess: () => navigate(-1),
      },
    );
  }

  // ── Progress update (only currentPage) ─────────────
  function onUpdateProgress() {
    const currentPage = Number(watch('currentPage') ?? 0);
    updateProgress.mutate(
      { currentPage },
      { onSuccess: () => { /* stay on page, data refetched */ } },
    );
  }

  // ── Remove ──────────────────────────────────────────
  function onConfirmRemove() {
    removeEntry.mutate(undefined, {
      onSuccess: () => {
        setRemoveOpen(false);
        navigate('/shelf');
      },
    });
  }

  return (
    <div className="flex flex-col gap-6 px-4 pt-4 pb-8">
      {/* Header */}
      <div>
        <h1 className="text-xl font-semibold leading-tight">{entry.title}</h1>
        {entry.authors && (
          <p className="text-sm text-muted-foreground mt-0.5">{entry.authors}</p>
        )}
      </div>

      <form onSubmit={handleSubmit(onSaveMetadata)} className="flex flex-col gap-6">
        {/* ── Group 1: Status + Current Page ─────────── */}
        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="status">Status</Label>
            <select
              id="status"
              {...register('status')}
              className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm shadow-xs transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
            >
              <option value="WANT_TO_READ">Want to Read</option>
              <option value="CURRENTLY_READING">Currently Reading</option>
              <option value="READ">Read</option>
            </select>
            {errors.status && (
              <p className="text-sm text-destructive">{errors.status.message}</p>
            )}
          </div>

          {statusValue === 'CURRENTLY_READING' && (
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="currentPage">Current Page</Label>
              <div className="flex gap-2">
                <Input
                  id="currentPage"
                  type="number"
                  min={0}
                  placeholder="e.g. 120"
                  {...register('currentPage', { valueAsNumber: true })}
                  className="flex-1"
                />
                <Button
                  type="button"
                  variant="outline"
                  onClick={onUpdateProgress}
                  disabled={updateProgress.isPending}
                >
                  {updateProgress.isPending ? 'Saving…' : 'Update Progress'}
                </Button>
              </div>
              {entry.pageCount && (
                <p className="text-xs text-muted-foreground">
                  of {entry.pageCount} pages
                </p>
              )}
              {updateProgress.isError && (
                <p className="text-sm text-destructive">
                  Progress not saved. Please try again.
                </p>
              )}
            </div>
          )}
        </div>

        <Separator />

        {/* ── Group 2: Rating + Review ────────────────── */}
        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label>Rating</Label>
            <StarRating
              value={ratingValue}
              onChange={(r) => setValue('rating', r)}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="review">Review</Label>
            <textarea
              id="review"
              rows={4}
              placeholder="What did you think?"
              {...register('review')}
              className="flex w-full rounded-md border border-input bg-background px-3 py-2 text-sm shadow-xs placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring resize-none"
            />
            {errors.review && (
              <p className="text-sm text-destructive">{errors.review.message}</p>
            )}
          </div>
        </div>

        <Separator />

        {/* ── Group 3: Dates ──────────────────────────── */}
        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="dateStarted">Date Started</Label>
            <Input
              id="dateStarted"
              type="date"
              {...register('dateStarted')}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="dateFinished">Date Finished</Label>
            <Input
              id="dateFinished"
              type="date"
              {...register('dateFinished')}
            />
          </div>
        </div>

        {/* ── Metadata save error ─────────────────────── */}
        {updateMetadata.isError && (
          <p className="text-sm text-destructive text-center">
            Changes not saved. Please try again.
          </p>
        )}

        {/* ── Save Changes CTA ─────────────────────────── */}
        <Button
          type="submit"
          disabled={updateMetadata.isPending}
          className="w-full"
        >
          {updateMetadata.isPending ? 'Saving…' : 'Save Changes'}
        </Button>
      </form>

      <Separator />

      {/* ── Remove (destructive, Dialog — T-06-11) ─────── */}
      <Dialog open={removeOpen} onOpenChange={setRemoveOpen}>
        <DialogTrigger asChild>
          <Button variant="destructive" className="w-full">
            Remove from Shelf
          </Button>
        </DialogTrigger>
        <DialogContent showCloseButton={false}>
          <DialogHeader>
            <DialogTitle>Remove this book from your shelf?</DialogTitle>
            <DialogDescription>
              Your progress, rating, and review will be deleted.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setRemoveOpen(false)}
              disabled={removeEntry.isPending}
            >
              Keep Book
            </Button>
            <Button
              variant="destructive"
              onClick={onConfirmRemove}
              disabled={removeEntry.isPending}
            >
              {removeEntry.isPending ? 'Removing…' : 'Remove Book'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
