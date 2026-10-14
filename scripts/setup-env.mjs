import { randomBytes } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
if (existsSync('.env')) {
  console.log('.env already exists; existing configuration was preserved.');
} else {
  let content = readFileSync('.env.example', 'utf8');
  const replacements = {
    CHANGE_ME_DATABASE_PASSWORD: randomBytes(24).toString('hex'),
    CHANGE_ME_REDIS_PASSWORD: randomBytes(24).toString('hex'),
    CHANGE_ME_BASE64_AT_LEAST_32_RANDOM_BYTES: randomBytes(48).toString('base64'),
    CHANGE_ME_BASE64_32_RANDOM_BYTES: randomBytes(32).toString('base64'),
    CHANGE_ME_GRAFANA_PASSWORD: randomBytes(24).toString('hex'),
  };
  for (const [placeholder, value] of Object.entries(replacements)) content = content.replaceAll(placeholder, value);
  writeFileSync('.env', content, { mode: 0o600, flag: 'wx' });
  console.log('Created .env with random local secrets. Values were not printed.');
}
