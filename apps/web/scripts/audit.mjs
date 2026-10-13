import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const file=(name)=>path.join(root,name);
const read=(name)=>JSON.parse(fs.readFileSync(file(name),'utf8').replace(/^\uFEFF/,''));
const sha=(name)=>crypto.createHash('sha256').update(fs.readFileSync(name)).digest('hex');
const manifest=read('audit/vue-migration/source-manifest.json');
const source=read('audit/vue-migration/requirements-source.json');
const contract=read('src/api/contract.json');
const unit=read('audit/vue-migration/unit-final.json');
const smoke=read('audit/vue-migration/smoke-final.json');
const mock=read('audit/vue-migration/vue-e2e-results.json');
const http=read('audit/vue-migration/http-e2e-results.json');
const flows=read('audit/vue-migration/flows/results.json');
const httpAnalysis=read('audit/vue-migration/http-analysis-results.json');
const httpResources=read('audit/vue-migration/http-resources-results.json');
const mapSelection=read('audit/vue-migration/map-selection-results.json');
const dependencyAudit=read('audit/vue-migration/dependency-audit.json');
const sourceFiles=fs.readdirSync(file('src'),{recursive:true}).map(name=>file('src/'+name)).filter(name=>fs.statSync(name).isFile());
const matrix=fs.readFileSync(file('../../contracts/api/api-coverage-matrix.md'),'utf8');
const pending=fs.readFileSync(file('../../contracts/api/OPENAPI_PENDING.md'),'utf8');
function ids(spec){const result=new Set();for(const piece of spec.split(',')){const [from,to]=piece.split('-').map(Number);for(let i=from;i<=(to??from);i++)result.add('PDF-'+String(i).padStart(3,'0'));}return result;}
function range(spec){return ids(spec);}
// 仅从实际通过的用例提取证据。用例覆盖不等于整条业务已全面验收。
const evidence=new Set();const evidenceCases=new Map();
function addEvidence(id,name,kind){evidence.add(id);if(!evidenceCases.has(id))evidenceCases.set(id,[]);evidenceCases.get(id).push({kind,name});}
for(const suite of unit.testResults||[])for(const test of suite.assertionResults||[])if(test.status==='passed')for(const id of (test.fullName||test.title||'').match(/PDF-\d{3}/g)||[])addEvidence(id,test.fullName||test.title,'unit_or_contract');
for(const test of flows.checks||[])if(test.passed)for(const id of test.ids||[])addEvidence(id,test.name,'browser_business_flow');
for(const test of [...httpAnalysis.checks,...httpResources.checks])if(test.passed)for(const id of test.ids||[])addEvidence(id,test.name,'http_contract_browser_flow');
// These statements need real data, decisions or asynchronous services after the frontend prototype.
const backendBoundary=range('5,17,24,26-28,37,39-41,50,52-54,59-61,67,70-73,76,79-85,88-93,96-102,104,106,110-111,114-121,122-132,139-141,149,154,156,164-165,169-173,175-189,191-195,199-202,204-208,210-214');
const notes={
  'PDF-031':'原文车牌段落误写 IMSI；按章节业务对象改用 PLATE。',
  'PDF-067':'HTTP 导入读取服务端模板和分类上传策略，经 ACTIVE 文件检查后提交导入任务；任务中心展示逐行结果/错误。',
  'PDF-070':'HTTP 地图已接入轨迹、采集详情、热力和区域摘要；按目标分组绘线，禁止跨目标连线。',
  'PDF-071':'HTTP 地图 IMEI 条件保存为 CAPTURE_SEARCH 快照，游标翻页复用 Context。',
  'PDF-073':'HTTP 地图右侧 11 项资料来自授权档案接口，缺少关联时不伪造人员。',
  'PDF-106':'Mock 解析器按括号和左结合验证；HTTP 以 previewSetTask/createSetTask 为边界。',
  'PDF-165':'无授权地图底图或数据时不伪造真实热力结果。',
  'PDF-169':'HTTP 配置向导已绑定分组、设备成员、参数目录和命令任务；可写参数来自后端，共同参数才可批量配置。',
  'PDF-191':'HTTP 通知表单已绑定模板、每日计划与平台接收人；发送仍受 NOTIFICATION_SEND 能力门禁控制。'
};
const requirements=source.map(r=>({
  ...r,
  pageRendered:smoke.checks.some(check=>check.page===r.page&&!check.errors.length),
  documentedApiMapping:matrix.includes(r.id),
  browserEvidence:evidence.has(r.id)?'representative_business_check':'page_smoke_only',
  evidenceCases:evidenceCases.get(r.id)||[],
  liveBackendBoundary:backendBoundary.has(r.id),
  note:notes[r.id]||''
}));

