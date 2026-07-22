/**
 * CollectionsPage.tsx — Manage named book collections (COLL-01/02/03/04)
 *
 * Features:
 * - Inline "New collection" form (name + public/private toggle)
 * - List of CollectionDto cards with book count and visibility badge
 * - Inline rename via pencil icon
 * - Delete via confirm Dialog (T-06-11 pattern — no window.confirm)
 * - Empty state when no collections exist
 *
 * Route: /collections (ProtectedRoute)
 */
import { useState } from 'react';
import { Pencil, Trash2, Check, X } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { Badge } from '../../components/ui/badge';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '../../components/ui/dialog';
import {
  useMyCollections,
  useCreateCollection,
  useUpdateCollection,
  useDeleteCollection,
} from '../../hooks/useCollections';
import type { CollectionDto } from '../../types/api.types';

// ── Inline rename row ────────────────────────────────────

function CollectionCard({ collection }: { collection: CollectionDto }) {
  const [editing, setEditing] = useState(false);
  const [renameValue, setRenameValue] = useState(collection.name);
  const [deleteOpen, setDeleteOpen] = useState(false);

  const updateCollection = useUpdateCollection(collection.id);
  const deleteCollection = useDeleteCollection();

  const handleRename = () => {
    const trimmed = renameValue.trim();
    if (!trimmed || trimmed === collection.name) {
      setEditing(false);
      return;
    }
    updateCollection.mutate(
      { name: trimmed },
      { onSuccess: () => setEditing(false) },
    );
  };

  const handleDelete = () => {
    deleteCollection.mutate(collection.id, {
      onSuccess: () => setDeleteOpen(false),
    });
  };

  return (
    <div className="flex items-center justify-between gap-3 rounded-lg border bg-card p-3">
      <div className="flex-1 min-w-0">
        {editing ? (
          <div className="flex items-center gap-2">
            <Input
              value={renameValue}
              onChange={(e) => setRenameValue(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleRename();
                if (e.key === 'Escape') setEditing(false);
              }}
              className="h-7 text-sm"
              autoFocus
            />
            <Button
              variant="ghost"
              size="icon"
              className="h-7 w-7 shrink-0"
              onClick={handleRename}
              disabled={updateCollection.isPending}
              aria-label="Save rename"
            >
              <Check className="h-4 w-4" />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              className="h-7 w-7 shrink-0"
              onClick={() => setEditing(false)}
              aria-label="Cancel rename"
            >
              <X className="h-4 w-4" />
            </Button>
          </div>
        ) : (
          <div className="flex flex-col gap-1">
            <p className="font-semibold text-sm leading-tight truncate">
              {collection.name}
            </p>
            <div className="flex items-center gap-2">
              <span className="text-xs text-muted-foreground">
                {collection.bookCount} {collection.bookCount === 1 ? 'book' : 'books'}
              </span>
              <Badge variant={collection.isPublic ? 'secondary' : 'outline'} className="text-xs px-1.5 py-0">
                {collection.isPublic ? 'Public' : 'Private'}
              </Badge>
            </div>
          </div>
        )}
      </div>

      {!editing && (
        <div className="flex items-center gap-1 shrink-0">
          <Button
            variant="ghost"
            size="icon"
            className="h-8 w-8"
            onClick={() => {
              setRenameValue(collection.name);
              setEditing(true);
            }}
            aria-label={`Rename ${collection.name}`}
          >
            <Pencil className="h-4 w-4" />
          </Button>

          <Dialog open={deleteOpen} onOpenChange={setDeleteOpen}>
            <DialogTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                className="h-8 w-8 text-destructive hover:text-destructive"
                aria-label={`Delete ${collection.name}`}
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Delete collection?</DialogTitle>
                <DialogDescription>
                  &ldquo;{collection.name}&rdquo; will be permanently deleted. Books on your
                  shelf are not affected.
                </DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <Button
                  variant="destructive"
                  onClick={handleDelete}
                  disabled={deleteCollection.isPending}
                >
                  Delete
                </Button>
                <Button variant="outline" onClick={() => setDeleteOpen(false)}>
                  Cancel
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </div>
      )}
    </div>
  );
}

// ── CollectionsPage ──────────────────────────────────────

export function CollectionsPage() {
  const [newName, setNewName] = useState('');
  const [isPublic, setIsPublic] = useState(false);

  const { data: collections, isPending, isError } = useMyCollections();
  const createCollection = useCreateCollection();

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = newName.trim();
    if (!trimmed) return;
    createCollection.mutate(
      { name: trimmed, isPublic },
      {
        onSuccess: () => {
          setNewName('');
          setIsPublic(false);
        },
      },
    );
  };

  return (
    <div className="pb-4">
      <h1 className="text-[28px] font-semibold mb-4">My Collections</h1>

      {/* Create form */}
      <form onSubmit={handleCreate} className="mb-6 space-y-3">
        <div className="flex gap-2">
          <Input
            placeholder="New collection name"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            className="flex-1"
          />
          <Button
            type="submit"
            disabled={!newName.trim() || createCollection.isPending}
          >
            Create
          </Button>
        </div>
        <label className="flex items-center gap-2 text-sm cursor-pointer select-none">
          <input
            type="checkbox"
            checked={isPublic}
            onChange={(e) => setIsPublic(e.target.checked)}
            className="h-4 w-4 rounded border"
          />
          Make this collection public
        </label>
        {createCollection.isError && (
          <p className="text-sm text-destructive">
            Could not create collection. Please try again.
          </p>
        )}
      </form>

      {/* List */}
      {isPending ? (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-16 rounded-lg bg-muted animate-pulse" />
          ))}
        </div>
      ) : isError ? (
        <p className="text-sm text-destructive text-center py-8">
          Could not load collections. Check your connection and try again.
        </p>
      ) : collections && collections.length > 0 ? (
        <div className="space-y-3">
          {collections.map((col) => (
            <CollectionCard key={col.id} collection={col} />
          ))}
        </div>
      ) : (
        <div className="flex flex-col items-center gap-2 py-12 text-center">
          <p className="text-base font-semibold">No collections yet</p>
          <p className="text-sm text-muted-foreground max-w-xs">
            Create one above to start organising your books into named lists.
          </p>
        </div>
      )}
    </div>
  );
}
