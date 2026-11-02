/**
 * 对话气泡的轻量 Markdown 渲染。
 *
 * 大模型输出和规则模板都经过这里：先整体转义 HTML，再只开放少量安全标记
 * （加粗、行内代码、列表、小标题），避免把模型输出直接当 HTML 注入页面。
 */
const escapeHtml = (value: string) =>
  value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');

function inline(text: string): string {
  return text
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\*([^*\n]+)\*/g, '<em>$1</em>');
}

/** 把受限 Markdown 转为可渲染 HTML；输入内容在转义之后才应用标记。 */
export function renderAgentMarkdown(source: string): string {
  const lines = escapeHtml(source.replace(/\r\n/g, '\n')).split('\n');
  const html: string[] = [];
  let list: 'ul' | 'ol' | null = null;
  const closeList = () => {
    if (list) {
      html.push('</' + list + '>');
      list = null;
    }
  };
  for (const raw of lines) {
    const line = raw.trimEnd();
    if (!line.trim()) {
      closeList();
      continue;
    }
    const heading = line.match(/^(#{1,4})\s+(.*)$/);
    if (heading) {
      closeList();
      const level = Math.min(4, heading[1]!.length) + 2;
      html.push('<h' + level + '>' + inline(heading[2]!.trim()) + '</h' + level + '>');
      continue;
    }
    const unordered = line.match(/^\s*[-*]\s+(.*)$/);
    if (unordered) {
      if (list !== 'ul') {
        closeList();
        list = 'ul';
        html.push('<ul>');
      }
      html.push('<li>' + inline(unordered[1]!) + '</li>');
      continue;
    }
    const ordered = line.match(/^\s*\d+[.)]\s+(.*)$/);
    if (ordered) {
      if (list !== 'ol') {
        closeList();
        list = 'ol';
        html.push('<ol>');
      }
      html.push('<li>' + inline(ordered[1]!) + '</li>');
      continue;
    }
    closeList();
    html.push('<p>' + inline(line) + '</p>');
  }
  closeList();
  return html.join('');
}
