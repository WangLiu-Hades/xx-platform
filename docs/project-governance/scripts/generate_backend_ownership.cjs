/* Current module allocation only. Does not edit OpenAPI or business policy. */
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '../../..');
const YAML = require(path.join(root, 'apps/web/node_modules/yaml'));
const raw = fs.readFileSync(path.join(root, 'contracts/api/openapi.yaml'), 'utf8');
const api = YAML.parse(raw);
const ownership = JSON.parse(fs.readFileSync(path.join(root, 'docs/team/ownership.json'), 'utf8'));

function moduleFor(url) {
  if (/^\/(health|ready)$/.test(url)) return 'M15';
  if (url === '/captures/statistics' || /^\/(contexts|targets|decrypt-requests)(\/|$)/.test(url)) return 'M06';
  if (url === '/map/devices') return 'M13';
  if (/^\/(dashboard|operations|metrics)(\/|$)/.test(url)) return 'M04';
  if (/^\/ingestion(-batches|\/)/.test(url)) return 'M03';
  if (/^\/(captures|faces)(\/|$)/.test(url)) return 'M05';
  if (/^\/(map|trajectories)(\/|$)/.test(url)) return 'M07';
  if (/^\/analysis-tasks(\/|$)/.test(url)) return 'M08';
  if (/^\/(datasets|set-tasks)(\/|$)/.test(url)) return 'M09';
  if (/^\/(person-libraries|profiles|trend-models|model-tags)(\/|$)/.test(url)) return 'M11';
  if (/^\/(sites|devices|device-groups|device-commands|reports)(\/|$)/.test(url)) return 'M12';
  if (/^\/(users|roles|audit-events)(\/|$)/.test(url) || /^\/deliveries\/(search|export)$/.test(url)) return 'M14';
  if (/^\/(watch-rules|alerts|notification-tasks|notification-templates|deliveries|messages|batch-revocations)(\/|$)/.test(url)) return 'M10';
  if (/^\/(auth|batch-deletions|client-config|dictionaries|exports|files|import-templates|me|menus|permissions|regions|tasks|action-approvals)(\/|$)/.test(url)) return 'M01';
  throw new Error('No reviewed backend module for ' + url);
}

const rows = [];
for (const [url, item] of Object.entries(api.paths)) {
  for (const [method, op] of Object.entries(item)) {
    if (!op?.operationId) continue;
    const module = moduleFor(url);
    const backendOwner = ownership.backendModuleOwners[module];
    assert.ok(['DEV-01', 'DEV-02'].includes(backendOwner), module);
    rows.push({ operationId: op.operationId, method: method.toUpperCase(), path: url,
      summary: op.summary, module, backendOwner,
      permission: op['x-permission'] || op['x-permission-code'] || null,
      designStatus: op['x-design-status'] || 'baseline',
      clientConfirmation: op['x-client-confirmation'] || null });
  }
}
assert.equal(new Set(rows.map(x => x.operationId)).size, rows.length);
const counts = Object.fromEntries(['DEV-01', 'DEV-02'].map(owner => [owner, rows.filter(x => x.backendOwner === owner).length]));
const result = { source: 'contracts/api/openapi.yaml', openapiVersion: api.info.version,
  openapiSha256: crypto.createHash('sha256').update(raw).digest('hex'),
  allocationSource: 'docs/project-governance/BACKEND_TWO_DEV_SPLIT.md',
  allocationVersion: 'PR1-MODULES-2026-10-19', operations: rows.length, counts, rows };
const out = path.join(root, 'docs/project-governance');
fs.writeFileSync(path.join(out, 'backend-operation-owners.json'), JSON.stringify(result, null, 2) + '\n');
const fields = ['operationId', 'method', 'path', 'module', 'backendOwner', 'permission', 'designStatus', 'clientConfirmation', 'summary'];
const quote = x => '"' + String(x ?? '').replaceAll('"', '""') + '"';
fs.writeFileSync(path.join(out, 'backend-operation-owners.csv'), '\uFEFF' + [fields, ...rows.map(x => fields.map(k => x[k]))].map(row => row.map(quote).join(',')).join('\r\n') + '\r\n');
console.log(JSON.stringify({ operations: rows.length, counts }));
