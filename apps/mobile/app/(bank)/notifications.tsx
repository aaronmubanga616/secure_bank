import { useState } from 'react';
import { View } from 'react-native';
import { type Page, type Notification } from '@securebank/contracts';
import { api } from '../../src/session';
import {
  Screen,
  Card,
  Copy,
  Heading,
  Form,
  Button,
  Loading,
  ErrorState,
  useData,
  styles,
} from '../../src/ui';
export default function Notifications() {
  const [page, setPage] = useState(0);
  const query = useData<Page<Notification>>('/notifications', { page, size: 20 });
  return (
    <Screen title="Your inbox." subtitle="A little more in the know.">
      <ErrorState error={query.error} />
      <Card>
        <Form
          fields={[]}
          submit="Mark all read"
          action={async () => {
            await api.post('/notifications/read-all');
          }}
        />
        {query.isPending ? (
          <Loading />
        ) : (
          query.data?.content.map((notice) => (
            <View style={styles.row} key={notice.id}>
              <Heading>
                {notice.read ? '' : '● '}
                {notice.title}
              </Heading>
              <Copy>{notice.message}</Copy>
              <Copy>{new Date(notice.createdAt).toLocaleString()}</Copy>
              {!notice.read && (
                <Form
                  fields={[]}
                  submit="Mark read"
                  action={async () => {
                    await api.patch('/notifications/' + notice.id + '/read');
                  }}
                />
              )}
            </View>
          ))
        )}
        {query.data?.totalElements === 0 && (
          <Copy>You’re all caught up. Account updates appear here.</Copy>
        )}
        <Button
          secondary
          title="Previous"
          disabled={page === 0}
          onPress={() => setPage(page - 1)}
        />
        <Copy>Page {page + 1}</Copy>
        <Button
          secondary
          title="Next"
          disabled={!query.data || (page + 1) * 20 >= query.data.totalElements}
          onPress={() => setPage(page + 1)}
        />
      </Card>
    </Screen>
  );
}
