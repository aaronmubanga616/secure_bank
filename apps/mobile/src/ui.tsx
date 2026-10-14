import { useState, type ReactNode } from 'react';
import {
  ScrollView,
  View,
  Text,
  TextInput,
  Pressable,
  StyleSheet,
  ActivityIndicator,
  RefreshControl,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router, type Href } from 'expo-router';
import { useQuery } from '@tanstack/react-query';
import { errorMessage } from '@securebank/contracts';
import { api, queryClient } from './session';
export const go = (path: string) => router.push(path as Href);
export function useData<T>(path: string, params?: Record<string, unknown>) {
  return useQuery({
    queryKey: [path, params],
    queryFn: async () => (await api.get<T>(path, { params })).data,
  });
}
export function Screen({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle?: string;
  children: ReactNode;
}) {
  const [refreshing, setRefreshing] = useState(false);
  return (
    <SafeAreaView style={styles.safe} edges={['left', 'right', 'bottom']}>
      <ScrollView
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={styles.page}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={() => {
              setRefreshing(true);
              void queryClient.invalidateQueries().finally(() => setRefreshing(false));
            }}
          />
        }
      >
        <Text style={styles.eyebrow}>SECUREBANK / PERSONAL</Text>
        <Text style={styles.title}>{title}</Text>
        {subtitle && <Text style={styles.subtitle}>{subtitle}</Text>}
        {children}
        <Text style={styles.footer}>Educational simulator · Simulated ZMW</Text>
      </ScrollView>
    </SafeAreaView>
  );
}
export function Card({ children, dark = false }: { children: ReactNode; dark?: boolean }) {
  return <View style={[styles.card, dark && styles.dark]}>{children}</View>;
}
export function Heading({ children, light = false }: { children: ReactNode; light?: boolean }) {
  return <Text style={[styles.heading, light && { color: '#e6f1cf' }]}>{children}</Text>;
}
export function Copy({
  children,
  selectable = false,
}: {
  children: ReactNode;
  selectable?: boolean;
}) {
  return (
    <Text selectable={selectable} style={styles.copy}>
      {children}
    </Text>
  );
}
export function Notice({ children, error = false }: { children: ReactNode; error?: boolean }) {
  return (
    <Text accessibilityRole="alert" style={[styles.notice, error && styles.error]}>
      {children}
    </Text>
  );
}
export function ErrorState({ error }: { error: unknown }) {
  return error ? <Notice error>{errorMessage(error)}</Notice> : null;
}
export function Loading() {
  return (
    <ActivityIndicator
      size="large"
      color="#244f3c"
      style={{ margin: 24 }}
      accessibilityLabel="Loading"
    />
  );
}
export function Button({
  title,
  onPress,
  secondary = false,
  disabled = false,
}: {
  title: string;
  onPress: () => void;
  secondary?: boolean;
  disabled?: boolean;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      onPress={onPress}
      disabled={disabled}
      style={({ pressed }) => [
        styles.button,
        secondary && styles.secondary,
        (disabled || pressed) && { opacity: 0.6 },
      ]}
    >
      <Text style={[styles.buttonText, secondary && { color: '#31523b' }]}>{title}</Text>
    </Pressable>
  );
}
export interface InputField {
  name: string;
  label: string;
  secure?: boolean;
  numeric?: boolean;
  multiline?: boolean;
  initial?: string;
  options?: { value: string; label: string }[];
  optional?: boolean;
}
export function Form({
  fields,
  submit,
  action,
  children,
}: {
  fields: InputField[];
  submit: string;
  action: (values: Record<string, string>) => Promise<string | void>;
  children?: ReactNode;
}) {
  const [values, setValues] = useState<Record<string, string>>(() =>
    Object.fromEntries(fields.map((field) => [field.name, field.initial ?? ''])),
  );
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState(false);
  async function send() {
    setMessage('');
    if (fields.some((field) => !field.optional && !values[field.name]?.trim())) {
      setError(true);
      setMessage('Complete each required field.');
      return;
    }
    setBusy(true);
    try {
      const result = await action(values);
      setError(false);
      setMessage(result ?? 'Changes saved.');
      await queryClient.invalidateQueries();
    } catch (failure) {
      setError(true);
      setMessage(
        failure instanceof Error && !('isAxiosError' in failure)
          ? failure.message
          : errorMessage(failure),
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <View style={{ gap: 18 }}>
      {fields.map((field) => (
        <View key={field.name} style={{ gap: 8 }}>
          <Text style={styles.label}>
            {field.label}
            {field.optional ? ' (optional)' : ''}
          </Text>
          {field.options ? (
            <View style={{ gap: 8 }}>
              {field.options.map((option) => (
                <Pressable
                  accessibilityRole="radio"
                  accessibilityState={{
                    checked: values[field.name] === option.value,
                    disabled: busy,
                  }}
                  accessibilityLabel={option.label}
                  disabled={busy}
                  onPress={() => setValues({ ...values, [field.name]: option.value })}
                  style={[styles.choice, values[field.name] === option.value && styles.selected]}
                  key={option.value}
                >
                  <Text style={styles.copy}>
                    {values[field.name] === option.value ? '●' : '○'} {option.label}
                  </Text>
                </Pressable>
              ))}
            </View>
          ) : (
            <TextInput
              accessibilityLabel={field.label}
              value={values[field.name] ?? ''}
              onChangeText={(text) => setValues({ ...values, [field.name]: text })}
              secureTextEntry={field.secure}
              keyboardType={
                field.numeric
                  ? 'decimal-pad'
                  : field.name.toLowerCase().includes('email')
                    ? 'email-address'
                    : 'default'
              }
              autoCapitalize="none"
              autoCorrect={false}
              textContentType={
                field.secure ? 'password' : field.name === 'email' ? 'emailAddress' : 'none'
              }
              editable={!busy}
              style={styles.input}
              multiline={field.multiline}
            />
          )}
        </View>
      ))}
      {children}
      <Button
        title={busy ? 'Working…' : submit}
        disabled={busy}
        onPress={() => {
          void send();
        }}
      />
      {message && <Notice error={error}>{message}</Notice>}
    </View>
  );
}
export const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#f5f6f0' },
  page: { padding: 23, gap: 20, paddingBottom: 35 },
  eyebrow: { fontSize: 9, fontWeight: '700', letterSpacing: 2, color: '#8a967f' },
  title: { fontSize: 33, fontWeight: '600', letterSpacing: -1.3, color: '#244333' },
  subtitle: { fontSize: 13, lineHeight: 21, color: '#839176', marginTop: -8 },
  card: {
    backgroundColor: '#fff',
    borderWidth: 1,
    borderColor: '#e2e8d8',
    padding: 22,
    borderRadius: 17,
    gap: 17,
  },
  dark: { backgroundColor: '#214b3b', borderColor: '#214b3b' },
  heading: { fontSize: 18, fontWeight: '600', letterSpacing: -0.4, color: '#31513b' },
  copy: { fontSize: 13, lineHeight: 21, color: '#687e5b' },
  notice: {
    backgroundColor: '#eaf0dc',
    color: '#4d6d3f',
    padding: 14,
    borderRadius: 9,
    fontSize: 13,
    lineHeight: 21,
  },
  error: { backgroundColor: '#f9e8e3', color: '#a1483d' },
  button: {
    backgroundColor: '#254e3a',
    padding: 15,
    minHeight: 49,
    borderRadius: 10,
    justifyContent: 'center',
    alignItems: 'center',
  },
  secondary: { backgroundColor: '#eef2e5', borderWidth: 1, borderColor: '#dce5ce' },
  buttonText: { color: '#f5fae9', fontWeight: '600', fontSize: 13 },
  input: {
    backgroundColor: '#fafbf7',
    borderWidth: 1,
    borderColor: '#dce3d3',
    borderRadius: 9,
    padding: 14,
    color: '#2c4d36',
    fontSize: 15,
    minHeight: 48,
  },
  label: { fontSize: 12, fontWeight: '600', color: '#536e43' },
  choice: { padding: 12, borderWidth: 1, borderColor: '#e1e7d7', borderRadius: 9 },
  selected: { backgroundColor: '#ecf2db', borderColor: '#94ad74' },
  footer: { fontSize: 10, color: '#9ca68f', textAlign: 'center', marginTop: 15 },
  amount: { fontSize: 32, fontWeight: '500', letterSpacing: -1, color: '#254b36' },
  row: { paddingVertical: 15, borderBottomWidth: 1, borderColor: '#eef2e7', gap: 5 },
  status: { fontSize: 10, fontWeight: '700', letterSpacing: 0.5, color: '#819966' },
});
