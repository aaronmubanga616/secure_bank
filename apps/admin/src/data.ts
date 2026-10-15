import { useQuery } from '@tanstack/react-query';
import { api } from '@securebank/ui';
import { errorMessage } from '@securebank/contracts';
export function useData<T>(path: string, params?: Record<string, unknown>, enabled = true) {
  return useQuery({
    queryKey: [path, params],
    enabled,
    queryFn: async () => (await api.get<T>(path, { params })).data,
  });
}
export const errorText = errorMessage;
