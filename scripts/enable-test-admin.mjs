import { readFileSync, writeFileSync } from 'node:fs';
if (!process.env.CI) throw new Error('This helper is only for a disposable CI database.');
const config = readFileSync('.env', 'utf8').replace(
  /^BOOTSTRAP_ADMIN_ENABLED=false$/m,
  'BOOTSTRAP_ADMIN_ENABLED=true',
);
writeFileSync('.env', config, { mode: 0o600 });
console.log('Enabled the randomly generated administrator for this disposable test run.');
