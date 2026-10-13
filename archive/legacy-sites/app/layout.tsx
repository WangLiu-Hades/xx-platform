import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "多维数据研判平台",
  description: "多源数据检索、地图研判、布控预警与设备运维页面原型。",
  other: {
    "codex-preview": "development",
  },
  icons: {
    icon: "/favicon.svg",
    shortcut: "/favicon.svg",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN">
      <body className="antialiased">{children}</body>
    </html>
  );
}
