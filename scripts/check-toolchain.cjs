const assert = require('node:assert/strict');
const project = require('xcode').project('test.pbxproj');
project.hash = { project: { objects: {} } };
assert.match(project.generateUuid(), /^[A-F0-9]{24}$/);
const path = require('node:path');
const reactVersions = ['apps/web', 'apps/admin', 'apps/mobile', 'packages/ui'].map(
  (directory) =>
    require(require.resolve('react/package.json', { paths: [path.resolve(directory)] })).version,
);
assert.equal(
  new Set(reactVersions).size,
  1,
  'Applications must resolve one compatible React version.',
);
console.log('The patched Xcode UUID API and shared React runtime are compatible.');
