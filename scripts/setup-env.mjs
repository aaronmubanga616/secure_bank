import { randomBytes } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { parseEnv } from 'node:util';
const existed = existsSync('.env');
let content = readFileSync(existed ? '.env' : '.env.example', 'utf8');
const replacements = {
  CHANGE_ME_DATABASE_PASSWORD: randomBytes(24).toString('hex'),
  CHANGE_ME_REDIS_PASSWORD: randomBytes(24).toString('hex'),
  CHANGE_ME_BASE64_AT_LEAST_32_RANDOM_BYTES: randomBytes(48).toString('base64'),
  CHANGE_ME_BASE64_32_RANDOM_BYTES: randomBytes(32).toString('base64'),
  CHANGE_ME_GRAFANA_PASSWORD: randomBytes(24).toString('hex'),
  CHANGE_ME_STAFF_PASSWORD: randomBytes(24).toString('base64url'),
  CHANGE_ME_METRICS_TOKEN: randomBytes(32).toString('hex'),
};
for (const [placeholder, value] of Object.entries(replacements))
  content = content.replaceAll(placeholder, value);
let config = parseEnv(content);
if (!config.METRICS_TOKEN) {
  content += '\nMETRICS_TOKEN=' + randomBytes(32).toString('hex') + '\n';
  config = parseEnv(content);
}
writeFileSync('.env', content, { mode: 0o600, flag: existed ? 'w' : 'wx' });
mkdirSync('.local', { recursive: true, mode: 0o700 });
writeFileSync('.local/metrics.token', config.METRICS_TOKEN, { mode: 0o600 });
console.log(
  'Local configuration is ready. Existing secrets were preserved; new values were not printed.',
);
