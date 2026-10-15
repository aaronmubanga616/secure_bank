const urls = [
  'http://localhost:8180/actuator/health',
  'http://localhost:8088',
  'http://localhost:8089',
];
for (const url of urls) {
  let ready = false;
  for (let attempt = 0; attempt < 90; attempt++) {
    try {
      if ((await fetch(url, { signal: AbortSignal.timeout(3000) })).ok) {
        ready = true;
        break;
      }
    } catch {
      /* A starting service may refuse connections. */
    }
    await new Promise((done) => setTimeout(done, 2000));
  }
  if (!ready) throw new Error('Stack readiness timed out: ' + url);
}
console.log('The API and both browser gateways are ready.');
