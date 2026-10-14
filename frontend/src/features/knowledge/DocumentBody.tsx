import React, { useEffect, useRef } from 'react';
import { Typography, theme } from 'antd';
import type { KnowledgeFormat } from '../../services/apiKnowledge';

const inline = (text: string): React.ReactNode[] => {
  const pattern = /(\*\*[^*]+\*\*|__[^_]+__|`[^`]+`|\[[^\]]+\]\([^)]+\)|\*[^*\n]+\*)/g;
  const parts: React.ReactNode[] = [];
  let cursor = 0;
  for (const match of text.matchAll(pattern)) {
    const start = match.index ?? 0;
    if (start > cursor) parts.push(text.slice(cursor, start));
    const value = match[0];
    if (value.startsWith('**') || value.startsWith('__')) {
      parts.push(<strong key={start}>{value.slice(2, -2)}</strong>);
    } else if (value.startsWith('`')) {
      parts.push(<Typography.Text key={start} code>{value.slice(1, -1)}</Typography.Text>);
    } else if (value.startsWith('*')) {
      parts.push(<em key={start}>{value.slice(1, -1)}</em>);
    } else {
      const link = /^\[([^\]]+)\]\(([^)]+)\)$/.exec(value);
      parts.push(link && /^https?:\/\//i.test(link[2])
        ? <a key={start} href={link[2]} target="_blank" rel="noopener noreferrer">{link[1]}</a>
        : value);
    }
    cursor = start + value.length;
  }
  if (cursor < text.length) parts.push(text.slice(cursor));
  return parts;
};

/** React text nodes preserve original text and never execute HTML contained in notes. */
const DocumentBody: React.FC<{ body: string; format: KnowledgeFormat; raw?: boolean; focusText?: string }> = ({ body, format, raw, focusText }) => {
  const { token } = theme.useToken();
  const mark = useRef<HTMLElement>(null);
  useEffect(() => {
    if (focusText && mark.current) mark.current.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }, [body, focusText, raw]);
  if (format !== 'MARKDOWN' || raw) {
    const start = focusText ? body.indexOf(focusText) : -1;
    return <pre style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', font: 'inherit', lineHeight: 1.8, margin: 0 }}>{start >= 0 && focusText ? <>{body.slice(0, start)}<mark ref={mark} style={{ color: token.colorText, background: token.colorWarningBg, padding: 2 }}>{focusText}</mark>{body.slice(start + focusText.length)}</> : body}</pre>;
  }
  const lines = body.replace(/\r\n/g, '\n').split('\n');
  const blocks: React.ReactNode[] = [];
  let paragraph: string[] = [];
  let code: string[] | null = null;
  const flush = () => {
    if (paragraph.length) {
      blocks.push(<Typography.Paragraph key={`p-${blocks.length}`} style={{ whiteSpace: 'pre-wrap', lineHeight: 1.8 }}>{inline(paragraph.join('\n'))}</Typography.Paragraph>);
      paragraph = [];
    }
  };
  const flushCode = () => {
    if (code) {
      blocks.push(<pre key={`code-${blocks.length}`} style={{ whiteSpace: 'pre-wrap', background: token.colorFillAlter, borderRadius: 8, padding: 16, overflowWrap: 'anywhere' }}>{code.join('\n')}</pre>);
      code = null;
    }
  };
  for (let lineIndex = 0; lineIndex < lines.length; lineIndex += 1) {
    const line = lines[lineIndex];
    if (/^\s*```/.test(line)) {
      flush();
      if (code) flushCode(); else code = [];
    } else if (code) {
      code.push(line);
    } else {
      const heading = /^(#{1,6})\s+(.+)$/.exec(line);
      const list = /^\s*([-*+]|\d+[.)])\s+(.+)$/.exec(line);
      const tableSeparator = lines[lineIndex + 1]?.trim();
      if (line.includes('|') && tableSeparator && /^\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?$/.test(tableSeparator)) {
        flush();
        const cells = (row: string) => row.trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map((cell) => cell.trim());
        const header = cells(line);
        const rows: string[][] = [];
        lineIndex += 2;
        while (lineIndex < lines.length && lines[lineIndex].includes('|') && lines[lineIndex].trim()) {
          rows.push(cells(lines[lineIndex])); lineIndex += 1;
        }
        lineIndex -= 1;
        const cellStyle = { padding: '8px 12px', border: `1px solid ${token.colorBorder}`, textAlign: 'left' as const };
        blocks.push(<div key={`table-${blocks.length}`} style={{ overflowX: 'auto', marginBottom: 16 }}><table style={{ borderCollapse: 'collapse', width: '100%' }}>
          <thead><tr>{header.map((cell, index) => <th key={index} style={{ ...cellStyle, background: token.colorFillAlter }}>{inline(cell)}</th>)}</tr></thead>
          <tbody>{rows.map((row, index) => <tr key={index}>{header.map((_, cellIndex) => <td key={cellIndex} style={cellStyle}>{inline(row[cellIndex] ?? '')}</td>)}</tr>)}</tbody>
        </table></div>);
      } else if (heading) {
        flush();
        const level = Math.min(heading[1].length + 1, 5) as 2 | 3 | 4 | 5;
        blocks.push(<Typography.Title key={`h-${blocks.length}`} level={level}>{inline(heading[2])}</Typography.Title>);
      } else if (list) {
        flush();
        blocks.push(<div key={`li-${blocks.length}`} style={{ display: 'flex', gap: 10, marginBottom: 6, lineHeight: 1.8 }}><span>{/^\d/.test(list[1]) ? list[1] : '•'}</span><span>{inline(list[2])}</span></div>);
      } else if (/^>\s?/.test(line)) {
        flush();
        blocks.push(<blockquote key={`q-${blocks.length}`} style={{ margin: '12px 0', paddingLeft: 16, borderLeft: `3px solid ${token.colorBorder}`, whiteSpace: 'pre-wrap' }}>{inline(line.replace(/^>\s?/, ''))}</blockquote>);
      } else if (!line.trim()) {
        flush();
      } else {
        paragraph.push(line);
      }
    }
  }
  flush();
  flushCode();
  return <div style={{ overflowWrap: 'anywhere' }}>{blocks}</div>;
};

export default DocumentBody;
