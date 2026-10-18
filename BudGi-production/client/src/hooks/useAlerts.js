import { useQuery } from '@tanstack/react-query';
import { alertsApi } from '../lib/api';

export function useUnreadAlerts() {
  const query = useQuery({
    queryKey: ['alerts-count'],
    queryFn: alertsApi.unreadCount,
    refetchInterval: 30_000,
    staleTime: 15_000,
  });

  return {
    ...query,
    unreadCount: query.data?.data?.count ?? 0,
  };
}
