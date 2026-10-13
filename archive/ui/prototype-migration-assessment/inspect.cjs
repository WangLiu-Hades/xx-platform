const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const bundle = 'C:/Users/C-Linglong/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright/lib/transform/babelBundle.js';
const { babelParse } = require(bundle);
const root = path.resolve(__dirname, '../prototype');
const source = fs.readFileSync(path.join(root, 'app.js'), 'utf8');
const ast = babelParse(source, 'app.js', false);
const visit = (node, fn) => {
  if (!node || typeof node !== 'object') return;
  if (node.type) fn(node);
  for (const [key, value] of Object.entries(node)) {
    if (['loc', 'extra', 'comments', 'tokens'].includes(key)) continue;
    if (Array.isArray(value)) value.forEach(n => visit(n, fn));
    else if (value && typeof value === 'object') visit(value, fn);
  }
};
const functions = [];
const variables = [];
const groupsNode = ast.program.body.find(n => n.type === 'VariableDeclaration' && n.declarations.some(d => d.id.name === 'groups')).declarations.find(d => d.id.name === 'groups').init;
const groups = Object.fromEntries(groupsNode.properties.map(p => [p.key.value, p.value.elements.map(e => e.value)]));
for (const n of ast.program.body) {
  if (n.type === 'VariableDeclaration') variables.push(...n.declarations.map(d => d.id.name));
  const defs = n.type === 'FunctionDeclaration' ? [{ name: n.id.name, node: n }] : n.type === 'VariableDeclaration' ? n.declarations.filter(d => ['ArrowFunctionExpression', 'FunctionExpression'].includes(d.init?.type)).map(d => ({ name: d.id.name, node: d.init })) : [];
  for (const { name, node } of defs) {
    const calls = new Set(); let nodes = 0, branches = 0, templates = 0;
    visit(node, child => {
      nodes++;
      if (['IfStatement', 'ConditionalExpression', 'SwitchCase'].includes(child.type)) branches++;
      if (child.type === 'TemplateLiteral') templates++;
      if (child.type === 'CallExpression' && child.callee.type === 'Identifier') calls.add(child.callee.name);
    });
    functions.push({ name, line: node.loc.start.line, bytes: Buffer.byteLength(source.slice(node.start, node.end)), nodes, branches, templates, calls: [...calls].sort() });
  }
}
let seedKeys = [];
visit(ast.program.body.find(n => n.type === 'FunctionDeclaration' && n.id.name === 'seed'), n => {
  if (n.type === 'ReturnStatement' && n.argument?.type === 'ObjectExpression') seedKeys = n.argument.properties.map(p => p.key.name || p.key.value);
});
const events = [], innerHTML = [], actions = new Set();
visit(ast, n => {
  if (n.type === 'CallExpression' && n.callee.type === 'MemberExpression' && n.callee.property.name === 'addEventListener') events.push({ target: n.callee.object.name, event: n.arguments[0]?.value, line: n.loc.start.line });
  if (n.type === 'AssignmentExpression' && n.left.type === 'MemberExpression' && n.left.property.name === 'innerHTML') innerHTML.push(n.loc.start.line);
  if (n.type === 'BinaryExpression' && ['===', '=='].includes(n.operator)) {
    if (n.left.type === 'Identifier' && n.left.name === 'action' && n.right.type === 'StringLiteral') actions.add(n.right.value);
  }
});
const requirements = JSON.parse(fs.readFileSync(path.join(root, 'audit/requirements.json')));
const tests = JSON.parse(fs.readFileSync(path.join(root, 'tests/evidence/results.json')));
const manifest = JSON.parse(fs.readFileSync(path.join(root, 'audit/manifest.json')));
const hashes = Object.fromEntries(Object.entries(manifest.sourceFiles).map(([file, expected]) => {
  const actual = crypto.createHash('sha256').update(fs.readFileSync(path.join(root, file))).digest('hex');
  return [file, { expected, actual, match: expected === actual }];
}));
const requirementsByPage = Object.fromEntries([...new Set(requirements.map(r => r.page))].map(p => [p, requirements.filter(r => r.page === p).length]));
const operationText = fs.readFileSync(path.resolve(root, '../../api/openapi.yaml'), 'utf8');
const inventory = {
  inspectedAt: new Date().toISOString(),
  method: 'Babel AST: top-level named function declarations/arrow bindings; action equality comparisons; AST event listener registrations; SHA256 against prior audit manifest. Counts are structural, not complexity or coverage guarantees.',
  appBytes: Buffer.byteLength(source), groups, pageCount: Object.values(groups).flat().length,
  namedFunctionCount: functions.length, variables, seedKeys, seedEntityCount: seedKeys.length,
  events, innerHTML, actionNames: [...actions].sort(), actionComparisonCount: actions.size,
  functions, requirementsByPage, requirementCount: requirements.length,
  tests: { timestamp: tests.timestamp, total: tests.total, passed: tests.passed, failed: tests.failed, consoleErrors: tests.consoleErrors, uniqueIds: new Set(tests.results.map(t => t.id)).size },
  hashes, apiOperationCount: (operationText.match(/^\s+operationId:/gm) || []).length,
  apiContractSHA256: crypto.createHash('sha256').update(operationText).digest('hex')
};
fs.writeFileSync(path.join(__dirname, 'inventory.json'), JSON.stringify(inventory, null, 2));
console.log(JSON.stringify({ bytes: inventory.appBytes, pages: inventory.pageCount, functions: functions.length, actions: actions.size, entities: seedKeys, events, innerHTML, requirementPages: requirementsByPage, tests: inventory.tests, hashes, apiOperations: inventory.apiOperationCount, largest: functions.slice().sort((a,b) => b.bytes-a.bytes).slice(0, 12).map(f => ({ name: f.name, line: f.line, bytes: f.bytes, branches: f.branches })) }, null, 2));
