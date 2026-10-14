import { Tabs } from 'expo-router';
import { Text } from 'react-native';
export default function BankLayout() {
  return (
    <Tabs
      screenOptions={{
        headerStyle: { backgroundColor: '#f5f6f0' },
        headerTintColor: '#244833',
        headerShadowVisible: false,
        tabBarActiveTintColor: '#315a3d',
        tabBarInactiveTintColor: '#8f9d83',
        tabBarStyle: { backgroundColor: '#fdfefa', borderTopColor: '#e3e9da' },
        tabBarLabelStyle: { fontSize: 10 },
      }}
    >
      <Tabs.Screen
        name="home"
        options={{
          title: 'Overview',
          tabBarIcon: ({ color }) => <Text style={{ color, fontSize: 22 }}>⌂</Text>,
        }}
      />
      <Tabs.Screen
        name="activity"
        options={{
          title: 'Activity',
          tabBarIcon: ({ color }) => <Text style={{ color, fontSize: 20 }}>↔</Text>,
        }}
      />
      <Tabs.Screen
        name="move"
        options={{
          title: 'Move money',
          tabBarIcon: ({ color }) => <Text style={{ color, fontSize: 21 }}>↗</Text>,
        }}
      />
      <Tabs.Screen
        name="more"
        options={{
          title: 'More',
          tabBarIcon: ({ color }) => <Text style={{ color, fontSize: 20 }}>☰</Text>,
        }}
      />
      {[
        'accounts',
        'receipt',
        'recipients',
        'scheduled',
        'notifications',
        'statements',
        'profile',
        'security',
      ].map((name) => (
        <Tabs.Screen
          name={name}
          key={name}
          options={{ href: null, title: name[0].toUpperCase() + name.slice(1) }}
        />
      ))}
    </Tabs>
  );
}
