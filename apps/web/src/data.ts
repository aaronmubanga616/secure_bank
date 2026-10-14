import { useQuery } from '@tanstack/react-query';
import { api } from '@securebank/ui';
export function useData<T>(path: string, params?: Record<string, unknown>) {
  return useQuery({
    queryKey: [path, params],
    queryFn: async () => (await api.get<T>(path, { params })).data,
  });
}
