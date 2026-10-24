import { useEffect, useRef, useState } from 'react';
import { CheckCircleOutlined, CloseCircleOutlined, LoadingOutlined, ClockCircleOutlined } from '@ant-design/icons';
import { Space, Typography } from 'antd';
import type { AgentProgressEvent } from '../../services/apiAgentStream';

const { Text } = Typography;
export default function AgentActivity({ events, active, startedAt, lastContactAt }: {
  events: AgentProgressEvent[]; active: boolean; startedAt: number; lastContactAt?: number;
}) {
  const [now, setNow] = useState(Date.now());
  const listRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (active && listRef.current) listRef.current.scrollTop = listRef.current.scrollHeight;
  }, [events, active]);
  useEffect(() => {
    if (!active) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [active]);
  const running = events.filter(event => event.status === 'running');
  const completed = events.filter(event => event.status === 'completed').length;
  return <div className="agent-activity" role="status" aria-label="助手处理进度">
    {active && <Space size={8} className="agent-activity-heading">
      <LoadingOutlined spin />
      <Text strong>{running.at(-1)?.label ?? '正在等待下一步处理'}</Text>
    </Space>}
    {active && <Text type="secondary" className="agent-activity-meta">
      已用时 {Math.max(0, Math.floor((now - startedAt) / 1000))} 秒 · 已完成 {completed} 步
      {lastContactAt && now - lastContactAt < 25000 ? ' · 连接正常' : ' · 等待服务器响应'}
    </Text>}
    <div className="agent-activity-list" ref={listRef}>
      {events.map(event => <div key={event.id} className="agent-activity-step">
        {event.status === 'completed' ? <CheckCircleOutlined style={{ color: 'var(--ant-color-success, #52c41a)' }} />
          : event.status === 'failed' ? <CloseCircleOutlined style={{ color: 'var(--ant-color-error, #ff4d4f)' }} />
          : active ? <LoadingOutlined spin /> : <ClockCircleOutlined />}
        <div><Text>{event.label}</Text><Text type="secondary" className="agent-activity-detail">
          {event.status === 'running' && !active ? '连接结束，未确认本步骤结果' : event.detail}
        </Text></div>
      </div>)}
    </div>
  </div>;
}
