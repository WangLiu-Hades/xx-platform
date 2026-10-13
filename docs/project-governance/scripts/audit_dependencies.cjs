// Run only after authorization to send dependency metadata to the public npm registry.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { spawnSync } = require('node:child_process');

const root = path.resolve(__dirname, '../../..');
const prototype = path.join(root, 'apps/web');
const out = path.join(root, 'docs/project-governance/audit');
const npmCLI = process.argv[2];
if (!npmCLI || !fs.existsSync(npmCLI)) throw new Error('Pass the absolute npm-cli.js path.');
const hash = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const inputs = ['package.json', 'package-lock.json'];
const inputSHA256 = Object.fromEntries(inputs.map(name => [name, hash(path.join(prototype, name))]));
const rawPath = path.join(out, 'dependency-current.json');
const previousPath = path.join(out, 'dependency-pre-authorization.json');
if (fs.existsSync(rawPath) && !fs.existsSync(previousPath)) fs.copyFileSync(rawPath, previousPath);
const args = [npmCLI, 'audit', '--json', '--registry=https://registry.npmjs.org/',
  '--include=dev', '--include=optional', '--include=peer', '--audit-level=low',
  '--ignore-scripts', '--fund=false', '--update-notifier=false', '--fetch-timeout=30000', '--fetch-retries=1'];
const startedAt = new Date().toISOString();
const version = spawnSync(process.execPath, [npmCLI, '--version'], { encoding: 'utf8', windowsHide: true });
const run = spawnSync(process.execPath, args, {
  cwd: prototype, encoding: 'utf8', windowsHide: true, timeout: 180000, maxBuffer: 16 * 1024 * 1024,
});
fs.writeFileSync(rawPath, run.stdout || '', 'utf8');
const stderrPath = path.join(out, 'dependency-current.stderr.txt');
fs.writeFileSync(stderrPath, run.stderr || '', 'utf8');
let report, parseError;
try { report = JSON.parse(run.stdout); } catch (error) { parseError = error.message; }
const complete = !run.error && !report?.error && Number.isInteger(report?.metadata?.vulnerabilities?.total)
  && report?.vulnerabilities && [0, 1].includes(run.status);
const afterSHA256 = Object.fromEntries(inputs.map(name => [name, hash(path.join(prototype, name))]));
const metadata = {
  startedAt, finishedAt: new Date().toISOString(),
  authorization: { source: 'explicit user reply', date: '2026-10-10', answer: '授权发送并补做检查', destination: 'https://registry.npmjs.org/' },
  executable: process.execPath, nodeVersion: process.version, npmCLI, npmVersion: version.stdout?.trim(),
  args: args.slice(1), scope: 'lockfile dependency tree including production, development, optional and peer dependencies',
  cwd: prototype, exitCode: run.status, signal: run.signal, error: run.error?.message || parseError || null,
  status: complete ? 'completed' : 'failed', vulnerabilities: report?.metadata?.vulnerabilities || null,
  dependencies: report?.metadata?.dependencies || null,
  inputSHA256, afterSHA256, inputsUnchanged: inputs.every(name => inputSHA256[name] === afterSHA256[name]),
  reportSHA256: hash(rawPath), stderrSHA256: hash(stderrPath),
};
fs.writeFileSync(path.join(out, 'dependency-current-run.json'), JSON.stringify(metadata, null, 2) + '\n', 'utf8');
console.log(JSON.stringify(metadata, null, 2));
process.exitCode = complete && metadata.inputsUnchanged ? 0 : 2;
