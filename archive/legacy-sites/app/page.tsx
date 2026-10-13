"use client";

import { useEffect, useMemo, useState, type ComponentType, type CSSProperties, type ReactNode } from "react";
import {
  Activity,
  Bell,
  Binoculars,
  Building2,
  Boxes,
  CalendarDays,
  Car,
  CheckCircle2,
  ChevronRight,
  CircleAlert,
  Clock3,
  Cpu,
  Crosshair,
  Database,
  Download,
  FileChartColumn,
  FileText,
  Fingerprint,
  Gauge,
  HardDrive,
  LayoutDashboard,
  Map,
  MapPin,
  Mail,
  MoreHorizontal,
  Network,
  Plus,
  RadioTower,
  RotateCcw,
  ScanFace,
  Search,
  Server,
  Settings,
  ShieldAlert,
  SlidersHorizontal,
  Trash2,
  Upload,
  Users,
  Waypoints,
  Wifi,
  Zap,
} from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Progress } from "@/components/ui/progress";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarInset,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  SidebarRail,
  SidebarTrigger,
} from "@/components/ui/sidebar";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { cn } from "@/lib/utils";

type ViewId =
  | "dashboard"
  | "search"
  | "map"
  | "analysis"
  | "surveillance"
  | "models"
  | "operations"
  | "settings";

type IconType = ComponentType<{ className?: string }>;

const navigation: Array<{
  label: string;
  items: Array<{ id: ViewId; label: string; icon: IconType }>;
}> = [
  {
    label: "核心应用",
    items: [
      { id: "dashboard", label: "系统首页", icon: LayoutDashboard },
      { id: "search", label: "数据检索", icon: Search },
      { id: "map", label: "地图研判", icon: Map },
      { id: "analysis", label: "数据研判", icon: Binoculars },
      { id: "surveillance", label: "布控预警", icon: ShieldAlert },
    ],
  },
  {
    label: "平台管理",
    items: [
      { id: "models", label: "数据模型", icon: Network },
      { id: "operations", label: "运维服务", icon: Gauge },
      { id: "settings", label: "系统设置", icon: Settings },
    ],
  },
];

const viewMeta: Record<ViewId, { title: string; description: string }> = {
  dashboard: { title: "数据总览", description: "多源采集、研判任务与设备运行态势" },
  search: { title: "数据检索", description: "按时间、场所和目标标识检索采集记录" },
  map: { title: "地图研判", description: "查看目标轨迹、途经点位和关联信息" },
  analysis: { title: "数据研判", description: "管理伴随、碰撞、区域统计与数据集运算任务" },
  surveillance: { title: "布控预警", description: "配置布控范围并跟踪实时命中情况" },
  models: { title: "数据模型", description: "管理图码档案、车码档案与标签规则" },
  operations: { title: "运维服务", description: "监控场所、设备、采集质量和通知任务" },
  settings: { title: "系统设置", description: "维护用户权限及操作审计" },
};

const searchRows = [
  { id: "4600•••••7812", type: "电围", site: "滨江路站点 03", time: "14:32:18", area: "城南片区", count: 18 },
  { id: "粤B·7••35", type: "车牌", site: "迎宾大道卡口", time: "14:28:04", area: "高新片区", count: 7 },
  { id: "9C:A5:••:31:7E", type: "MAC", site: "中心广场点位", time: "14:21:36", area: "中心片区", count: 23 },
  { id: "IMG-09•••42", type: "人像", site: "客运中心北门", time: "14:17:52", area: "城北片区", count: 4 },
  { id: "4600•••••1946", type: "电围", site: "环城东路站点", time: "14:05:11", area: "城东片区", count: 12 },
];

const baseTasks = [
  { id: "YP-20260912-031", name: "重点区域伴随排查", type: "伴随分析", created: "今天 13:48", owner: "研判一组", progress: 86, status: "计算中" },
  { id: "YP-20260912-028", name: "城南三点位碰撞", type: "碰撞分析", created: "今天 10:26", owner: "研判二组", progress: 100, status: "已完成" },
  { id: "YP-20260911-116", name: "交通枢纽区域统计", type: "区域统计", created: "昨天 18:05", owner: "研判一组", progress: 100, status: "已完成" },
];

const alerts = [
  { time: "14:31", name: "重点人员临时布控", source: "城南客运站", level: "高" },
  { time: "14:16", name: "重点车辆布控", source: "迎宾大道卡口", level: "中" },
  { time: "13:58", name: "MAC 特征布控", source: "中心广场点位", level: "中" },
];

