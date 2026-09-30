import { useEffect, useState } from 'react';
import { Alert, Card, Radio, Space, Spin, Typography, message } from 'antd';
import { apiAgentPermission, type AiPermissionLevel } from '../../services/apiAgentPermission';

const options: { level: AiPermissionLevel; title: string; description: string }[] = [
  { level: 'READ_ONLY', title: '只读', description: 'AI 只能查询和分析资产数据，不能提出或执行增删改操作。' },
  { level: 'REVIEW', title: '需要审核（默认）', description: 'AI 可以准备增删改草案，由你核对并点击确认后写入。' },
  { level: 'DIRECT', title: '直接执行', description: '你明确要求修改时，AI 生成的操作经后端校验后立即执行，不再等待你审核。' },
];

export default function AiPermissionSettings() {
  const [level, setLevel] = useState<AiPermissionLevel | null>(null);
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    apiAgentPermission.get()
      .then(result => { if (active) setLevel(result.level); })
      .catch(err => { if (active) message.error(err instanceof Error ? err.message : 'AI 权限读取失败'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  const changeLevel = async (next: AiPermissionLevel) => {
    if (saving || next === level) return;
    setSaving(true);
    try {
      const saved = await apiAgentPermission.update(next);
      setLevel(saved.level);
      message.success('AI 权限已保存');
    } catch (err) {
      message.error(err instanceof Error ? err.message : 'AI 权限保存失败');
    } finally {
      setSaving(false);
    }
  };

  return <Card title="AI 权限" style={{ maxWidth: 600, marginBottom: 16 }}>
    {loading ? <Spin /> : <>
      <Radio.Group value={level} disabled={saving || level === null}
        onChange={event => void changeLevel(event.target.value as AiPermissionLevel)}>
        <Space direction="vertical" size="middle">
          {options.map(option => <Radio key={option.level} value={option.level}>
            <Typography.Text strong>{option.title}</Typography.Text>
            <div><Typography.Text type="secondary">{option.description}</Typography.Text></div>
          </Radio>)}
        </Space>
      </Radio.Group>
      {level === 'DIRECT' && <Alert type="warning" showIcon style={{ marginTop: 16 }}
        message="AI 修改将立即写入"
        description="包括批量新增、修改和删除快照；删除后不可恢复。执行前仍会校验日期、分类、金额及操作范围。" />}
    </>}
  </Card>;
}
