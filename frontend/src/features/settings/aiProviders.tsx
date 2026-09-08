import React, { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Input,
  List,
  Modal,
  Popconfirm,
  Select,
  Space,
  Tag,
  Typography,
  message,
} from 'antd';
import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import {
  aiProviderService,
  type AiProviderConfig,
  type AiProviderTestResult,
} from '../../services/apiAgentProviders';

interface ProviderPreset {
  id: string;
  label: string;
  defaultBaseUrl: string;
  defaultModel: string;
}

const PROVIDER_PRESETS: ProviderPreset[] = [
  {
    id: 'openai',
    label: 'OpenAI',
    defaultBaseUrl: 'https://api.openai.com/v1',
    defaultModel: 'gpt-4o-mini',
  },
  {
    id: 'deepseek',
    label: 'DeepSeek',
    defaultBaseUrl: 'https://api.deepseek.com/v1',
    defaultModel: 'deepseek-chat',
  },
  {
    id: 'qwen',
    label: '千问',
    defaultBaseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    defaultModel: 'qwen-plus',
  },
  {
    id: 'kimi',
    label: 'Kimi',
    defaultBaseUrl: 'https://api.moonshot.cn/v1',
    defaultModel: 'moonshot-v1-8k',
  },
];

const CUSTOM_TYPE = 'custom';
const PROVIDER_ID_PATTERN = /^[a-zA-Z0-9_-]{1,64}$/;

interface FormState {
  providerType: string;
  providerId: string;
  displayName: string;
  baseUrl: string;
  model: string;
  apiKey: string;
}

const EMPTY_FORM: FormState = {
  providerType: PROVIDER_PRESETS[0].id,
  providerId: PROVIDER_PRESETS[0].id,
  displayName: PROVIDER_PRESETS[0].label,
  baseUrl: PROVIDER_PRESETS[0].defaultBaseUrl,
  model: PROVIDER_PRESETS[0].defaultModel,
  apiKey: '',
};

/**
 * “AI 模型提供商”设置卡片。
 *
 * API Key 使用 Password 输入并禁用浏览器自动填充；仅存在于 React state，
 * 保存请求完成后立即清空，不落入任何持久化机制。
 */