const checks={
  originalPdfHash:sha(manifest.pdf)===manifest.pdfSHA256,
  openapiHash:sha(manifest.openapi)===manifest.openapiSHA256,
  requirementCount:source.length===214&&new Set(source.map(r=>r.id)).size===214,
  allRequirementPagesRendered:requirements.every(r=>r.pageRendered),
  allRequirementsMappedInSystemDesign:requirements.every(r=>r.documentedApiMapping),
  routeCount:smoke.pages===28&&smoke.checks.length===28,
  noSmokeErrors:smoke.errors.length===0&&smoke.consoleErrors.length===0,
  unitAndContractTests:unit.success&&unit.numPassedTests>=269&&unit.numFailedTests===0&&unit.numFailedTestSuites===0,
  mockBrowserChecks:mock.passed===13&&mock.failed===0&&mock.pageErrors.length===0&&mock.consoleErrors.length===0,
  expandedBrowserFlows:flows.checks.length>=22&&flows.checks.every(c=>c.passed)&&flows.errors.length===0&&flows.consoleErrors.length===0,
  httpExampleChecks:http.checks.length===5&&http.checks.every(c=>c.passed)&&http.errors.length===0,
  httpAnalysisChecks:httpAnalysis.checks.length===3&&httpAnalysis.checks.every(c=>c.passed)&&httpAnalysis.errors.length===0,
  httpResourceChecks:httpResources.checks.length===8&&httpResources.checks.every(c=>c.passed)&&httpResources.errors.length===0,
  mapSelectionChecks:mapSelection.origin==='http://127.0.0.1:5182'&&mapSelection.checks.length===11&&mapSelection.checks.every(c=>c.passed)&&mapSelection.errors.length===0&&mapSelection.consoleErrors.length===0,
  dependencyVulnerabilities:dependencyAudit.metadata?.vulnerabilities?.total===0&&Object.keys(dependencyAudit.vulnerabilities||{}).length===0,
  productionBrowserRuns:flows.origin==='http://127.0.0.1:5182'&&smoke.origin==='http://127.0.0.1:5182',
  openapiOperationCount:Object.keys(contract.operations).length===193,
  openapiSchemaCount:Object.keys(contract.components.schemas).length===581,
  buildArtifact:fs.existsSync(file('dist/index.html')),
  buildMatchesSource:fs.statSync(file('dist/index.html')).mtimeMs>=Math.max(...sourceFiles.map(name=>fs.statSync(name).mtimeMs)),
  pendingDesignLedger:/P-001/.test(pending)&&/P-021/.test(pending),
  originalStaticBackup:fs.existsSync(file('../../archive/ui/prototype-v1-baseline/app.js'))&&fs.existsSync(file('../../archive/ui/prototype-v1-baseline/app.css')),
  noActiveStaticEntry:!fs.existsSync(file('app.js'))&&!fs.existsSync(file('app.css'))
};
const findings=[
  {severity:'boundary',description:'真实后端尚未提供；HTTP 浏览器回归使用 OpenAPI 样例拦截，不构成真实端到端联调。'},
  {severity:'boundary',description:'原 PDF 的 64 张内嵌图均为 2×2 占位图；文字要求可核对，原图像素布局不可核对。'},
  {severity:'boundary',description:'OPENAPI_PENDING 中算法、身份关联、短信/邮件、厂商设备命令、地图服务及模型阈值仍需签认，HTTP 模式按能力门禁或明确提示。'},
  {severity:'test_gap',description:'业务单元测试与 22 组浏览器流程覆盖显著增加；仍须区分代表性用例证据、仅页面渲染和整条需求完整验收，不能把 ID 出现在用例中等同于全要求签收。'},
  {severity:'performance',description:'页面已按路由拆包，接口目录与运行时校验契约已去除重复样例；仍需在正式部署环境按实际数据规模做性能验收。'}
];
const ready=Object.values(checks).every(Boolean);
const audit={status:ready?'completed_frontend_prototype':'checks_failed',generatedAt:new Date().toISOString(),scope:'Vue frontend prototype; backend and algorithms excluded',sources:{pdf:manifest.pdf,pdfSHA256:manifest.pdfSHA256,pdfPages:manifest.pdfPages,embeddedImages:manifest.embeddedImages,openapi:manifest.openapi,openapiSHA256:manifest.openapiSHA256},checks,summary:{requirements:requirements.length,pages:smoke.pages,directOrSharedEvidence:requirements.filter(r=>r.browserEvidence!=='page_smoke_only').length,pageSmokeOnly:requirements.filter(r=>r.browserEvidence==='page_smoke_only').length,liveBackendBoundary:requirements.filter(r=>r.liveBackendBoundary).length,unitTests:unit.numPassedTests,historicalMockBrowserChecks:mock.passed,expandedBrowserFlows:flows.checks.length,httpExampleChecks:http.checks.length+httpAnalysis.checks.length+httpResources.checks.length,knownDependencyVulnerabilities:dependencyAudit.metadata?.vulnerabilities?.total,operations:Object.keys(contract.operations).length,schemas:Object.keys(contract.components.schemas).length},findings,requirements};
const out=file('audit/vue-migration/final-audit.json');
audit.summary.mapSelectionChecks=mapSelection.checks.filter(c=>c.passed).length;
fs.writeFileSync(out,JSON.stringify(audit,null,2)+'\n');
const lines=[
  '# Vue 前端原型迁移最终审计',
  '',
  `审计时间：${audit.generatedAt}。范围：仅前端原型；后端、算法及真实外部服务不在本次实现范围。`,
  '',
  `基线：原始 PDF ${manifest.pdfPages} 页，214 条文字要求；OpenAPI ${audit.summary.operations} 个操作、${audit.summary.schemas} 个 Schema。PDF 与 OpenAPI 的 SHA-256 已复核。`,
  '',
  `结论：${ready?'前端原型范围内已执行检查全部通过，无本轮审计尚待修复的前端项。':'检查未全部通过，不能验收。'}`,
  '',
  `检查 ${Object.values(checks).filter(Boolean).length}/${Object.keys(checks).length} 通过。生产构建的 28 页冒烟无脚本异常；${unit.numPassedTests} 项单元/契约测试、${flows.checks.length} 组业务浏览器流程、${audit.summary.httpExampleChecks} 组 HTTP 样例检查通过。npm 官方漏洞库本次扫描为 ${audit.summary.knownDependencyVulnerabilities} 项已知依赖漏洞。生产构建由 npm run build 生成 dist/。`,
  '',
  `逐项证据：${audit.summary.directOrSharedEvidence} 条有直接或共用组件浏览器证据，${audit.summary.pageSmokeOnly} 条目前只有页面渲染与设计映射证据；${audit.summary.liveBackendBoundary} 条还涉及真实数据、算法、通知或其他后端行为。详见 final-audit.json 的逐项矩阵。页面可见不等于业务行为已逐条验收。`,
  '',
  `地图框选修复追加回归：${audit.summary.mapSelectionChecks} 项真实鼠标检查通过，覆盖地图研判与设备分布的四点全选、框外排除、反向拖拽、两点替换、空白清空、重置，以及地图研判单选。修复前同一用例预期 4 点、实际仅选中 1 点；修复后全部通过。证据为 map-selection-results.json。`,
  '',
  '## 已处理的冲突',
  '',
  '- 车牌章节误写 IMSI：界面与接口采用车牌/PLATE 语义。',
  '- 原文复制了分析任务的部分标题：按伴随、碰撞、区域统计各自任务语义组织页面。',
  '- PDF 重复描述设备分布：使用同一设备分布页面承载两处描述。',
  '- 系统设计曾称原型为可直接打开的静态 HTML：更新为 Vite 启动的 Vue 原型，旧版文件单独备份。',
  '- 系统设计曾指定旧 TS 生成器：更新为当前 openapi-typescript DTO/操作类型与 Axios 运行时适配。',
  '',
  '## 边界与待验证事项',
  '',
  ...findings.map(f=>`- [${f.severity}] ${f.description}`),
  '',
  'HTTP 模式不将合成 Mock 数据或本地导入当成真实结果。分析、通知、布控、设备配置、模型、受控导入、人像搜图、地图与档案页面已绑定契约。真实后端完成后还须按 T01–T27 和权限矩阵联调；本结论仅验收前端原型，不代表生产系统、算法或外部服务已验收，也不承诺绝对不存在漏洞。',
  '',
  '## 逐项摘要',
  '',
  '| ID | 页面 | 要求 | 证据级别 | 需真实后端 |',
  '|---|---|---|---|---|',
  ...requirements.map(r=>`| ${r.id} | ${r.page} | ${r.requirement.replaceAll('|','/')} | ${r.browserEvidence==='page_smoke_only'?'页面冒烟':'代表性交互/组件'} | ${r.liveBackendBoundary?'是':'否'} |`),
  ''
];
fs.writeFileSync(file('audit/vue-migration/final-audit.md'),lines.join('\n'));
console.log(JSON.stringify({checks:Object.values(checks).filter(Boolean).length,totalChecks:Object.keys(checks).length,summary:audit.summary,findings:findings.length}));
if(Object.values(checks).some(ok=>!ok))process.exitCode=1;
