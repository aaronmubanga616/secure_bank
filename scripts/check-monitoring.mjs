import assert from 'node:assert/strict';
const api = 'http://localhost:8180/actuator/prometheus';
assert.equal((await fetch(api)).status, 403, 'Metrics must reject anonymous requests');
const metrics = await fetch(api, {
  headers: { Authorization: 'Bearer ' + process.env.METRICS_TOKEN },
});
assert.equal(metrics.status, 200);
assert.match(await metrics.text(), /securebank_outbox_pending/);
let target;
for (let attempt = 0; attempt < 20; attempt++) {
  const result = await (await fetch('http://localhost:9090/api/v1/targets')).json();
  target = result.data?.activeTargets?.find((item) => item.labels.job === 'securebank');
  if (target?.health === 'up') break;
  await new Promise((done) => setTimeout(done, 2000));
}
assert.equal(target?.health, 'up', 'Prometheus must successfully scrape the API');
const authorization =
  'Basic ' + Buffer.from('admin:' + process.env.GRAFANA_ADMIN_PASSWORD).toString('base64');
const response = await fetch(
  `http://localhost:${process.env.GRAFANA_PORT ?? 3000}/api/dashboards/uid/securebank-overview`,
  {
    headers: { Authorization: authorization },
  },
);
assert.equal(response.status, 200);
const dashboard = await response.json();
assert.ok(dashboard.dashboard.panels.length >= 10);
console.log('Private metrics, Prometheus scraping and the provisioned Grafana dashboard passed.');