const AiProviderSettings: React.FC = () => {
  const [providers, setProviders] = useState<AiProviderConfig[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<AiProviderConfig | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const [testingIds, setTestingIds] = useState<Record<string, boolean>>({});
  const [testResults, setTestResults] = useState<
    Record<string, AiProviderTestResult>
  >({});

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      setProviders(await aiProviderService.getAll());
    } catch (err) {
      message.error(err instanceof Error ? err.message : '提供商配置加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const openCreate = (preset: ProviderPreset) => {
    setEditing(null);
    setForm({
      providerType: preset.id,
      providerId: preset.id,
      displayName: preset.label,
      baseUrl: preset.defaultBaseUrl,
      model: preset.defaultModel,
      apiKey: '',
    });
    setModalOpen(true);
  };

  const openCreateCustom = () => {
    setEditing(null);
    setForm({
      providerType: CUSTOM_TYPE,
      providerId: '',
      displayName: '',
      baseUrl: '',
      model: '',
      apiKey: '',
    });
    setModalOpen(true);
  };

  const openEdit = (config: AiProviderConfig) => {
    setEditing(config);
    setForm({
      providerType: PROVIDER_PRESETS.some((p) => p.id === config.providerId)
        ? config.providerId
        : CUSTOM_TYPE,
      providerId: config.providerId,
      displayName: config.displayName,
      baseUrl: config.baseUrl,
      model: config.model,
      apiKey: '',
    });
    setModalOpen(true);
  };

  interface RowItem {
    key: string;
    label: string;
    providerId: string;
    config: AiProviderConfig | null;
    onConfigure: () => void;
  }

  const rows: RowItem[] = (() => {
    const configMap = new Map(providers.map((p) => [p.providerId, p]));
    const presetItems: RowItem[] = PROVIDER_PRESETS.map((preset) => ({
      key: preset.id,
      label: preset.label,
      providerId: preset.id,
      config: configMap.get(preset.id) ?? null,
      onConfigure: () => openCreate(preset),
    }));
    const customItems: RowItem[] = providers
      .filter((p) => !PROVIDER_PRESETS.some((pr) => pr.id === p.providerId))
      .map((config) => ({
        key: config.providerId,
        label: config.displayName,
        providerId: config.providerId,
        config,
        onConfigure: () => openEdit(config),
      }));
    return [...presetItems, ...customItems];
  })();

  const handleTypeChange = (type: string) => {
    if (type === CUSTOM_TYPE) {
      setForm((prev) => ({
        ...prev,
        providerType: CUSTOM_TYPE,
        providerId: '',
        displayName: '',
        baseUrl: '',
        model: '',
        apiKey: prev.apiKey,
      }));
      return;
    }
    const preset = PROVIDER_PRESETS.find((p) => p.id === type);
    if (!preset) return;
    setForm((prev) => ({
      ...prev,
      providerType: preset.id,
      providerId: preset.id,
      displayName: preset.label,
      baseUrl: preset.defaultBaseUrl,
      model: preset.defaultModel,
      apiKey: prev.apiKey,
    }));
  };

  const handleSave = async () => {
    if (!PROVIDER_ID_PATTERN.test(form.providerId.trim())) {
      message.error('提供商 ID 只能包含英文、数字、- 和 _');
      return;
    }
    if (!form.displayName.trim()) {
      message.error('请输入显示名称');
      return;
    }
    if (!form.baseUrl.trim().startsWith('http')) {
      message.error('Base URL 必须以 http:// 或 https:// 开头');
      return;
    }
    if (!form.model.trim()) {
      message.error('请输入模型名称');
      return;
    }
    if (!editing && !form.apiKey) {
      message.error('请输入 API Key');
      return;
    }

    const payload = {
      providerId: form.providerId.trim(),
      displayName: form.displayName.trim(),
      protocol: 'OPENAI_COMPATIBLE' as const,
      baseUrl: form.baseUrl.trim(),
      model: form.model.trim(),
      apiKey: form.apiKey || undefined,
    };

    setSaving(true);
    try {
      if (editing) {
        await aiProviderService.update(editing.providerId, payload);
      } else {
        await aiProviderService.create(payload);
      }
      // 保存成功后立即清空 Key，不保留在任何前端状态中
      setForm((prev) => ({ ...prev, apiKey: '' }));
      setModalOpen(false);
      message.success('提供商配置已保存');
      void refresh();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleTest = async (providerId: string) => {
    setTestingIds((prev) => ({ ...prev, [providerId]: true }));
    try {
      const result = await aiProviderService.test(providerId);
      setTestResults((prev) => ({ ...prev, [providerId]: result }));
      if (result.success) {
        message.success(`${providerId} 连接成功（${result.latencyMs} ms）`);
      } else {
        message.error(result.message);
      }
    } catch (err) {
      const messageText =
        err instanceof Error ? err.message : '连接测试失败';
      setTestResults((prev) => ({
        ...prev,
        [providerId]: { success: false, message: messageText, latencyMs: 0 },
      }));
      message.error(messageText);
    } finally {
      setTestingIds((prev) => ({ ...prev, [providerId]: false }));
    }
  };

  const handleDelete = async (providerId: string) => {
    try {
      await aiProviderService.remove(providerId);
      setTestResults((prev) => {
        const next = { ...prev };
        delete next[providerId];
        return next;
      });
      message.success('提供商配置已删除');
      void refresh();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '删除失败');
    }
  };

  const renderTestStatus = (providerId: string) => {
    const result = testResults[providerId];
    if (!result) return null;
    if (result.success) {
      return (
        <Tag color="green">测试成功 · {result.latencyMs} ms</Tag>
      );
    }
    return <Tag color="red">测试失败</Tag>;
  };

  const renderRowActions = (
    providerId: string,
    config: AiProviderConfig
  ): React.ReactNode[] => {
    return [
      <Button
        key="edit"
        size="small"
        icon={<EditOutlined />}
        onClick={() => openEdit(config)}
      >
        编辑
      </Button>,
      <Button
        key="test"
        size="small"
        icon={<ThunderboltOutlined />}
        loading={testingIds[providerId]}
        onClick={() => void handleTest(providerId)}
      >
        测试连接
      </Button>,
      <Popconfirm
        key="delete"
        title={`确认删除「${config.displayName}」配置？`}
        description="删除后需要重新配置 API Key。"
        okText="删除"
        cancelText="取消"
        okButtonProps={{ danger: true }}
        onConfirm={() => void handleDelete(providerId)}
      >
        <Button size="small" danger icon={<DeleteOutlined />}>
          删除
        </Button>
      </Popconfirm>,
    ];
  };

  const renderRow = (row: RowItem) => {
    const { label, providerId, config, onConfigure } = row;
    return (
    <List.Item
      key={providerId}
      actions={
        config
          ? renderRowActions(providerId, config)
          : [
              <Button
                key="configure"
                size="small"
                type="primary"
                icon={<PlusOutlined />}
                onClick={onConfigure}
              >
                配置
              </Button>,
            ]
      }
    >
      <List.Item.Meta
        title={
          <Space wrap>
            <Typography.Text strong>{label}</Typography.Text>
            {config ? (
              <Tag color="green">已配置</Tag>
            ) : (
              <Tag>未配置</Tag>
            )}
            {config && renderTestStatus(providerId)}
          </Space>
        }
        description={
          config ? (
            <Space direction="vertical" size={0}>
              <Typography.Text type="secondary">
                {config.baseUrl} · 模型 {config.model}
              </Typography.Text>
              <Typography.Text type="secondary">
                API Key：{config.maskedApiKey}
              </Typography.Text>
              {testResults[providerId] && !testResults[providerId].success && (
                <Typography.Text type="danger">
                  {testResults[providerId].message}
                </Typography.Text>
              )}
            </Space>
          ) : (
            <Typography.Text type="secondary">尚未配置 API Key</Typography.Text>
          )
        }
      />
    </List.Item>
    );
  };

  return (
    <>
      <Card
        title="AI 模型提供商"
        style={{ maxWidth: 600, marginBottom: 16 }}
        extra={
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={openCreateCustom}
          >
            新增自定义配置
          </Button>
        }
      >
        <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
          API Key 使用 Windows DPAPI 加密后保存在本地数据库，任何接口都不会
          返回完整 Key，仅显示掩码。
        </Typography.Paragraph>

        <List
          size="small"
          loading={loading}
          dataSource={rows}
          renderItem={renderRow}
        />
      </Card>

      <Modal
        title={editing ? `编辑「${editing.displayName}」` : '新增 AI 模型提供商'}
        open={modalOpen}
        onOk={() => void handleSave()}
        onCancel={() => setModalOpen(false)}
        okText="保存配置"
        cancelText="取消"
        confirmLoading={saving}
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <div>
            <Typography.Text type="secondary">提供商类型</Typography.Text>
            <Select
              style={{ width: '100%', marginTop: 4 }}
              value={form.providerType}
              onChange={handleTypeChange}
              disabled={editing !== null}
              options={[
                ...PROVIDER_PRESETS.map((p) => ({
                  label: p.label,
                  value: p.id,
                })),
                { label: '自定义', value: CUSTOM_TYPE },
              ]}
            />
          </div>
          <div>
            <Typography.Text type="secondary">提供商 ID</Typography.Text>
            <Input
              style={{ marginTop: 4 }}
              value={form.providerId}
              onChange={(e) =>
                setForm((prev) => ({ ...prev, providerId: e.target.value }))
              }
              disabled={
                editing !== null || form.providerType !== CUSTOM_TYPE
              }
              placeholder="英文、数字、-、_"
            />
          </div>
          <div>
            <Typography.Text type="secondary">显示名称</Typography.Text>
            <Input
              style={{ marginTop: 4 }}
              value={form.displayName}
              onChange={(e) =>
                setForm((prev) => ({
                  ...prev,
                  displayName: e.target.value,
                }))
              }
              placeholder="例如：OpenAI"
            />
          </div>
          <div>
            <Typography.Text type="secondary">Base URL</Typography.Text>
            <Input
              style={{ marginTop: 4 }}
              value={form.baseUrl}
              onChange={(e) =>
                setForm((prev) => ({ ...prev, baseUrl: e.target.value }))
              }
              placeholder="https://api.openai.com/v1"
            />
          </div>
          <div>
            <Typography.Text type="secondary">模型名称</Typography.Text>
            <Input
              style={{ marginTop: 4 }}
              value={form.model}
              onChange={(e) =>
                setForm((prev) => ({ ...prev, model: e.target.value }))
              }
              placeholder="例如：gpt-4o-mini"
            />
          </div>
          <div>
            <Typography.Text type="secondary">API Key</Typography.Text>
            <Input.Password
              style={{ marginTop: 4 }}
              value={form.apiKey}
              onChange={(e) =>
                setForm((prev) => ({ ...prev, apiKey: e.target.value }))
              }
              placeholder={
                editing
                  ? `${editing.maskedApiKey}（留空则不修改）`
                  : 'sk-...'
              }
              autoComplete="new-password"
              name="ai-provider-api-key"
            />
            {editing && (
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                已保存的 Key 不会回填，输入新 Key 才会覆盖。
              </Typography.Text>
            )}
          </div>
        </Space>
      </Modal>
    </>
  );
};

export default AiProviderSettings;