function SectionHeading({
  title,
  description,
  actions,
}: {
  title: string;
  description: string;
  actions?: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-slate-950">{title}</h1>
        <p className="mt-1 text-sm text-slate-500">{description}</p>
      </div>
      {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
    </div>
  );
}

function StatusDot({ tone = "normal" }: { tone?: "normal" | "warn" | "danger" }) {
  return (
    <span
      className={cn(
        "mr-1.5 inline-block size-2 rounded-full",
        tone === "normal" && "bg-emerald-500",
        tone === "warn" && "bg-amber-500",
        tone === "danger" && "bg-rose-500",
      )}
    />
  );
}

function MetricCard({
  label,
  value,
  meta,
  icon: Icon,
  tone,
}: {
  label: string;
  value: string;
  meta: string;
  icon: IconType;
  tone: "blue" | "cyan" | "amber" | "violet";
}) {
  const tones = {
    blue: "bg-blue-50 text-blue-700",
    cyan: "bg-cyan-50 text-cyan-700",
    amber: "bg-amber-50 text-amber-700",
    violet: "bg-violet-50 text-violet-700",
  };
  return (
    <Card className="border-slate-200/80 shadow-sm">
      <CardContent className="p-5">
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="text-sm text-slate-500">{label}</p>
            <p className="mt-2 text-2xl font-semibold tracking-tight text-slate-950 tabular-nums">{value}</p>
          </div>
          <div className={cn("grid size-10 place-items-center rounded-xl", tones[tone])}>
            <Icon className="size-5" />
          </div>
        </div>
        <p className="mt-3 text-xs text-slate-500">{meta}</p>
      </CardContent>
    </Card>
  );
}

function MiniLineChart() {
  return (
    <div className="relative mt-4 h-48 overflow-hidden rounded-xl bg-slate-950 px-5 pb-5 pt-4 text-white">
      <div className="absolute inset-0 data-grid opacity-35" />
      <div className="relative flex items-center justify-between">
        <div>
          <p className="text-xs text-slate-400">近 7 日采集趋势</p>
          <p className="mt-1 text-xl font-medium tabular-nums">1,826.4 万</p>
        </div>
        <Badge className="border-white/10 bg-white/10 text-cyan-200">较上周 +8.6%</Badge>
      </div>
      <svg className="relative mt-4 h-24 w-full" viewBox="0 0 640 112" role="img" aria-label="近七日采集量整体呈上升趋势">
        <defs>
          <linearGradient id="area" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#22d3ee" stopOpacity="0.38" />
            <stop offset="1" stopColor="#22d3ee" stopOpacity="0" />
          </linearGradient>
        </defs>
        <path d="M0,86 C64,78 86,48 140,58 C205,71 226,28 286,41 C346,54 374,72 430,49 C498,20 542,35 640,12 L640,112 L0,112 Z" fill="url(#area)" />
        <path d="M0,86 C64,78 86,48 140,58 C205,71 226,28 286,41 C346,54 374,72 430,49 C498,20 542,35 640,12" fill="none" stroke="#22d3ee" strokeWidth="3" strokeLinecap="round" />
        {[0, 140, 286, 430, 640].map((x, i) => (
          <circle key={x} cx={x} cy={[86, 58, 41, 49, 12][i]} r="4" fill="#0f172a" stroke="#67e8f9" strokeWidth="3" />
        ))}
      </svg>
      <div className="relative flex justify-between text-[11px] text-slate-500"><span>09/06</span><span>09/08</span><span>09/10</span><span>今日</span></div>
    </div>
  );
}

function MapCanvas({ compact = false }: { compact?: boolean }) {
  return (
    <div className={cn("map-canvas relative overflow-hidden rounded-xl border border-slate-200", compact ? "h-72" : "h-[540px]")}>
      <div className="absolute left-4 top-4 z-10 flex gap-2">
        <Badge className="border-slate-700 bg-slate-900/90 text-slate-100"><Wifi className="mr-1 size-3" />1284 个在线点位</Badge>
        {!compact ? <Badge className="border-cyan-500/30 bg-cyan-400/10 text-cyan-100">实时轨迹</Badge> : null}
      </div>
      <svg className="absolute inset-0 h-full w-full" viewBox="0 0 800 520" preserveAspectRatio="none" role="img" aria-label="城市场所与设备点位示意地图">
        <g stroke="#30465f" strokeWidth="2" opacity="0.8" fill="none">
          <path d="M-20 90 Q170 40 330 130 T820 120" />
          <path d="M-20 350 Q150 290 310 360 T820 330" />
          <path d="M120 -20 Q170 160 120 300 T190 550" />
          <path d="M470 -20 Q430 150 500 270 T530 550" />
          <path d="M680 -20 Q620 150 670 300 T720 550" />
          <path d="M0 220 Q230 190 390 250 T800 210" />
        </g>
        <g stroke="#183047" strokeWidth="10" opacity="0.6" fill="none">
          <path d="M-30 420 Q250 390 420 430 T830 390" />
          <path d="M300 -30 Q320 140 300 290 T340 550" />
        </g>
        <path d="M124 392 C190 340 225 286 308 260 S438 160 520 196 S628 238 704 122" fill="none" stroke="#22d3ee" strokeWidth="5" strokeLinecap="round" strokeDasharray="10 8" />
        {[[124, 392], [222, 302], [308, 260], [412, 190], [520, 196], [610, 226], [704, 122]].map(([x, y], index) => (
          <g key={`${x}-${y}`}>
            <circle cx={x} cy={y} r={index === 6 ? 13 : 9} fill={index === 6 ? "#fb7185" : "#0f172a"} stroke={index === 6 ? "#fecdd3" : "#67e8f9"} strokeWidth="4" />
            {index === 6 ? <circle cx={x} cy={y} r="25" fill="none" stroke="#fb7185" opacity=".35" strokeWidth="2" /> : null}
          </g>
        ))}
      </svg>
      <div className="absolute bottom-4 left-4 rounded-lg border border-white/10 bg-slate-950/80 px-3 py-2 text-xs text-slate-300 backdrop-blur">
        <span className="text-cyan-300">7</span> 个途经点位 · 轨迹跨度 42 分钟
      </div>
      <div className="absolute bottom-4 right-4 flex flex-col gap-2">
        <Button size="icon" variant="secondary" aria-label="放大地图">+</Button>
        <Button size="icon" variant="secondary" aria-label="缩小地图">−</Button>
      </div>
    </div>
  );
}

function DashboardView({ navigate }: { navigate: (view: ViewId) => void }) {
  return (
    <div className="space-y-6">
      <SectionHeading
        title="数据总览"
        description="多源采集、研判任务与设备运行态势"
        actions={<><Badge variant="outline" className="bg-white text-slate-600"><StatusDot />数据通道正常</Badge><Button variant="outline" size="sm"><Download />导出日报</Button></>}
      />
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard label="在线设备" value="1,284" meta="在线率 97.6% · 31 台离线" icon={RadioTower} tone="blue" />
        <MetricCard label="今日采集" value="248.6 万" meta="已入库 241.9 万 · 每 30 秒更新" icon={Database} tone="cyan" />
        <MetricCard label="实时告警" value="12" meta="高优先级 3 条 · 待复核 5 条" icon={ShieldAlert} tone="amber" />
        <MetricCard label="研判任务" value="38" meta="运行中 6 项 · 今日完成 11 项" icon={Binoculars} tone="violet" />
      </div>
      <div className="grid gap-4 xl:grid-cols-[1.45fr_.85fr]">
        <Card className="border-slate-200/80 shadow-sm">
          <CardHeader className="pb-0"><div className="flex items-center justify-between"><CardTitle className="text-base">采集数据态势</CardTitle><Badge variant="secondary">实时</Badge></div></CardHeader>
          <CardContent><MiniLineChart /></CardContent>
        </Card>
        <Card className="border-slate-200/80 shadow-sm">
          <CardHeader className="pb-3"><div className="flex items-center justify-between"><CardTitle className="text-base">最新布控告警</CardTitle><Button variant="ghost" size="sm" onClick={() => navigate("surveillance")}>查看全部<ChevronRight /></Button></div></CardHeader>
          <CardContent className="space-y-1">
            {alerts.map((alert) => (
              <button key={`${alert.time}-${alert.name}`} onClick={() => navigate("surveillance")} className="flex w-full items-start gap-3 rounded-lg px-2 py-3 text-left transition-colors hover:bg-slate-50">
                <div className={cn("mt-1 grid size-8 shrink-0 place-items-center rounded-lg", alert.level === "高" ? "bg-rose-50 text-rose-600" : "bg-amber-50 text-amber-600")}><Zap className="size-4" /></div>
                <div className="min-w-0 flex-1"><p className="truncate text-sm font-medium text-slate-800">{alert.name}</p><p className="mt-1 truncate text-xs text-slate-500">{alert.source}</p></div>
                <span className="text-xs tabular-nums text-slate-400">{alert.time}</span>
              </button>
            ))}
          </CardContent>
        </Card>
      </div>
      <div className="grid gap-4 xl:grid-cols-[1.25fr_.75fr]">
        <Card className="border-slate-200/80 shadow-sm">
          <CardHeader className="pb-3"><div className="flex items-center justify-between"><CardTitle className="text-base">设备分布与重点轨迹</CardTitle><Button variant="outline" size="sm" onClick={() => navigate("map")}><Map />进入地图研判</Button></div></CardHeader>
          <CardContent><MapCanvas compact /></CardContent>
        </Card>
        <Card className="border-slate-200/80 shadow-sm">
          <CardHeader><CardTitle className="text-base">设备分类在线率</CardTitle></CardHeader>
          <CardContent className="space-y-5">
            {[["电围设备", "586 / 598", 98], ["车牌卡口", "312 / 326", 96], ["MAC 采集", "248 / 253", 98], ["人像设备", "138 / 144", 96]].map(([label, value, progress]) => (
              <div key={label as string}><div className="mb-2 flex items-center justify-between text-sm"><span className="text-slate-600">{label}</span><span className="font-medium tabular-nums text-slate-900">{value}</span></div><Progress value={progress as number} className="h-2" /></div>
            ))}
            <div className="rounded-xl bg-slate-50 p-4"><div className="flex items-center gap-2 text-sm font-medium text-slate-800"><CircleAlert className="size-4 text-amber-500" />31 台设备需要关注</div><p className="mt-1.5 text-xs leading-5 text-slate-500">其中 8 台持续离线超过 30 分钟，建议优先检查城南片区。</p></div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function SearchView() {
  const [dataType, setDataType] = useState("电围");
  const [keyword, setKeyword] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const filtered = useMemo(() => searchRows.filter((row) => dataType === "全部" || row.type === dataType), [dataType]);
  return (
    <div className="space-y-6">
      <SectionHeading title="数据检索" description="按时间、场所和目标标识检索采集记录" actions={<><Button variant="outline"><Boxes />加入数据集</Button><Button variant="outline"><Download />导出结果</Button></>} />
      <Tabs value={dataType} onValueChange={setDataType}>
        <TabsList className="h-auto flex-wrap bg-white p-1 shadow-sm">
          {["电围", "车牌", "MAC", "人像", "全部"].map((type) => <TabsTrigger key={type} value={type} className="px-5 py-2">{type}</TabsTrigger>)}
        </TabsList>
        <TabsContent value={dataType} className="mt-4 space-y-4">
          <Card className="border-slate-200/80 shadow-sm">
            <CardContent className="grid gap-4 p-5 lg:grid-cols-[1fr_1fr_1.4fr_auto] lg:items-end">
              <div className="space-y-2"><Label>所属场所</Label><Select defaultValue="all"><SelectTrigger className="w-full"><SelectValue /></SelectTrigger><SelectContent><SelectItem value="all">全部场所</SelectItem><SelectItem value="south">城南片区</SelectItem><SelectItem value="center">中心片区</SelectItem></SelectContent></Select></div>
              <div className="space-y-2"><Label>采集日期</Label><Input type="date" defaultValue="2026-09-12" /></div>
              <div className="space-y-2"><Label>{dataType === "车牌" ? "车牌号码" : dataType === "人像" ? "档案或图像编号" : dataType === "MAC" ? "MAC 地址" : "IMSI / IMEI"}</Label><Input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="输入完整或部分标识" /></div>
              <div className="flex gap-2"><Button variant="outline" aria-label="重置" onClick={() => { setKeyword(""); setSubmitted(false); }}><RotateCcw /></Button><Button onClick={() => setSubmitted(true)}><Search />搜索</Button></div>
            </CardContent>
          </Card>
          <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_280px]">
            <Card className="min-w-0 border-slate-200/80 shadow-sm">
              <CardHeader className="flex-row items-center justify-between space-y-0 pb-3"><div><CardTitle className="text-base">检索结果</CardTitle><p className="mt-1 text-xs text-slate-500">{submitted ? `已按“${keyword || "全部标识"}”完成检索` : "展示最新采集记录"}</p></div><Badge variant="secondary">{filtered.length} 条演示记录</Badge></CardHeader>
              <CardContent className="overflow-x-auto p-0">
                <Table>
                  <TableHeader><TableRow><TableHead className="w-12"></TableHead><TableHead>目标标识</TableHead><TableHead>类型</TableHead><TableHead>采集场所</TableHead><TableHead>区域</TableHead><TableHead>最新时间</TableHead><TableHead className="text-right">采集次数</TableHead><TableHead className="w-12"></TableHead></TableRow></TableHeader>
                  <TableBody>{filtered.map((row) => <TableRow key={row.id}><TableCell><Checkbox aria-label={`选择 ${row.id}`} /></TableCell><TableCell className="font-medium text-slate-900">{row.id}</TableCell><TableCell><Badge variant="outline">{row.type}</Badge></TableCell><TableCell>{row.site}</TableCell><TableCell>{row.area}</TableCell><TableCell className="tabular-nums">{row.time}</TableCell><TableCell className="text-right tabular-nums">{row.count}</TableCell><TableCell><Button variant="ghost" size="icon" aria-label="更多操作"><MoreHorizontal /></Button></TableCell></TableRow>)}</TableBody>
                </Table>
              </CardContent>
            </Card>
            <Card className="border-slate-200/80 shadow-sm">
              <CardHeader><CardTitle className="text-base">记录概览</CardTitle></CardHeader>
              <CardContent className="space-y-5">
                <div><p className="text-xs text-slate-500">当前条件采集总次数</p><p className="mt-1 text-2xl font-semibold tabular-nums">64</p></div>
                <div className="grid grid-cols-2 gap-3"><div className="rounded-lg bg-slate-50 p-3"><p className="text-xs text-slate-500">经过站点</p><p className="mt-1 font-semibold tabular-nums">12</p></div><div className="rounded-lg bg-slate-50 p-3"><p className="text-xs text-slate-500">经过区域</p><p className="mt-1 font-semibold tabular-nums">5</p></div></div>
                <div><p className="text-xs text-slate-500">高频出现区域</p><div className="mt-2 flex items-center gap-2 text-sm font-medium"><MapPin className="size-4 text-blue-600" />城南片区</div></div>
                <div className="grid gap-2"><Button variant="outline"><Waypoints />查看轨迹</Button><Button variant="outline"><Users />伴随分析</Button><Button><ShieldAlert />加入临时布控</Button></div>
              </CardContent>
            </Card>
          </div>
        </TabsContent>
      </Tabs>
    </div>
  );
}

function MapView() {
  return (
    <div className="space-y-6">
      <SectionHeading title="地图研判" description="查看目标轨迹、途经点位和关联信息" actions={<><Button variant="outline"><Crosshair />框选点位</Button><Button variant="outline"><RotateCcw />重置地图</Button></>} />
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_320px]">
        <Card className="min-w-0 border-slate-200/80 shadow-sm"><CardContent className="p-3"><MapCanvas /></CardContent></Card>
        <div className="space-y-4">
          <Card className="border-slate-200/80 shadow-sm"><CardHeader><div className="flex items-center justify-between"><CardTitle className="text-base">目标概览</CardTitle><Badge className="bg-cyan-50 text-cyan-700">跟踪中</Badge></div></CardHeader><CardContent className="space-y-4"><div className="flex items-center gap-3"><div className="grid size-12 place-items-center rounded-xl bg-slate-900 text-white"><Fingerprint className="size-6" /></div><div><p className="font-medium text-slate-900">4600•••••7812</p><p className="mt-1 text-xs text-slate-500">电围目标 · 未入人员库</p></div></div><div className="grid grid-cols-2 gap-3 text-sm"><div className="rounded-lg bg-slate-50 p-3"><p className="text-xs text-slate-500">捕捉次数</p><p className="mt-1 font-semibold">18</p></div><div className="rounded-lg bg-slate-50 p-3"><p className="text-xs text-slate-500">活动区域</p><p className="mt-1 font-semibold">3</p></div></div><div className="space-y-2 border-t border-slate-100 pt-4 text-sm"><div className="flex justify-between"><span className="text-slate-500">最早出现</span><span>13:49:26</span></div><div className="flex justify-between"><span className="text-slate-500">最后出现</span><span>14:31:18</span></div><div className="flex justify-between"><span className="text-slate-500">疑似落脚点</span><span>城南客运站</span></div></div><Button className="w-full"><Users />发起伴随分析</Button></CardContent></Card>
          <Card className="border-slate-200/80 shadow-sm"><CardHeader><CardTitle className="text-base">途经点位</CardTitle></CardHeader><CardContent className="space-y-4">{["滨江路站点 03", "中心广场点位", "城南客运站"].map((site, index) => <div key={site} className="flex gap-3"><div className="flex flex-col items-center"><span className="grid size-7 place-items-center rounded-full bg-blue-50 text-xs font-semibold text-blue-700">{index + 1}</span>{index < 2 ? <span className="mt-1 h-6 w-px bg-slate-200" /> : null}</div><div><p className="text-sm font-medium text-slate-800">{site}</p><p className="mt-1 text-xs text-slate-500">{["13:49:26", "14:08:43", "14:31:18"][index]}</p></div></div>)}</CardContent></Card>
        </div>
      </div>
    </div>
  );
}

function AnalysisView({ tasks, onNewTask }: { tasks: typeof baseTasks; onNewTask: () => void }) {
  return (
    <div className="space-y-6">
      <SectionHeading title="数据研判" description="管理伴随、碰撞、区域统计与数据集运算任务" actions={<Button onClick={onNewTask}><Plus />新增研判任务</Button>} />
      <Tabs defaultValue="companion">
        <TabsList className="h-auto flex-wrap bg-white p-1 shadow-sm"><TabsTrigger value="companion">伴随分析</TabsTrigger><TabsTrigger value="collision">碰撞分析</TabsTrigger><TabsTrigger value="region">区域统计</TabsTrigger><TabsTrigger value="dataset">数据运算</TabsTrigger></TabsList>
        <TabsContent value="companion" className="mt-4 space-y-4">
          <div className="grid gap-4 sm:grid-cols-3"><MetricCard label="全部任务" value={String(tasks.length + 24)} meta="近 30 日创建" icon={FileChartColumn} tone="blue" /><MetricCard label="计算中" value={String(tasks.filter((task) => task.status === "计算中").length)} meta="平均预计 6 分钟完成" icon={Activity} tone="cyan" /><MetricCard label="今日产出线索" value="46" meta="待人工复核 17 条" icon={Search} tone="amber" /></div>
          <Card className="border-slate-200/80 shadow-sm"><CardHeader className="flex-row items-center justify-between space-y-0"><CardTitle className="text-base">研判任务</CardTitle><div className="flex gap-2"><Input className="w-52" placeholder="搜索任务名称" /><Button variant="outline"><SlidersHorizontal />筛选</Button></div></CardHeader><CardContent className="overflow-x-auto p-0"><Table><TableHeader><TableRow><TableHead>任务编号</TableHead><TableHead>任务名称</TableHead><TableHead>类型</TableHead><TableHead>创建时间</TableHead><TableHead>创建单位</TableHead><TableHead className="min-w-36">进度</TableHead><TableHead>状态</TableHead></TableRow></TableHeader><TableBody>{tasks.map((task) => <TableRow key={task.id}><TableCell className="font-mono text-xs">{task.id}</TableCell><TableCell className="font-medium">{task.name}</TableCell><TableCell>{task.type}</TableCell><TableCell>{task.created}</TableCell><TableCell>{task.owner}</TableCell><TableCell><div className="flex items-center gap-2"><Progress value={task.progress} className="h-2" /><span className="w-8 text-xs tabular-nums text-slate-500">{task.progress}%</span></div></TableCell><TableCell><Badge className={task.status === "已完成" ? "bg-emerald-50 text-emerald-700" : "bg-blue-50 text-blue-700"}>{task.status}</Badge></TableCell></TableRow>)}</TableBody></Table></CardContent></Card>
        </TabsContent>
        <TabsContent value="collision" className="mt-4"><EmptyAnalysis type="碰撞分析" text="从多个时间和区域条件中筛选重复出现的目标数据。" onNewTask={onNewTask} /></TabsContent>
        <TabsContent value="region" className="mt-4"><EmptyAnalysis type="区域统计" text="统计选区中的目标、站点、经过次数与区域匹配度。" onNewTask={onNewTask} /></TabsContent>
        <TabsContent value="dataset" className="mt-4"><EmptyAnalysis type="数据运算" text="对检索和研判数据集执行交集、并集或差集运算。" onNewTask={onNewTask} /></TabsContent>
      </Tabs>
    </div>
  );
}

function EmptyAnalysis({ type, text, onNewTask }: { type: string; text: string; onNewTask: () => void }) {
  return <Card className="border-slate-200/80 shadow-sm"><CardContent className="flex min-h-72 flex-col items-center justify-center p-8 text-center"><div className="grid size-12 place-items-center rounded-2xl bg-blue-50 text-blue-700"><Network className="size-6" /></div><h2 className="mt-4 font-semibold text-slate-900">{type}工作区</h2><p className="mt-2 max-w-md text-sm leading-6 text-slate-500">{text}</p><Button className="mt-5" onClick={onNewTask}><Plus />新建{type}任务</Button></CardContent></Card>;
}

function SurveillanceView({ onNewTask }: { onNewTask: () => void }) {
  const plans = [["重点人员临时布控", "人像 / 电围", "城南片区", "运行中", "3"], ["重点车辆布控", "车牌", "全市卡口", "运行中", "7"], ["MAC 特征布控", "MAC", "中心片区", "即将结束", "2"]];
  return <div className="space-y-6"><SectionHeading title="布控预警" description="配置布控范围并跟踪实时命中情况" actions={<Button onClick={onNewTask}><Plus />新增布控</Button>} /><div className="grid gap-4 sm:grid-cols-3"><MetricCard label="运行中布控" value="16" meta="今日新建 3 项" icon={ShieldAlert} tone="blue" /><MetricCard label="今日命中" value="12" meta="高优先级 3 条" icon={Zap} tone="amber" /><MetricCard label="待复核告警" value="5" meta="最长等待 18 分钟" icon={Clock3} tone="violet" /></div><div className="grid gap-4 xl:grid-cols-[1.3fr_.7fr]"><Card className="border-slate-200/80 shadow-sm"><CardHeader><CardTitle className="text-base">布控任务</CardTitle></CardHeader><CardContent className="overflow-x-auto p-0"><Table><TableHeader><TableRow><TableHead>布控名称</TableHead><TableHead>目标类型</TableHead><TableHead>范围</TableHead><TableHead>状态</TableHead><TableHead className="text-right">今日命中</TableHead></TableRow></TableHeader><TableBody>{plans.map((plan) => <TableRow key={plan[0]}><TableCell className="font-medium">{plan[0]}</TableCell><TableCell>{plan[1]}</TableCell><TableCell>{plan[2]}</TableCell><TableCell><Badge className={plan[3] === "运行中" ? "bg-emerald-50 text-emerald-700" : "bg-amber-50 text-amber-700"}>{plan[3]}</Badge></TableCell><TableCell className="text-right font-semibold tabular-nums">{plan[4]}</TableCell></TableRow>)}</TableBody></Table></CardContent></Card><Card className="border-slate-200/80 shadow-sm"><CardHeader><div className="flex items-center justify-between"><CardTitle className="text-base">告警动态</CardTitle><Badge variant="outline">实时</Badge></div></CardHeader><CardContent className="space-y-4">{alerts.map((alert) => <div key={`${alert.time}-${alert.name}`} className="flex gap-3 border-b border-slate-100 pb-4 last:border-0 last:pb-0"><div className="mt-1"><StatusDot tone={alert.level === "高" ? "danger" : "warn"} /></div><div className="min-w-0 flex-1"><div className="flex items-center justify-between"><p className="truncate text-sm font-medium">{alert.name}</p><span className="text-xs text-slate-400">{alert.time}</span></div><p className="mt-1 text-xs text-slate-500">{alert.source} · 等待人工复核</p></div></div>)}</CardContent></Card></div></div>;
}

function ModelsView() {
  const cards = [{ title: "图码档案", text: "按一人一档整合人像、围栏、MAC 与车牌关联轨迹。", icon: ScanFace, count: "12,486", tone: "bg-blue-50 text-blue-700" }, { title: "车码档案", text: "按一车一档沉淀车辆轨迹及高关联多维数据。", icon: Car, count: "8,294", tone: "bg-cyan-50 text-cyan-700" }, { title: "数据态势模型", text: "通过可审计的参数规则为数据打标签并筛选线索。", icon: Network, count: "16 个模型", tone: "bg-violet-50 text-violet-700" }];
  return <div className="space-y-6"><SectionHeading title="数据模型" description="管理图码档案、车码档案与标签规则" actions={<Button><Plus />新增模型</Button>} /><div className="grid gap-4 lg:grid-cols-3">{cards.map(({ title, text, icon: Icon, count, tone }) => <Card key={title} className="border-slate-200/80 shadow-sm"><CardContent className="p-6"><div className={cn("grid size-11 place-items-center rounded-xl", tone)}><Icon className="size-5" /></div><h2 className="mt-5 font-semibold text-slate-900">{title}</h2><p className="mt-2 min-h-12 text-sm leading-6 text-slate-500">{text}</p><div className="mt-5 flex items-center justify-between border-t border-slate-100 pt-4"><span className="text-sm font-semibold tabular-nums">{count}</span><Button variant="ghost" size="sm">查看详情<ChevronRight /></Button></div></CardContent></Card>)}</div><Card className="border-slate-200/80 shadow-sm"><CardHeader><CardTitle className="text-base">规则模型运行情况</CardTitle></CardHeader><CardContent className="overflow-x-auto p-0"><Table><TableHeader><TableRow><TableHead>模型名称</TableHead><TableHead>模型类型</TableHead><TableHead>规则版本</TableHead><TableHead>今日命中</TableHead><TableHead>最近运行</TableHead><TableHead>状态</TableHead></TableRow></TableHeader><TableBody>{[["常驻数据识别", "行为标签", "v1.8", "326", "14:30", "启用"], ["昼伏夜出特征", "时空规则", "v1.3", "48", "14:28", "启用"], ["高频跨区域活动", "时空规则", "v2.1", "19", "14:26", "试运行"]].map((row) => <TableRow key={row[0]}>{row.map((cell, index) => <TableCell key={cell} className={index === 0 ? "font-medium" : ""}>{index === 5 ? <Badge className={cell === "启用" ? "bg-emerald-50 text-emerald-700" : "bg-amber-50 text-amber-700"}>{cell}</Badge> : cell}</TableCell>)}</TableRow>)}</TableBody></Table></CardContent></Card></div>;
}

type OpsSection = "home" | "sites" | "devices" | "publish" | "distribution" | "config" | "reports" | "notifications";

const opsSections: Array<{ id: OpsSection; label: string; icon: IconType }> = [
  { id: "home", label: "运维首页", icon: LayoutDashboard },
  { id: "sites", label: "场所管理", icon: Building2 },
  { id: "devices", label: "设备管理", icon: Server },
  { id: "publish", label: "设备发布", icon: MapPin },
  { id: "distribution", label: "设备分布", icon: Map },
  { id: "config", label: "参数配置", icon: SlidersHorizontal },
  { id: "reports", label: "运维报表", icon: FileChartColumn },
  { id: "notifications", label: "运维通知", icon: Bell },
];

const opsSites = [
  { name: "城南客运站", region: "城南片区", type: "交通枢纽", devices: 86, online: 84, status: "正常", updated: "14:35:08" },
  { name: "中心广场点位", region: "中心片区", type: "公共区域", devices: 54, online: 53, status: "正常", updated: "14:34:51" },
  { name: "迎宾大道卡口", region: "高新片区", type: "道路卡口", devices: 42, online: 39, status: "关注", updated: "14:33:26" },
  { name: "滨江路站点 03", region: "城东片区", type: "重点场所", devices: 28, online: 27, status: "正常", updated: "14:32:44" },
];

const opsDevices = [
  { name: "DW-CN-031", serial: "DW2026-031", category: "电围设备", site: "城南客运站", status: "在线", last: "14:35:10", count: "12.8 万", anomaly: "—" },
  { name: "CP-GX-118", serial: "CP2026-118", category: "车牌卡口", site: "迎宾大道卡口", status: "在线", last: "14:35:08", count: "4.6 万", anomaly: "数据骤降" },
  { name: "MAC-ZX-044", serial: "MC2026-044", category: "MAC 采集", site: "中心广场点位", status: "离线", last: "13:52:30", count: "1.8 万", anomaly: "时间异常" },
  { name: "IMG-CN-009", serial: "IMG2026-009", category: "人像设备", site: "城南客运站", status: "在线", last: "14:34:42", count: "2.1 万", anomaly: "—" },
  { name: "DW-BJ-017", serial: "DW2026-017", category: "电围设备", site: "滨江路站点 03", status: "在线", last: "14:35:01", count: "9.7 万", anomaly: "—" },
];

const opsReports = [
  { name: "2026-09-12 区域设备在线率", type: "设备报表", range: "今日 / 全市", created: "14:20", status: "已完成" },
  { name: "2026-09-11 电围区域采集统计", type: "数据报表", range: "昨日 / 城南片区", created: "09:12", status: "已完成" },
  { name: "2026 年 9 月系统组件状态", type: "系统报表", range: "本月 / 全部组件", created: "08:45", status: "生成中" },
];

const opsNotifications = [
  { name: "离线设备日报", type: "短信 + 平台", target: "运维一组（8人）", schedule: "每日 08:30", next: "明日 08:30", status: "运行中" },
  { name: "数据异常周报", type: "邮件", target: "平台主管（3人）", schedule: "每周一 09:00", next: "下周一 09:00", status: "运行中" },
  { name: "重点场所告警", type: "平台通知", target: "城南片区值守组", schedule: "实时", next: "持续运行", status: "已暂停" },
];

function OpsTrendChart({
  title,
  value,
  options,
  selected,
  onSelect,
  tone = "cyan",
  exportable = false,
  onExport,
}: {
  title: string;
  value: string;
  options: string[];
  selected: string;
  onSelect: (value: string) => void;
  tone?: "cyan" | "amber" | "blue" | "violet";
  exportable?: boolean;
  onExport?: () => void;
}) {
  const stroke = { cyan: "#0891b2", amber: "#d97706", blue: "#2563eb", violet: "#7c3aed" }[tone];
  const fill = { cyan: "#cffafe", amber: "#fef3c7", blue: "#dbeafe", violet: "#ede9fe" }[tone];
  return (
    <Card className="border-slate-200/80 shadow-sm">
      <CardHeader className="gap-3 pb-2">
        <div className="flex items-start justify-between gap-3">
          <div>
            <CardTitle className="text-sm">{title}</CardTitle>
            <p className="mt-1 text-lg font-semibold tabular-nums text-slate-950">{value}</p>
          </div>
          <div className="flex items-center gap-1">
            <Select value={selected} onValueChange={onSelect}>
              <SelectTrigger className="h-8 w-[132px] text-xs"><SelectValue /></SelectTrigger>
              <SelectContent>{options.map((option) => <SelectItem key={option} value={option}>{option}</SelectItem>)}</SelectContent>
            </Select>
            {exportable ? <Button variant="ghost" size="icon" className="size-8" aria-label="导出统计结果" onClick={onExport}><Download /></Button> : null}
          </div>
        </div>
      </CardHeader>
      <CardContent className="pt-1">
        <div className="rounded-xl border border-slate-100 bg-slate-50/80 p-2">
          <svg className="h-28 w-full" viewBox="0 0 520 120" role="img" aria-label={title + "趋势图"}>
            <path d="M0 94 C50 88 72 58 122 67 C170 76 205 39 250 55 C300 74 326 22 374 42 C421 62 456 26 520 18 L520 120 L0 120 Z" fill={fill} />
            <path d="M0 94 C50 88 72 58 122 67 C170 76 205 39 250 55 C300 74 326 22 374 42 C421 62 456 26 520 18" fill="none" stroke={stroke} strokeWidth="3" strokeLinecap="round" />
            {[0, 122, 250, 374, 520].map((x, index) => <circle key={x} cx={x} cy={[94, 67, 55, 42, 18][index]} r="4" fill="white" stroke={stroke} strokeWidth="3" />)}
          </svg>
          <div className="flex justify-between px-1 text-[11px] text-slate-400"><span>09/06</span><span>09/08</span><span>09/10</span><span>今日</span></div>
        </div>
      </CardContent>
    </Card>
  );
}

function OperationsView() {
  const [section, setSection] = useState<OpsSection>("home");
  const [range, setRange] = useState("近一周");
  const [chartSelections, setChartSelections] = useState({ collection: "当日采集", devices: "未登记设备", data: "未登记数据数量", volume: "在线无数据设备量" });
  const [notice, setNotice] = useState("");
  const [modal, setModal] = useState<null | { title: string; kind: string }>(null);
  const [siteQuery, setSiteQuery] = useState("");
  const [siteRegion, setSiteRegion] = useState("全部区域");
  const [selectedSite, setSelectedSite] = useState(opsSites[0]);
  const [deviceQuery, setDeviceQuery] = useState("");
  const [deviceStatus, setDeviceStatus] = useState("全部状态");
  const [deviceCategory, setDeviceCategory] = useState("全部分类");
  const [selectedDevice, setSelectedDevice] = useState(opsDevices[0]);
  const [mapMode, setMapMode] = useState("浏览");
  const [reportQuery, setReportQuery] = useState("");
  const [notificationQuery, setNotificationQuery] = useState("");

  const openModal = (title: string, kind: string) => setModal({ title, kind });
  const saveModal = () => {
    setNotice((modal?.title || "任务") + "已保存为演示数据");
    setModal(null);
  };
  const filteredSites = opsSites.filter((site) => (siteQuery.trim() === "" || site.name.includes(siteQuery.trim())) && (siteRegion === "全部区域" || site.region === siteRegion));
  const filteredDevices = opsDevices.filter((device) => (deviceQuery.trim() === "" || device.name.includes(deviceQuery.trim()) || device.serial.includes(deviceQuery.trim())) && (deviceStatus === "全部状态" || device.status === deviceStatus) && (deviceCategory === "全部分类" || device.category === deviceCategory));
  const filteredReports = opsReports.filter((report) => report.name.includes(reportQuery.trim()) || report.type.includes(reportQuery.trim()));
  const filteredNotifications = opsNotifications.filter((item) => item.name.includes(notificationQuery.trim()) || item.type.includes(notificationQuery.trim()));

  const rangeOptions = ["近一周", "近一月", "近一季度", "近一年"];
  const chartOptions = {
    collection: ["当日采集", "当日入库", "统计入库", "日均采集量"],
    devices: ["未登记设备", "重复数据设备", "时间异常设备", "模式异常"],
    data: ["未登记数据数量", "重复数据数量", "时间异常数据数量", "模式异常数据数量", "格式异常数据数量"],
    volume: ["在线无数据设备量", "数据异常增加设备量", "数据异常减少设备量"],
  };

  const renderHome = () => (
    <div className="space-y-5">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard label="设备总数" value="1,569" meta="在线 1,528 台" icon={Server} tone="blue" />
        <MetricCard label="设备在线率" value="97.4%" meta="较昨日 +0.8%" icon={Wifi} tone="cyan" />
        <MetricCard label="今日采集总量" value="248.6 万" meta="已入库 241.9 万" icon={Database} tone="violet" />
        <MetricCard label="异常数据" value="1,842" meta="占今日采集 0.07%" icon={CircleAlert} tone="amber" />
      </div>
      <div className="grid gap-4 xl:grid-cols-[1.25fr_.75fr]">
        <Card className="border-slate-200/80 shadow-sm">
          <CardHeader className="flex-row items-center justify-between space-y-0">
            <div><CardTitle className="text-base">数据采集情况</CardTitle><p className="mt-1 text-xs text-slate-500">按设备类型查看截至目前的采集量和累计入库量</p></div>
            <div className="flex items-center gap-2"><Select value={range} onValueChange={setRange}><SelectTrigger className="w-28"><SelectValue /></SelectTrigger><SelectContent>{rangeOptions.map((item) => <SelectItem key={item} value={item}>{item}</SelectItem>)}</SelectContent></Select><Button variant="outline" size="sm" onClick={() => setNotice("采集数据已刷新")}>刷新</Button></div>
          </CardHeader>
          <CardContent className="grid gap-3 sm:grid-cols-3">
            {[["电围", "126.4 万", "在线设备 598 台"], ["MAC", "68.2 万", "在线设备 253 台"], ["车牌", "42.7 万", "在线设备 326 台"]].map(([label, value, meta]) => <div key={label} className="rounded-xl border border-slate-100 bg-slate-50 p-4"><div className="flex items-center justify-between"><span className="text-sm font-medium text-slate-700">{label}</span><StatusDot /></div><p className="mt-3 text-xl font-semibold tabular-nums text-slate-950">{value}</p><p className="mt-1 text-xs text-slate-500">{meta}</p></div>)}
          </CardContent>
        </Card>
        <Card className="border-slate-200/80 shadow-sm">
          <CardHeader><div className="flex items-center justify-between"><CardTitle className="text-base">短信猫状态</CardTitle><Badge className="bg-emerald-50 text-emerald-700">运行正常</Badge></div></CardHeader>
          <CardContent className="grid grid-cols-2 gap-x-5 gap-y-4 text-sm">
            {[["运行时间", "18天 06时"], ["信号质量", "优 / 92%"], ["信号强度", "-61 dBm"], ["运营商", "中国移动"], ["网络注册", "4 / 4"], ["SIM 卡", "正常"], ["成功发送", "18,426 条"], ["账户余额", "¥ 862.40"]].map(([label, value]) => <div key={label}><p className="text-xs text-slate-500">{label}</p><p className="mt-1 font-medium tabular-nums text-slate-900">{value}</p></div>)}
          </CardContent>
        </Card>
      </div>
      <div className="grid gap-4 xl:grid-cols-2">
        <OpsTrendChart title="电围数据采集量" value="126.4 万" options={chartOptions.collection} selected={chartSelections.collection} onSelect={(value) => setChartSelections((current) => ({ ...current, collection: value }))} />
        <OpsTrendChart title="异常设备统计" value="31 台" options={chartOptions.devices} selected={chartSelections.devices} onSelect={(value) => setChartSelections((current) => ({ ...current, devices: value }))} tone="amber" exportable onExport={() => setNotice("异常设备统计已导出为 Excel 演示文件")} />
        <OpsTrendChart title="异常数据统计" value="1,842 条" options={chartOptions.data} selected={chartSelections.data} onSelect={(value) => setChartSelections((current) => ({ ...current, data: value }))} tone="violet" />
        <OpsTrendChart title="数据量异常设备" value="18 台" options={chartOptions.volume} selected={chartSelections.volume} onSelect={(value) => setChartSelections((current) => ({ ...current, volume: value }))} tone="blue" exportable onExport={() => setNotice("数据量异常设备统计已导出为 Excel 演示文件")} />
      </div>
      {notice ? <div className="rounded-lg border border-cyan-200 bg-cyan-50 px-4 py-3 text-sm text-cyan-800">{notice}</div> : null}
    </div>
  );

  const renderSites = () => (
    <div className="space-y-4">
      <Card className="border-slate-200/80 shadow-sm"><CardContent className="grid gap-3 p-4 lg:grid-cols-[1fr_180px_auto_auto] lg:items-end"><div className="space-y-2"><Label>场所名称</Label><Input value={siteQuery} onChange={(event) => setSiteQuery(event.target.value)} placeholder="输入场所名称" /></div><div className="space-y-2"><Label>所属区域</Label><Select value={siteRegion} onValueChange={setSiteRegion}><SelectTrigger><SelectValue /></SelectTrigger><SelectContent>{["全部区域", "城南片区", "中心片区", "高新片区", "城东片区"].map((item) => <SelectItem key={item} value={item}>{item}</SelectItem>)}</SelectContent></Select></div><Button variant="outline" onClick={() => { setSiteQuery(""); setSiteRegion("全部区域"); }}> <RotateCcw />重置</Button><Button onClick={() => openModal("新增场所", "site")}><Plus />新增场所</Button></CardContent></Card>
      <div className="grid gap-4 xl:grid-cols-[1.35fr_.65fr]">
        <Card className="border-slate-200/80 shadow-sm"><CardHeader className="flex-row items-center justify-between space-y-0"><div><CardTitle className="text-base">场所列表</CardTitle><p className="mt-1 text-xs text-slate-500">支持按名称和区域组合查询，点击行查看设备信息</p></div><Badge variant="secondary">{filteredSites.length} 个场所</Badge></CardHeader><CardContent className="overflow-x-auto p-0"><Table><TableHeader><TableRow><TableHead>场所名称</TableHead><TableHead>区域</TableHead><TableHead>场所类型</TableHead><TableHead>设备数</TableHead><TableHead>在线率</TableHead><TableHead>状态</TableHead><TableHead className="text-right">操作</TableHead></TableRow></TableHeader><TableBody>{filteredSites.map((site) => <TableRow key={site.name} className={selectedSite.name === site.name ? "bg-cyan-50/60" : "cursor-pointer"} onClick={() => setSelectedSite(site)}><TableCell className="font-medium text-slate-900">{site.name}</TableCell><TableCell>{site.region}</TableCell><TableCell>{site.type}</TableCell><TableCell className="tabular-nums">{site.devices}</TableCell><TableCell className="tabular-nums">{Math.round(site.online / site.devices * 100)}%</TableCell><TableCell><Badge className={site.status === "正常" ? "bg-emerald-50 text-emerald-700" : "bg-amber-50 text-amber-700"}>{site.status}</Badge></TableCell><TableCell><div className="flex justify-end gap-1"><Button variant="ghost" size="icon" aria-label="编辑场所" onClick={(event) => { event.stopPropagation(); openModal("编辑场所", "site"); }}><SlidersHorizontal /></Button><Button variant="ghost" size="icon" aria-label="删除场所" onClick={(event) => { event.stopPropagation(); setNotice("已提交删除场所的演示操作"); }}><Trash2 /></Button></div></TableCell></TableRow>)}</TableBody></Table></CardContent></Card>
        <Card className="border-slate-200/80 shadow-sm"><CardHeader><CardTitle className="text-base">场所详情</CardTitle></CardHeader><CardContent className="space-y-4"><div className="rounded-xl bg-slate-950 p-4 text-white"><p className="text-xs text-slate-400">当前选中场所</p><p className="mt-2 text-lg font-semibold">{selectedSite.name}</p><p className="mt-1 text-xs text-slate-400">{selectedSite.region} · {selectedSite.type}</p></div><div className="grid grid-cols-2 gap-3"><div className="rounded-lg bg-slate-50 p-3"><p className="text-xs text-slate-500">设备总数</p><p className="mt-1 text-xl font-semibold">{selectedSite.devices}</p></div><div className="rounded-lg bg-slate-50 p-3"><p className="text-xs text-slate-500">在线设备</p><p className="mt-1 text-xl font-semibold text-emerald-700">{selectedSite.online}</p></div></div><div className="space-y-3 border-t border-slate-100 pt-4 text-sm"><div className="flex justify-between"><span className="text-slate-500">最后更新</span><span>{selectedSite.updated}</span></div><div className="flex justify-between"><span className="text-slate-500">数据状态</span><span className="text-emerald-700">采集正常</span></div></div><Button className="w-full" variant="outline" onClick={() => setSection("devices")}><Server />查看场所设备</Button></CardContent></Card>
      </div>
    </div>
  );

  const renderDevices = () => (
    <div className="space-y-4">
      <Card className="border-slate-200/80 shadow-sm"><CardContent className="grid gap-3 p-4 lg:grid-cols-[1.2fr_180px_180px_auto_auto] lg:items-end"><div className="space-y-2"><Label>设备名称 / 序列号</Label><Input value={deviceQuery} onChange={(event) => setDeviceQuery(event.target.value)} placeholder="输入设备名称或序列号" /></div><div className="space-y-2"><Label>设备状态</Label><Select value={deviceStatus} onValueChange={setDeviceStatus}><SelectTrigger><SelectValue /></SelectTrigger><SelectContent>{["全部状态", "在线", "离线"].map((item) => <SelectItem key={item} value={item}>{item}</SelectItem>)}</SelectContent></Select></div><div className="space-y-2"><Label>设备分类</Label><Select value={deviceCategory} onValueChange={setDeviceCategory}><SelectTrigger><SelectValue /></SelectTrigger><SelectContent>{["全部分类", "电围设备", "车牌卡口", "MAC 采集", "人像设备"].map((item) => <SelectItem key={item} value={item}>{item}</SelectItem>)}</SelectContent></Select></div><Button variant="outline" onClick={() => { setDeviceQuery(""); setDeviceStatus("全部状态"); setDeviceCategory("全部分类"); }}><RotateCcw />重置</Button><Button onClick={() => openModal("新增设备", "device")}><Plus />新增设备</Button></CardContent></Card>
      <div className="grid gap-4 xl:grid-cols-[1.45fr_.55fr]">
        <Card className="border-slate-200/80 shadow-sm"><CardHeader className="flex-row items-center justify-between space-y-0"><div><CardTitle className="text-base">设备列表</CardTitle><p className="mt-1 text-xs text-slate-500">支持设备参数、离线记录、历史采集量以及导入导出</p></div><div className="flex gap-2"><Button variant="outline" size="sm" onClick={() => setNotice("设备信息导入窗口已准备")}><Upload />导入</Button><Button variant="outline" size="sm" onClick={() => setNotice("设备信息已导出为 Excel 演示文件")}><Download />导出</Button></div></CardHeader><CardContent className="overflow-x-auto p-0"><Table><TableHeader><TableRow><TableHead>设备名称</TableHead><TableHead>序列号</TableHead><TableHead>设备分类</TableHead><TableHead>所属场所</TableHead><TableHead>状态</TableHead><TableHead>最后上报</TableHead><TableHead>今日采集</TableHead><TableHead>异常</TableHead></TableRow></TableHeader><TableBody>{filteredDevices.map((device) => <TableRow key={device.name} className={selectedDevice.name === device.name ? "bg-cyan-50/60" : "cursor-pointer"} onClick={() => setSelectedDevice(device)}><TableCell className="font-medium text-slate-900">{device.name}</TableCell><TableCell className="font-mono text-xs">{device.serial}</TableCell><TableCell>{device.category}</TableCell><TableCell>{device.site}</TableCell><TableCell><Badge className={device.status === "在线" ? "bg-emerald-50 text-emerald-700" : "bg-rose-50 text-rose-700"}>{device.status}</Badge></TableCell><TableCell className="tabular-nums">{device.last}</TableCell><TableCell className="tabular-nums">{device.count}</TableCell><TableCell className={device.anomaly === "—" ? "text-slate-400" : "text-amber-700"}>{device.anomaly}</TableCell></TableRow>)}</TableBody></Table></CardContent></Card>
        <Card className="border-slate-200/80 shadow-sm"><CardHeader><div className="flex items-center justify-between"><CardTitle className="text-base">设备详情</CardTitle><Badge variant="outline">{selectedDevice.status}</Badge></div></CardHeader><CardContent className="space-y-4"><div><p className="text-lg font-semibold text-slate-950">{selectedDevice.name}</p><p className="mt-1 font-mono text-xs text-slate-500">{selectedDevice.serial}</p></div><div className="space-y-3 text-sm"><div className="flex justify-between"><span className="text-slate-500">所属场所</span><span>{selectedDevice.site}</span></div><div className="flex justify-between"><span className="text-slate-500">设备分类</span><span>{selectedDevice.category}</span></div><div className="flex justify-between"><span className="text-slate-500">今日采集量</span><span>{selectedDevice.count}</span></div><div className="flex justify-between"><span className="text-slate-500">最后上报</span><span>{selectedDevice.last}</span></div></div><div className="grid grid-cols-2 gap-2 border-t border-slate-100 pt-4"><Button variant="outline" size="sm" onClick={() => openModal("设备参数配置", "device-config")}><SlidersHorizontal />参数</Button><Button variant="outline" size="sm" onClick={() => setNotice(selectedDevice.name + " 的离线记录已打开")}><Clock3 />离线记录</Button><Button variant="outline" size="sm" onClick={() => setNotice(selectedDevice.name + " 的采集历史已打开")}><FileChartColumn />历史记录</Button><Button variant="outline" size="sm" onClick={() => setNotice(selectedDevice.name + " 已提交复位操作")}><RotateCcw />设备复位</Button></div></CardContent></Card></div>
      {notice ? <div className="rounded-lg border border-cyan-200 bg-cyan-50 px-4 py-3 text-sm text-cyan-800">{notice}</div> : null}
    </div>
  );

  const renderMap = (publishMode = false) => (
    <div className="space-y-4">
      <Card className="border-slate-200/80 shadow-sm"><CardContent className="grid gap-3 p-4 lg:grid-cols-[1fr_180px_auto_auto] lg:items-end"><div className="space-y-2"><Label>关键字</Label><Input placeholder="输入场所或设备名称" /></div><div className="space-y-2"><Label>在线状态</Label><Select defaultValue="all"><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="all">全部状态</SelectItem><SelectItem value="online">在线</SelectItem><SelectItem value="offline">离线</SelectItem></SelectContent></Select></div><Button variant="outline" onClick={() => { setMapMode("浏览"); setNotice("地图筛选条件已重置"); }}><RotateCcw />重置</Button><Button onClick={() => setNotice("已按当前条件刷新设备分布")}><Search />查询</Button></CardContent></Card>
      <div className="grid gap-4 xl:grid-cols-[1.25fr_.75fr]">
        <Card className="border-slate-200/80 shadow-sm"><CardHeader className="flex-row items-center justify-between space-y-0"><div><CardTitle className="text-base">{publishMode ? "设备发布地图" : "设备分布地图"}</CardTitle><p className="mt-1 text-xs text-slate-500">可按场所筛选、地图框选或手动选择设备</p></div><div className="flex gap-2"><Button variant={mapMode === "框选" ? "default" : "outline"} size="sm" onClick={() => setMapMode("框选")}><Crosshair />框选</Button><Button variant={mapMode === "手动选择" ? "default" : "outline"} size="sm" onClick={() => setMapMode("手动选择")}><MapPin />手动选择</Button></div></CardHeader><CardContent><div className="relative"><MapCanvas compact={false} /><div className="absolute left-4 bottom-4 rounded-lg border border-white/10 bg-slate-950/80 px-3 py-2 text-xs text-slate-200 backdrop-blur">{mapMode === "浏览" ? "点击地图查看设备" : "当前模式：" + mapMode}</div><div className="absolute right-16 top-4 rounded-lg border border-white/10 bg-slate-950/80 px-3 py-2 text-xs text-slate-200 backdrop-blur"><span className="text-cyan-300">热力图</span> · 采集量</div></div></CardContent></Card>
        <div className="space-y-4"><Card className="border-slate-200/80 shadow-sm"><CardHeader><div className="flex items-center justify-between"><CardTitle className="text-base">已选择设备</CardTitle><Badge variant="secondary">4 台</Badge></div></CardHeader><CardContent className="space-y-2">{opsDevices.slice(0, 4).map((device) => <button key={device.name} onClick={() => setSelectedDevice(device)} className="flex w-full items-center gap-3 rounded-lg border border-slate-100 p-3 text-left hover:bg-slate-50"><span className={cn("size-2 rounded-full", device.status === "在线" ? "bg-emerald-500" : "bg-rose-500")} /><span className="min-w-0 flex-1"><span className="block text-sm font-medium text-slate-800">{device.name}</span><span className="mt-1 block text-xs text-slate-500">{device.site} · {device.category}</span></span><ChevronRight className="size-4 text-slate-400" /></button>)}<div className="grid grid-cols-2 gap-2 pt-2"><Button variant="outline" size="sm" onClick={() => setSection("sites")}><Building2 />场所详情</Button><Button variant="outline" size="sm" onClick={() => setSection("devices")}><Server />设备详情</Button></div></CardContent></Card><Card className="border-slate-200/80 shadow-sm"><CardHeader><CardTitle className="text-base">区域在线率</CardTitle></CardHeader><CardContent className="space-y-4">{[["城南片区", "98.2%"], ["中心片区", "99.1%"], ["高新片区", "94.7%"], ["城东片区", "97.8%"]].map(([label, value], index) => <div key={label}><div className="mb-2 flex justify-between text-sm"><span>{label}</span><span className={index === 2 ? "font-semibold text-amber-700" : "font-semibold text-slate-900"}>{value}</span></div><Progress value={Number(value.replace("%", ""))} /></div>)}</CardContent></Card></div>
      </div>
    </div>
  );

  const renderConfig = () => (
    <div className="space-y-4">
      <Card className="border-slate-200/80 shadow-sm"><CardContent className="grid gap-3 p-4 lg:grid-cols-[1fr_1fr_auto_auto] lg:items-end"><div className="space-y-2"><Label>分组名称</Label><Input placeholder="输入设备分组" /></div><div className="space-y-2"><Label>设备名称</Label><Input placeholder="输入设备名称" /></div><Button variant="outline"><RotateCcw />重置</Button><Button onClick={() => openModal("新增配置任务", "config")}><Plus />新增配置任务</Button></CardContent></Card>
      <div className="grid gap-4 xl:grid-cols-[1.35fr_.65fr]"><Card className="border-slate-200/80 shadow-sm"><CardHeader><div className="flex items-center justify-between"><div><CardTitle className="text-base">参数配置任务</CardTitle><p className="mt-1 text-xs text-slate-500">按照分组选择设备并批量下发参数</p></div><Button variant="outline" size="sm" onClick={() => setNotice("已删除选中的配置任务（演示）")}><Trash2 />删除</Button></div></CardHeader><CardContent className="overflow-x-auto p-0"><Table><TableHeader><TableRow><TableHead className="w-12"></TableHead><TableHead>任务名称</TableHead><TableHead>设备分组</TableHead><TableHead>设备数量</TableHead><TableHead>参数项</TableHead><TableHead>创建时间</TableHead><TableHead>状态</TableHead></TableRow></TableHeader><TableBody>{[["夜间采集策略更新", "城南片区电围", "38", "采集频率 / 上报间隔", "今天 13:20", "执行中"], ["卡口心跳间隔调整", "道路卡口", "16", "心跳周期", "今天 10:15", "已完成"], ["MAC 设备白名单下发", "中心片区 MAC", "22", "白名单", "昨天 18:06", "待执行"]].map((row) => <TableRow key={row[0]}><TableCell><Checkbox aria-label={"选择" + row[0]} /></TableCell><TableCell className="font-medium">{row[0]}</TableCell><TableCell>{row[1]}</TableCell><TableCell className="tabular-nums">{row[2]}</TableCell><TableCell>{row[3]}</TableCell><TableCell>{row[4]}</TableCell><TableCell><Badge className={row[5] === "已完成" ? "bg-emerald-50 text-emerald-700" : "bg-blue-50 text-blue-700"}>{row[5]}</Badge></TableCell></TableRow>)}</TableBody></Table></CardContent></Card><Card className="border-slate-200/80 shadow-sm"><CardHeader><CardTitle className="text-base">配置流程</CardTitle></CardHeader><CardContent className="space-y-5">{[["01", "选择设备分组", "先按场所和区域筛选设备"], ["02", "选择具体设备", "支持批量勾选和在线状态过滤"], ["03", "设置下发参数", "保存任务后进入执行和回执跟踪"]].map(([number, title, text]) => <div key={number} className="flex gap-3"><div className="grid size-8 shrink-0 place-items-center rounded-lg bg-blue-50 text-xs font-semibold text-blue-700">{number}</div><div><p className="text-sm font-medium">{title}</p><p className="mt-1 text-xs leading-5 text-slate-500">{text}</p></div></div>)}</CardContent></Card></div>
    </div>
  );

  const renderReports = () => (
    <div className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-3"><MetricCard label="设备报表" value="24" meta="在线率、实时状态" icon={Server} tone="blue" /><MetricCard label="数据报表" value="68" meta="MAC、电围、车牌" icon={Database} tone="cyan" /><MetricCard label="系统报表" value="12" meta="组件和服务状态" icon={HardDrive} tone="violet" /></div>
      <Card className="border-slate-200/80 shadow-sm"><CardHeader className="flex-row items-center justify-between space-y-0"><div><CardTitle className="text-base">报表任务</CardTitle><p className="mt-1 text-xs text-slate-500">支持单报表查看和多报表 ZIP 下载</p></div><div className="flex gap-2"><Input className="w-52" value={reportQuery} onChange={(event) => setReportQuery(event.target.value)} placeholder="搜索任务名称或类型" /><Button onClick={() => openModal("新增报表任务", "report")}><Plus />新增报表</Button></div></CardHeader><CardContent className="overflow-x-auto p-0"><Table><TableHeader><TableRow><TableHead className="w-12"></TableHead><TableHead>任务名称</TableHead><TableHead>报表类型</TableHead><TableHead>统计范围</TableHead><TableHead>创建时间</TableHead><TableHead>状态</TableHead><TableHead className="text-right">操作</TableHead></TableRow></TableHeader><TableBody>{filteredReports.map((report) => <TableRow key={report.name}><TableCell><Checkbox aria-label={"选择" + report.name} /></TableCell><TableCell className="font-medium">{report.name}</TableCell><TableCell><Badge variant="outline">{report.type}</Badge></TableCell><TableCell>{report.range}</TableCell><TableCell>{report.created}</TableCell><TableCell><Badge className={report.status === "已完成" ? "bg-emerald-50 text-emerald-700" : "bg-blue-50 text-blue-700"}>{report.status}</Badge></TableCell><TableCell><div className="flex justify-end gap-1"><Button variant="ghost" size="icon" aria-label="查看报表详情" onClick={() => setNotice(report.name + " 详情已打开")}><FileText /></Button><Button variant="ghost" size="icon" aria-label="下载报表" onClick={() => setNotice(report.name + " 已下载")}><Download /></Button></div></TableCell></TableRow>)}</TableBody></Table></CardContent></Card>
      <Card className="border-slate-200/80 shadow-sm"><CardHeader><CardTitle className="text-base">报表类型说明</CardTitle></CardHeader><CardContent className="grid gap-3 md:grid-cols-3">{[["设备报表", "区域设备实时在线率、设备实时状态"], ["数据报表", "MAC、电围、车牌的区域、运营商和设备采集统计"], ["系统报表", "系统组件、服务和运行状态"]].map(([title, text]) => <div key={title} className="rounded-xl border border-slate-100 bg-slate-50 p-4"><p className="font-medium text-slate-800">{title}</p><p className="mt-2 text-xs leading-5 text-slate-500">{text}</p></div>)}</CardContent></Card>
    </div>
  );

  const renderNotifications = () => (
    <div className="space-y-4">
      <Card className="border-slate-200/80 shadow-sm"><CardContent className="flex flex-col gap-3 p-4 sm:flex-row sm:items-end"><div className="flex-1 space-y-2"><Label>通知任务名称</Label><Input value={notificationQuery} onChange={(event) => setNotificationQuery(event.target.value)} placeholder="输入通知任务名称或通知类型" /></div><Button variant="outline" onClick={() => setNotificationQuery("")}><RotateCcw />重置</Button><Button onClick={() => openModal("新增通知任务", "notification")}><Plus />新增通知任务</Button></CardContent></Card>
      <Card className="border-slate-200/80 shadow-sm"><CardHeader><div className="flex items-center justify-between"><div><CardTitle className="text-base">运维通知任务</CardTitle><p className="mt-1 text-xs text-slate-500">支持短信、邮件和平台通知，可撤销或删除执行中的任务</p></div><Badge variant="secondary">{filteredNotifications.length} 项任务</Badge></div></CardHeader><CardContent className="overflow-x-auto p-0"><Table><TableHeader><TableRow><TableHead className="w-12"></TableHead><TableHead>任务名称</TableHead><TableHead>通知方式</TableHead><TableHead>通知对象</TableHead><TableHead>执行计划</TableHead><TableHead>下次发送</TableHead><TableHead>状态</TableHead><TableHead className="text-right">操作</TableHead></TableRow></TableHeader><TableBody>{filteredNotifications.map((item) => <TableRow key={item.name}><TableCell><Checkbox aria-label={"选择" + item.name} /></TableCell><TableCell className="font-medium">{item.name}</TableCell><TableCell><div className="flex items-center gap-1.5">{item.type.includes("邮件") ? <Mail className="size-3.5 text-blue-600" /> : <Bell className="size-3.5 text-amber-600" />}{item.type}</div></TableCell><TableCell>{item.target}</TableCell><TableCell>{item.schedule}</TableCell><TableCell>{item.next}</TableCell><TableCell><Badge className={item.status === "运行中" ? "bg-emerald-50 text-emerald-700" : "bg-slate-100 text-slate-600"}>{item.status}</Badge></TableCell><TableCell><div className="flex justify-end gap-1"><Button variant="ghost" size="sm" onClick={() => setNotice(item.name + " 已撤销（演示）")}>撤销</Button><Button variant="ghost" size="icon" aria-label="删除通知任务" onClick={() => setNotice(item.name + " 已删除（演示）")}><Trash2 /></Button></div></TableCell></TableRow>)}</TableBody></Table></CardContent></Card>
    </div>
  );

  const modalFields: Record<string, Array<[string, string]>> = {
    site: [["场所名称", "输入场所名称"], ["所属区域", "选择区域"], ["场所类型", "选择场所类型"]],
    device: [["设备名称", "输入设备名称"], ["序列号", "输入设备序列号"], ["所属场所", "选择所属场所"]],
    "device-config": [["采集频率", "例如：30 秒"], ["上报间隔", "例如：60 秒"], ["备注", "输入配置说明"]],
    config: [["任务名称", "输入配置任务名称"], ["设备分组", "选择设备分组"], ["参数设置", "填写下发参数"]],
    report: [["报表名称", "输入报表名称"], ["报表类型", "选择设备、数据或系统报表"], ["统计周期", "选择年、月或日"]],
    notification: [["任务名称", "输入通知任务名称"], ["通知方式", "选择短信、邮件或平台通知"], ["通知对象", "输入用户或用户组"]],
  };

  return (
    <div className="space-y-6">
      <SectionHeading title="运维服务" description="设备、场所、采集质量、配置和通知的一体化运维工作台" actions={<><Button variant="outline" onClick={() => setNotice("运维数据已刷新")}><RotateCcw />刷新数据</Button><Button onClick={() => openModal("新增报表任务", "report")}><FileChartColumn />生成报表</Button></>} />
      <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white p-1 shadow-sm">
        <div className="flex min-w-max gap-1">
          {opsSections.map(({ id, label, icon: Icon }) => <button key={id} onClick={() => setSection(id)} className={cn("flex items-center gap-2 rounded-lg px-3 py-2.5 text-sm transition-colors", section === id ? "bg-slate-950 text-white shadow-sm" : "text-slate-600 hover:bg-slate-50")}><Icon className="size-4" />{label}</button>)}
        </div>
      </div>
      {section === "home" ? renderHome() : null}
      {section === "sites" ? renderSites() : null}
      {section === "devices" ? renderDevices() : null}
      {section === "publish" ? renderMap(true) : null}
      {section === "distribution" ? renderMap(false) : null}
      {section === "config" ? renderConfig() : null}
      {section === "reports" ? renderReports() : null}
      {section === "notifications" ? renderNotifications() : null}
      <Dialog open={Boolean(modal)} onOpenChange={(open) => !open && setModal(null)}>
        <DialogContent>
          <DialogHeader><DialogTitle>{modal?.title}</DialogTitle><DialogDescription>当前为前端交互原型，保存后会在本地生成一条演示记录。</DialogDescription></DialogHeader>
          <div className="grid gap-4 py-2 sm:grid-cols-2">{(modal ? modalFields[modal.kind] || [] : []).map(([label, placeholder]) => <div key={label} className="space-y-2"><Label>{label}</Label><Input placeholder={placeholder} /></div>)}</div>
          <DialogFooter><Button variant="outline" onClick={() => setModal(null)}>取消</Button><Button onClick={saveModal}>保存</Button></DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function SettingsView() {
  return <div className="space-y-6"><SectionHeading title="系统设置" description="维护用户权限及操作审计" actions={<Button><Plus />新增用户</Button>} /><Tabs defaultValue="roles"><TabsList className="bg-white p-1 shadow-sm"><TabsTrigger value="roles">角色管理</TabsTrigger><TabsTrigger value="users">用户管理</TabsTrigger><TabsTrigger value="logs">操作日志</TabsTrigger><TabsTrigger value="messages">通知日志</TabsTrigger></TabsList><TabsContent value="roles" className="mt-4"><div className="grid gap-4 xl:grid-cols-[.75fr_1.25fr]"><Card className="border-slate-200/80 shadow-sm"><CardHeader><CardTitle className="text-base">权限设计原则</CardTitle></CardHeader><CardContent className="space-y-4">{[["菜单权限", "控制可访问的功能模块"], ["数据权限", "按区域、场所和数据类型授权"], ["密级权限", "控制敏感字段查看与解密"], ["操作权限", "限制导出、布控和删除等动作"]].map(([title, text]) => <div key={title} className="flex gap-3"><CheckCircle2 className="mt-0.5 size-4 shrink-0 text-blue-600" /><div><p className="text-sm font-medium">{title}</p><p className="mt-1 text-xs leading-5 text-slate-500">{text}</p></div></div>)}</CardContent></Card><Card className="border-slate-200/80 shadow-sm"><CardHeader><CardTitle className="text-base">角色列表</CardTitle></CardHeader><CardContent className="overflow-x-auto p-0"><Table><TableHeader><TableRow><TableHead>角色名称</TableHead><TableHead>用户数</TableHead><TableHead>数据范围</TableHead><TableHead>密级</TableHead><TableHead>状态</TableHead></TableRow></TableHeader><TableBody>{[["超级管理员", "3", "全部区域", "涉密", "启用"], ["研判人员", "42", "授权区域", "涉密", "启用"], ["运维人员", "18", "设备与场所", "内部", "启用"], ["审计人员", "6", "审计日志", "内部", "启用"]].map((row) => <TableRow key={row[0]}>{row.map((cell, index) => <TableCell key={cell} className={index === 0 ? "font-medium" : ""}>{index === 4 ? <Badge className="bg-emerald-50 text-emerald-700">{cell}</Badge> : cell}</TableCell>)}</TableRow>)}</TableBody></Table></CardContent></Card></div></TabsContent><TabsContent value="users" className="mt-4"><EmptyAnalysis type="用户管理" text="维护用户账号、角色、授权区域与启用状态。" onNewTask={() => undefined} /></TabsContent><TabsContent value="logs" className="mt-4"><EmptyAnalysis type="操作日志" text="记录查询、解密、导出、布控和管理操作，支持审计追溯。" onNewTask={() => undefined} /></TabsContent><TabsContent value="messages" className="mt-4"><EmptyAnalysis type="通知日志" text="查看站内消息、短信与邮件的发送记录及送达状态。" onNewTask={() => undefined} /></TabsContent></Tabs></div>;
}

type ModelContext = {
  registerTool: (tool: {
    name: string;
    title: string;
    description: string;
    inputSchema: Record<string, unknown>;
    annotations?: { readOnlyHint?: boolean; untrustedContentHint?: boolean };
    execute: (input: Record<string, unknown>) => unknown;
  }, options?: { signal?: AbortSignal }) => void | Promise<void>;
};

export default function Home() {
  const [activeView, setActiveView] = useState<ViewId>("dashboard");
  const [tasks, setTasks] = useState(baseTasks);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [taskName, setTaskName] = useState("");
  const [taskType, setTaskType] = useState("伴随分析");
  const meta = viewMeta[activeView];

  const createTask = () => {
    const nextName = taskName.trim() || `${taskType}演示任务`;
    setTasks((current) => [{ id: `YP-20260912-${String(current.length + 32).padStart(3, "0")}`, name: nextName, type: taskType, created: "刚刚", owner: "当前用户", progress: 0, status: "计算中" }, ...current]);
    setTaskName("");
    setDialogOpen(false);
    setActiveView("analysis");
  };

  useEffect(() => {
    const context = (document as Document & { modelContext?: ModelContext }).modelContext;
    if (!context?.registerTool) return;
    const lifecycle = new AbortController();
    const allowedViews: ViewId[] = ["dashboard", "search", "map", "analysis", "surveillance", "models", "operations", "settings"];
    void Promise.resolve(context.registerTool({
      name: "navigate_platform_module",
      title: "打开平台模块",
      description: "在多维数据研判平台原型中打开指定模块。",
      inputSchema: { type: "object", properties: { module: { type: "string", enum: allowedViews } }, required: ["module"], additionalProperties: false },
      annotations: { readOnlyHint: true, untrustedContentHint: false },
      execute(input) {
        const module = input.module;
        if (typeof module !== "string" || !allowedViews.includes(module as ViewId)) throw new Error("不支持的平台模块");
        setActiveView(module as ViewId);
        return { module, title: viewMeta[module as ViewId].title };
      },
    }, { signal: lifecycle.signal })).catch(() => undefined);
    void Promise.resolve(context.registerTool({
      name: "start_analysis_task",
      title: "准备研判任务",
      description: "打开新增研判任务窗口，并预填任务名称与类型；不会提交任务。",
      inputSchema: { type: "object", properties: { name: { type: "string" }, type: { type: "string", enum: ["伴随分析", "碰撞分析", "区域统计", "数据运算"] } }, required: ["name", "type"], additionalProperties: false },
      annotations: { readOnlyHint: false, untrustedContentHint: false },
      execute(input) {
        if (typeof input.name !== "string" || typeof input.type !== "string") throw new Error("任务名称和类型不能为空");
        setTaskName(input.name.slice(0, 40));
        setTaskType(input.type);
        setActiveView("analysis");
        setDialogOpen(true);
        return { status: "staged", name: input.name, type: input.type };
      },
    }, { signal: lifecycle.signal })).catch(() => undefined);
    return () => lifecycle.abort();
  }, []);

  return (
    <SidebarProvider style={{ "--sidebar-width": "16.5rem" } as CSSProperties}>
      <Sidebar collapsible="icon" className="border-r-0">
        <SidebarHeader className="border-b border-white/10 px-4 py-4">
          <div className="flex items-center gap-3 overflow-hidden">
            <div className="grid size-9 shrink-0 place-items-center rounded-xl bg-cyan-400 text-slate-950"><Waypoints className="size-5" /></div>
            <div className="min-w-0 group-data-[collapsible=icon]:hidden"><p className="truncate text-sm font-semibold tracking-wide text-white">多维数据研判平台</p><p className="mt-0.5 text-xs text-slate-400">页面交互原型</p></div>
          </div>
        </SidebarHeader>
        <SidebarContent className="px-2 py-3">
          {navigation.map((group) => <SidebarGroup key={group.label}><SidebarGroupLabel className="text-[11px] font-medium tracking-wider text-slate-500">{group.label}</SidebarGroupLabel><SidebarGroupContent><SidebarMenu>{group.items.map((item) => <SidebarMenuItem key={item.id}><SidebarMenuButton isActive={activeView === item.id} tooltip={item.label} onClick={() => setActiveView(item.id)} className="h-10 text-slate-300 hover:bg-white/8 hover:text-white data-[active=true]:bg-cyan-400/12 data-[active=true]:text-cyan-200"><item.icon className="size-4" /><span>{item.label}</span></SidebarMenuButton></SidebarMenuItem>)}</SidebarMenu></SidebarGroupContent></SidebarGroup>)}
        </SidebarContent>
        <SidebarFooter className="border-t border-white/10 p-3"><div className="flex items-center gap-3 overflow-hidden rounded-xl bg-white/5 p-2.5"><div className="grid size-8 shrink-0 place-items-center rounded-lg bg-blue-500/20 text-blue-200"><Users className="size-4" /></div><div className="min-w-0 group-data-[collapsible=icon]:hidden"><p className="truncate text-xs font-medium text-slate-200">研判演示用户</p><p className="mt-0.5 text-[11px] text-slate-500">授权区域 · 全市</p></div></div></SidebarFooter>
        <SidebarRail />
      </Sidebar>
      <SidebarInset className="min-w-0 bg-[#f3f6fa]">
        <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b border-slate-200/80 bg-white/90 px-4 backdrop-blur-md sm:px-6">
          <SidebarTrigger className="-ml-1" /><div className="h-5 w-px bg-slate-200" /><div className="min-w-0"><p className="truncate text-sm font-medium text-slate-800">{meta.title}</p><p className="hidden truncate text-xs text-slate-400 sm:block">{meta.description}</p></div>
          <div className="ml-auto flex items-center gap-2"><div className="hidden items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-500 md:flex"><CalendarDays className="size-3.5" />2026 年 9 月 12 日<span className="text-slate-300">·</span>14:36</div><Button variant="ghost" size="icon" className="relative" aria-label="查看通知"><Bell /><span className="absolute right-1.5 top-1.5 size-2 rounded-full border-2 border-white bg-rose-500" /></Button></div>
        </header>
        <main className="mx-auto w-full max-w-[1600px] p-4 sm:p-6 lg:p-7">
          {activeView === "dashboard" && <DashboardView navigate={setActiveView} />}
          {activeView === "search" && <SearchView />}
          {activeView === "map" && <MapView />}
          {activeView === "analysis" && <AnalysisView tasks={tasks} onNewTask={() => setDialogOpen(true)} />}
          {activeView === "surveillance" && <SurveillanceView onNewTask={() => { setTaskType("布控任务"); setDialogOpen(true); }} />}
          {activeView === "models" && <ModelsView />}
          {activeView === "operations" && <OperationsView />}
          {activeView === "settings" && <SettingsView />}
        </main>
      </SidebarInset>
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          <DialogHeader><DialogTitle>{taskType === "布控任务" ? "新增布控" : "新增研判任务"}</DialogTitle><DialogDescription>当前为前端交互原型，提交后仅会在本地页面生成一条演示任务。</DialogDescription></DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2"><Label htmlFor="task-name">任务名称</Label><Input id="task-name" value={taskName} onChange={(event) => setTaskName(event.target.value)} placeholder="输入便于识别的任务名称" /></div>
            {taskType !== "布控任务" ? <div className="space-y-2"><Label>任务类型</Label><Select value={taskType} onValueChange={setTaskType}><SelectTrigger className="w-full"><SelectValue /></SelectTrigger><SelectContent><SelectItem value="伴随分析">伴随分析</SelectItem><SelectItem value="碰撞分析">碰撞分析</SelectItem><SelectItem value="区域统计">区域统计</SelectItem><SelectItem value="数据运算">数据运算</SelectItem></SelectContent></Select></div> : <div className="grid grid-cols-2 gap-4"><div className="space-y-2"><Label>目标类型</Label><Select defaultValue="person"><SelectTrigger className="w-full"><SelectValue /></SelectTrigger><SelectContent><SelectItem value="person">人员 / 人像</SelectItem><SelectItem value="vehicle">车辆</SelectItem><SelectItem value="mac">MAC</SelectItem></SelectContent></Select></div><div className="space-y-2"><Label>告警级别</Label><Select defaultValue="high"><SelectTrigger className="w-full"><SelectValue /></SelectTrigger><SelectContent><SelectItem value="high">高</SelectItem><SelectItem value="medium">中</SelectItem><SelectItem value="low">低</SelectItem></SelectContent></Select></div></div>}
          </div>
          <DialogFooter><Button variant="outline" onClick={() => setDialogOpen(false)}>取消</Button><Button onClick={createTask}>{taskType === "布控任务" ? "保存布控草案" : "创建任务"}</Button></DialogFooter>
        </DialogContent>
      </Dialog>
    </SidebarProvider>
  );
}
