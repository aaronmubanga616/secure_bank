import { File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';
import { type Account } from '@securebank/contracts';
import { api } from '../../src/session';
import { Screen, Card, Copy, Form, ErrorState, useData } from '../../src/ui';
export default function Statements() {
  const accounts = useData<Account[]>('/accounts');
  const today = new Date().toLocaleDateString('en-CA', { timeZone: 'Africa/Lusaka' });
  return (
    <Screen title="Your record, ready." subtitle="A clear PDF statement, whenever you need it.">
      <ErrorState error={accounts.error} />
      <Card>
        <Copy>
          Includes only posted transactions and exact opening/closing balances. Choose up to one
          year.
        </Copy>
        <Form
          fields={[
            {
              name: 'accountId',
              label: 'Account',
              options:
                accounts.data?.map((account) => ({
                  value: account.id,
                  label: account.type + ' · ' + account.accountNumber,
                })) ?? [],
            },
            { name: 'from', label: 'From date (YYYY-MM-DD)', initial: today.slice(0, 8) + '01' },
            { name: 'to', label: 'To date (YYYY-MM-DD)', initial: today },
          ]}
          submit="Download & share PDF"
          action={async (values) => {
            if (!(await Sharing.isAvailableAsync()))
              throw new Error('File sharing is not available on this device.');
            const response = await api.get('/statements', {
              params: values,
              responseType: 'arraybuffer',
            });
            const file = new File(Paths.cache, `securebank-statement-${Date.now()}.pdf`);
            try {
              file.write(new Uint8Array(response.data));
              await Sharing.shareAsync(file.uri, {
                mimeType: 'application/pdf',
                UTI: 'com.adobe.pdf',
                dialogTitle: 'SecureBank statement',
              });
            } finally {
              if (file.exists) file.delete();
            }
            return 'Statement ready. The temporary device copy has been removed.';
          }}
        />
      </Card>
    </Screen>
  );
}
