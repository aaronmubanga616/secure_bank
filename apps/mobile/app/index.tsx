import { View, Text, StyleSheet } from 'react-native';
export default function Home() {
  return <View style={styles.page}><Text style={styles.label}>SECUREBANK / ZAMBIA</Text><Text style={styles.title}>Your money.{ '\n' }In your hands.</Text><Text style={styles.text}>A considered approach to everyday banking.</Text><Text style={styles.note}>Educational simulator · ZMW only</Text></View>;
}
const styles = StyleSheet.create({ page: { flex: 1, padding: 28, justifyContent: 'center', backgroundColor: '#f4f6f2' }, label: { color: '#496453', letterSpacing: 3, fontSize: 11 }, title: { color: '#10271f', fontSize: 52, fontWeight: '700', letterSpacing: -2, marginVertical: 24 }, text: { color: '#496453', fontSize: 18, lineHeight: 28 }, note: { color: '#68756b', fontSize: 12, marginTop: 48 } });
