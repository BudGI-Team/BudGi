// hooks/useTransactions.js
// FIX: backend envelope is { success, data: { transactions, pagination }, meta }
// All selectors updated to read from response.data (the envelope's data field)

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { transactionsApi } from '../lib/api';

const TXKEY = ['transactions'];

export function useTransactions(params = {}) {
  return useQuery({
    queryKey: [...TXKEY, params],
    queryFn: () => transactionsApi.list(params),
    // FIX: backend returns { success, data: { transactions }, meta: { pagination } }
    // axios interceptor returns r.data (the envelope), so we read .data.transactions
    select: (envelope) => envelope?.data?.transactions ?? [],
    staleTime: 30_000,
  });
}

export function useTransactionsPaginated(params = {}) {
  return useQuery({
    queryKey: [...TXKEY, 'paginated', params],
    queryFn: () => transactionsApi.list(params),
    // Return full envelope so caller can access both transactions and pagination meta
    select: (envelope) => ({
      transactions: envelope?.data?.transactions ?? [],
      pagination: envelope?.meta ?? {},
    }),
    staleTime: 30_000,
  });
}

export function useCreateTransaction() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: transactionsApi.create,
    onMutate: async (newTx) => {
      await qc.cancelQueries({ queryKey: TXKEY });
      // Get snapshot of all queries starting with TXKEY
      const previous = qc.getQueriesData({ queryKey: TXKEY });

      // Optimistically update all caches
      qc.setQueriesData({ queryKey: TXKEY }, (old) => {
        if (!old || !old.data || !old.data.transactions) return old;
        const tempTx = { ...newTx, _id: `temp-${Date.now()}` };
        return {
          ...old,
          data: {
            ...old.data,
            transactions: [tempTx, ...old.data.transactions],
          },
        };
      });

      return { previous };
    },
    onError: (err, newTx, context) => {
      // Rollback
      if (context?.previous) {
        context.previous.forEach(([key, data]) => qc.setQueryData(key, data));
      }
      toast.error(err.userMessage || 'Failed to add transaction');
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: TXKEY });
      qc.invalidateQueries({ queryKey: ['analytics'] });
    },
    onSuccess: () => {
      toast.success('Transaction added');
    },
  });
}

export function useBulkCreateTransactions() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: transactionsApi.bulkCreate,
    onSuccess: (envelope) => {
      qc.invalidateQueries({ queryKey: TXKEY });
      qc.invalidateQueries({ queryKey: ['analytics'] });
      // FIX: backend returns data.imported not data.count
      const { imported = 0, duplicates = 0 } = envelope?.data ?? {};
      const msg =
        duplicates > 0
          ? `${imported} imported, ${duplicates} duplicates skipped`
          : `${imported} transaction${imported !== 1 ? 's' : ''} imported`;
      toast.success(msg);
    },
    onError: (err) => toast.error(err.userMessage || 'Import failed'),
  });
}

export function useUpdateTransaction() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }) => transactionsApi.update(id, data),
    onMutate: async ({ id, data }) => {
      await qc.cancelQueries({ queryKey: TXKEY });
      const previous = qc.getQueriesData({ queryKey: TXKEY });

      qc.setQueriesData({ queryKey: TXKEY }, (old) => {
        if (!old || !old.data || !old.data.transactions) return old;
        return {
          ...old,
          data: {
            ...old.data,
            transactions: old.data.transactions.map((tx) => (tx._id === id ? { ...tx, ...data } : tx)),
          },
        };
      });

      return { previous };
    },
    onError: (err, req, context) => {
      if (context?.previous) {
        context.previous.forEach(([key, data]) => qc.setQueryData(key, data));
      }
      toast.error(err.userMessage || 'Update failed');
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: TXKEY });
      qc.invalidateQueries({ queryKey: ['analytics'] });
    },
    onSuccess: () => {
      toast.success('Transaction updated');
    },
  });
}

export function useDeleteTransaction() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: transactionsApi.remove,
    onMutate: async (id) => {
      await qc.cancelQueries({ queryKey: TXKEY });
      const previous = qc.getQueriesData({ queryKey: TXKEY });

      qc.setQueriesData({ queryKey: TXKEY }, (old) => {
        if (!old || !old.data || !old.data.transactions) return old;
        return {
          ...old,
          data: {
            ...old.data,
            transactions: old.data.transactions.filter((tx) => tx._id !== id),
          },
        };
      });

      return { previous };
    },
    onError: (err, id, context) => {
      if (context?.previous) {
        context.previous.forEach(([key, data]) => qc.setQueryData(key, data));
      }
      toast.error(err.userMessage || 'Delete failed');
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: TXKEY });
      qc.invalidateQueries({ queryKey: ['analytics'] });
    },
    onSuccess: () => {
      toast.success('Transaction deleted');
    },
  });
}

export function useTransactionSummary(params = {}) {
  return useQuery({
    queryKey: ['transactions', 'summary', params],
    queryFn: () => transactionsApi.summary(params),
    select: (envelope) => envelope?.data ?? {},
    staleTime: 60_000,
  });
}
