/**
 * useCollections.ts — TanStack Query hooks for collections CRUD and book membership
 *
 * Hooks:
 *   useMyCollections()           — GET  /api/collections
 *   useCollectionDetail(id)      — GET  /api/collections/{id}
 *   useCreateCollection()        — POST /api/collections { name, isPublic }
 *   useUpdateCollection(id)      — PATCH /api/collections/{id}
 *   useDeleteCollection()        — DELETE /api/collections/{id}
 *   useAddBookToCollection()     — POST /api/collections/{id}/books { olKey }
 *   useRemoveBookFromCollection()— DELETE /api/collections/{id}/books/{olKey}
 */
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { QUERY_KEYS } from '../lib/queryKeys';
import type { CollectionDto, CollectionDetail } from '../types/api.types';

export function useMyCollections() {
  return useQuery({
    queryKey: QUERY_KEYS.collections(),
    queryFn: () => api.get<CollectionDto[]>('/collections').then((r) => r.data),
  });
}

export function useCollectionDetail(id: string) {
  return useQuery({
    queryKey: QUERY_KEYS.collectionDetail(id),
    queryFn: () =>
      api.get<CollectionDetail>('/collections/' + id).then((r) => r.data),
    enabled: !!id,
  });
}

export function useCreateCollection() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { name: string; isPublic: boolean }) =>
      api.post<CollectionDto>('/collections', body).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.collections() });
    },
  });
}

export function useUpdateCollection(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { name?: string; isPublic?: boolean }) =>
      api.patch<CollectionDto>('/collections/' + id, body).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.collections() });
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.collectionDetail(id) });
    },
  });
}

export function useDeleteCollection() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.delete('/collections/' + id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.collections() });
    },
  });
}

export function useAddBookToCollection() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ collectionId, olKey }: { collectionId: string; olKey: string }) =>
      api.post('/collections/' + collectionId + '/books', { olKey }),
    onSuccess: (_data, { collectionId }) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.collections() });
      queryClient.invalidateQueries({
        queryKey: QUERY_KEYS.collectionDetail(collectionId),
      });
    },
  });
}

export function useRemoveBookFromCollection() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ collectionId, olKey }: { collectionId: string; olKey: string }) =>
      api.delete('/collections/' + collectionId + '/books/' + olKey),
    onSuccess: (_data, { collectionId }) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.collections() });
      queryClient.invalidateQueries({
        queryKey: QUERY_KEYS.collectionDetail(collectionId),
      });
    },
  });
}
