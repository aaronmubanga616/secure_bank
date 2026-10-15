import { spawnSync } from 'node:child_process';
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
const baseline = JSON.parse(
  readFileSync(new URL('./dependency-baseline.json', import.meta.url), 'utf8'),
);
const lock = JSON.parse(readFileSync('package-lock.json', 'utf8'));
if (!process.env.npm_execpath) throw new Error('Run this check with npm run security:check.');
const result = spawnSync(process.execPath, [process.env.npm_execpath, 'audit', '--json'], {
  encoding: 'utf8',
  maxBuffer: 10 * 1024 * 1024,
  timeout: 120000,
});
let audit;
try {
  audit = JSON.parse(result.stdout);
} catch {
  throw new Error('The dependency audit did not return a valid report. Check registry access.');
}
if (audit.error) throw new Error('The dependency registry could not complete its audit.');
const findings = [];
for (const [name, vulnerability] of Object.entries(audit.vulnerabilities ?? {}))
  for (const advisory of vulnerability.via.filter((value) => typeof value === 'object')) {
    const id = advisory.url.split('/').at(-1);
    const versions = [...new Set(vulnerability.nodes.map((node) => lock.packages[node]?.version))];
    const known = baseline.advisories.find(
      (item) =>
        item.package === name &&
        item.id === id &&
        versions.every((version) => item.versions.includes(version)),
    );
    findings.push({
      package: name,
      id,
      severity: advisory.severity,
      versions,
      url: advisory.url,
      reviewed: Boolean(known),
    });
  }
mkdirSync('.local', { recursive: true });
writeFileSync(
  '.local/security-report.json',
  JSON.stringify(
    { checkedAt: new Date().toISOString(), counts: audit.metadata?.vulnerabilities, findings },
    null,
    2,
  ),
);
for (const finding of findings)
  console.log(
    `${finding.reviewed ? 'REVIEWED' : 'NEW'} ${finding.severity}: ${finding.package} ${finding.versions.join(', ')} (${finding.id})`,
  );
if (findings.some((finding) => !finding.reviewed)) {
  console.error('New advisories or changed affected versions require review.');
  process.exitCode = 1;
} else
  console.log(
    `${findings.length} documented upstream advisories remain. This check does not certify a release as vulnerability-free.`,
  );
