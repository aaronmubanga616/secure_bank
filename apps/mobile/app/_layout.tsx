import { Stack } from 'expo-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StatusBar } from 'expo-status-bar';
const client = new QueryClient();
export default function Layout() {
  return <QueryClientProvider client={client}><StatusBar style="dark"/><Stack screenOptions={{ headerStyle: { backgroundColor: '#f4f6f2' }, headerTintColor: '#10271f' }}/></QueryClientProvider>;
}
