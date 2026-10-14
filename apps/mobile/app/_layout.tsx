import { Stack } from 'expo-router';
import { useEffect } from 'react';
import { AppState } from 'react-native';
import { QueryClientProvider } from '@tanstack/react-query';
import { StatusBar } from 'expo-status-bar';
import { initializeSession, lockSession, queryClient, useSession } from '../src/session';
export default function Layout() {
  const state = useSession();
  useEffect(() => {
    void initializeSession();
    const listener = AppState.addEventListener('change', (value) => {
      if (value === 'background') lockSession();
    });
    return () => listener.remove();
  }, []);
  return (
    <QueryClientProvider client={queryClient}>
      <StatusBar style="dark" />
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: '#f5f6f0' },
          headerTintColor: '#234433',
          headerShadowVisible: false,
        }}
      >
        <Stack.Screen name="index" options={{ headerShown: false }} />
        <Stack.Protected guard={state.ready && !state.session && !state.locked}>
          <Stack.Screen name="auth" options={{ title: 'SecureBank' }} />
        </Stack.Protected>
        <Stack.Protected guard={state.ready && state.locked}>
          <Stack.Screen name="unlock" options={{ headerShown: false }} />
        </Stack.Protected>
        <Stack.Protected guard={state.ready && !!state.session && !state.locked}>
          <Stack.Screen name="(bank)" options={{ headerShown: false }} />
        </Stack.Protected>
      </Stack>
    </QueryClientProvider>
  );
}
