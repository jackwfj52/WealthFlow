import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Input,
  Progress,
  Segmented,
  Select,
  Space,
  Switch,
  Tag,
  Typography,
  message,
} from 'antd';
import { ReloadOutlined, SaveOutlined, ThunderboltOutlined } from '@ant-design/icons';
import {
  aiProviderService,
  type AiProviderConfig,
} from '../../services/apiAgentProviders';
import {
  embeddingService,
  type EmbeddingConfig,
  type EmbeddingConfigPayload,
  type EmbeddingMode,
  type EmbeddingTestResult,
  type KnowledgeIndexStatus,
} from '../../services/apiEmbedding';

interface EmbeddingForm {
  mode: EmbeddingMode;
  providerId: string;
  baseUrl: string;
  model: string;
  apiKey: string;
  enabled: boolean;
}

const EMPTY_FORM: EmbeddingForm = {
  mode: 'REUSE',
  providerId: '',
  baseUrl: '',
  model: '',
  apiKey: '',
  enabled: false,
};

function configToForm(config: EmbeddingConfig): EmbeddingForm {
  return {
    mode: config.mode,
    providerId: config.providerId ?? '',
    baseUrl: config.mode === 'CUSTOM' ? config.baseUrl ?? '' : '',
    model: config.model ?? '',
    apiKey: '',
    enabled: config.enabled,
  };
}

function errorText(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback;
}

const EmbeddingSettings: React.FC = () => {
  const [config, setConfig] = useState<EmbeddingConfig | null>(null);
  const [form, setForm] = useState<EmbeddingForm>(EMPTY_FORM);
  const [providers, setProviders] = useState<AiProviderConfig[]>([]);
  const [status, setStatus] = useState<KnowledgeIndexStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [reindexing, setReindexing] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [statusError, setStatusError] = useState<string | null>(null);
  const [testResult, setTestResult] = useState<EmbeddingTestResult | null>(null);

  const refreshStatus = useCallback(async () => {
    try {
      setStatus(await embeddingService.getIndexStatus());
      setStatusError(null);
    } catch (error) {
      setStatusError(errorText(error, '索引状态加载失败'));
    }
  }, []);

  const refreshProviders = useCallback(async () => {
    try {
      setProviders(await aiProviderService.getAll());
    } catch (error) {
      message.error(errorText(error, '提供商配置加载失败'));
    }
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    const results = await Promise.allSettled([
      embeddingService.getConfig(),
      aiProviderService.getAll(),
      embeddingService.getIndexStatus(),
    ]);
    const [configResult, providersResult, statusResult] = results;
    if (configResult.status === 'fulfilled') {
      setConfig(configResult.value);
      setForm(configToForm(configResult.value));
      setLoadError(null);
    } else {
      setLoadError(errorText(configResult.reason, 'Embedding 配置加载失败'));
    }
    if (providersResult.status === 'fulfilled') {
      setProviders(providersResult.value);
    } else {
      message.error(errorText(providersResult.reason, '提供商配置加载失败'));
    }
    if (statusResult.status === 'fulfilled') {
      setStatus(statusResult.value);
      setStatusError(null);
    } else {
      setStatusError(errorText(statusResult.reason, '索引状态加载失败'));
    }
    setLoading(false);
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!status?.processing && !(status?.enabled && (status.pending > 0 || status.failed > 0))) return;
    const timer = window.setInterval(() => void refreshStatus(), 4000);
    return () => window.clearInterval(timer);
  }, [status?.processing, status?.enabled, status?.pending, status?.failed, refreshStatus]);

  const savedForm = config ? configToForm(config) : EMPTY_FORM;
  const dirty = form.mode !== savedForm.mode
    || form.model.trim() !== savedForm.model
    || form.enabled !== savedForm.enabled
    || (form.mode === 'REUSE' && form.providerId !== savedForm.providerId)
    || (form.mode === 'CUSTOM' && (
      form.baseUrl.trim() !== savedForm.baseUrl || form.apiKey !== ''
    ));
  const busy = loading || saving || testing || reindexing;
  const selectedProvider = providers.find((provider) => provider.providerId === form.providerId);
  const selectedProviderMissing = form.mode === 'REUSE'
    && Boolean(form.providerId) && !selectedProvider;
  const modelChanged = config !== null && (
    form.mode !== config.mode
    || form.model.trim() !== config.model
    || (form.mode === 'REUSE' && form.providerId !== config.providerId)
    || (form.mode === 'CUSTOM' && form.baseUrl.trim() !== (config.baseUrl ?? ''))
  );

  const handleSave = async () => {
    if (!form.model.trim()) {
      message.error('请输入 Embedding 模型名称');
      return;
    }
    if (form.mode === 'REUSE' && !selectedProvider?.configured) {
      message.error('请选择已配置 API Key 的提供商');
      return;
    }
    if (form.mode === 'CUSTOM') {
      try {
        const url = new URL(form.baseUrl.trim());
        if (!['https:', 'http:'].includes(url.protocol) || url.username || url.password) {
          throw new Error('invalid-url');
        }
      } catch {
        message.error('请输入有效的 http:// 或 https:// Base URL');
        return;
      }
      if (!form.apiKey.trim() && !(config?.mode === 'CUSTOM' && config.maskedApiKey)) {
        message.error('请输入 API Key');
        return;
      }
    }

    const payload: EmbeddingConfigPayload = {
      mode: form.mode,
      model: form.model.trim(),
      enabled: form.enabled,
      ...(form.mode === 'REUSE'
        ? { providerId: form.providerId }
        : {
          baseUrl: form.baseUrl.trim(),
          ...(form.apiKey.trim() ? { apiKey: form.apiKey.trim() } : {}),
        }),
    };
    setSaving(true);
    try {
      const saved = await embeddingService.saveConfig(payload);
      setConfig(saved);
      setForm(configToForm(saved));
      setTestResult(null);
      message.success(modelChanged
        ? saved.enabled
          ? '配置已保存，将自动按新模型重建索引'
          : '配置已保存，启用联网后将按新模型重建索引'
        : 'Embedding 配置已保存');
      await refreshStatus();
    } catch (error) {
      message.error(errorText(error, 'Embedding 配置保存失败'));
    } finally {
      setSaving(false);
    }
  };

  const handleTest = async () => {
    setTesting(true);
    setTestResult(null);
    try {
      const result = await embeddingService.test();
      setTestResult(result);
      if (result.success) {
        message.success(`Embedding 测试成功（${result.latencyMs} ms）`);
      } else {
        message.error(result.message);
      }
    } catch (error) {
      const result = {
        success: false,
        message: errorText(error, 'Embedding 测试失败'),
        latencyMs: 0,
        dimensions: 0,
      };
      setTestResult(result);
      message.error(result.message);
    } finally {
      setTesting(false);
    }
  };

  const handleDisable = async () => {
    if (!config) return;
    setSaving(true);
    try {
      const saved = await embeddingService.saveConfig({
        mode: config.mode,
        ...(config.providerId ? { providerId: config.providerId } : {}),
        ...(config.mode === 'CUSTOM' && config.baseUrl ? { baseUrl: config.baseUrl } : {}),
        model: config.model,
        enabled: false,
      });
      setConfig(saved);
      setForm(configToForm(saved));
      setTestResult(null);
      message.success('知识库联网生成已停用');
      await refreshStatus();
    } catch (error) {
      message.error(errorText(error, '停用联网失败'));
    } finally {
      setSaving(false);
    }
  };

  const handleReindex = async () => {
    setReindexing(true);
    try {
      setStatus(await embeddingService.reindex());
      setStatusError(null);
      message.success('重建索引已开始');
    } catch (error) {
      message.error(errorText(error, '重建索引失败'));
    } finally {
      setReindexing(false);
    }
  };

  const handleRefresh = async () => {
    if (!config) {
      await load();
      return;
    }
    setRefreshing(true);
    await Promise.all([refreshStatus(), refreshProviders()]);
    setRefreshing(false);
  };

  return (
    <Card
      title="知识库 Embedding"
      style={{ maxWidth: 600, marginBottom: 16 }}
      loading={loading}
      extra={(
        <Button
          type="link"
          size="small"
          icon={<ReloadOutlined />}
          loading={refreshing}
          disabled={busy}
          onClick={() => void handleRefresh()}
        >
          刷新状态
        </Button>
      )}
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
          Embedding 将投资策略和笔记转换为向量，帮助助手找到意思相关的资料。
          向量检索在本地运行，无需另外配置检索 API。知识库固定使用下面选定的配置，
          切换助手的聊天提供商不会改变它。
        </Typography.Paragraph>

        {loadError && <Alert type="error" showIcon message={loadError} />}

        <div>
          <Typography.Text>API 配置方式</Typography.Text>
          <Segmented
            block
            style={{ marginTop: 6 }}
            options={[
              { label: '复用已有提供商', value: 'REUSE' },
              { label: '单独配置', value: 'CUSTOM' },
            ]}
            value={form.mode}
            disabled={busy || !config}
            onChange={(value) => {
              setForm((previous) => ({ ...previous, mode: value as EmbeddingMode, apiKey: '' }));
              setTestResult(null);
            }}
          />
        </div>

        {form.mode === 'REUSE' ? (
          <div>
            <Typography.Text>复用提供商的地址与 API Key</Typography.Text>
            <Select
              style={{ width: '100%', marginTop: 6 }}
              placeholder="选择上方已配置的 AI 提供商"
              value={form.providerId || undefined}
              disabled={busy || !config}
              options={providers.map((provider) => ({
                value: provider.providerId,
                label: `${provider.displayName}（${provider.providerId}）`,
                disabled: !provider.configured,
              }))}
              notFoundContent="请先在上方添加提供商，再刷新状态"
              onOpenChange={(open) => { if (open) void refreshProviders(); }}
              onChange={(providerId) => {
                setForm((previous) => ({ ...previous, providerId }));
                setTestResult(null);
              }}
            />
            {selectedProvider && (
              <Typography.Paragraph type="secondary" style={{ marginTop: 6, marginBottom: 0, wordBreak: 'break-all' }}>
                {selectedProvider.baseUrl} · API Key：{selectedProvider.maskedApiKey}
              </Typography.Paragraph>
            )}
            {selectedProviderMissing && (
              <Alert type="warning" showIcon style={{ marginTop: 8 }} message="绑定的提供商已不存在，请重新选择" />
            )}
          </div>
        ) : (
          <>
            <div>
              <Typography.Text>Base URL</Typography.Text>
              <Input
                style={{ marginTop: 6 }}
                value={form.baseUrl}
                disabled={busy || !config}
                placeholder="https://api.example.com/v1"
                onChange={(event) => {
                  setForm((previous) => ({ ...previous, baseUrl: event.target.value }));
                  setTestResult(null);
                }}
              />
            </div>
            <div>
              <Typography.Text>API Key</Typography.Text>
              <Input.Password
                style={{ marginTop: 6 }}
                value={form.apiKey}
                disabled={busy || !config}
                autoComplete="new-password"
                name="knowledge-embedding-api-key"
                placeholder={config?.mode === 'CUSTOM' && config.maskedApiKey
                  ? `${config.maskedApiKey}（留空则不修改）`
                  : '填写 Embedding 服务商的 API Key'}
                onChange={(event) => {
                  setForm((previous) => ({ ...previous, apiKey: event.target.value }));
                  setTestResult(null);
                }}
              />
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                Key 加密保存在本地，已保存的 Key 不会回填。
              </Typography.Text>
            </div>
          </>
        )}

        <div>
          <Typography.Text>Embedding 模型名称</Typography.Text>
          <Input
            style={{ marginTop: 6 }}
            value={form.model}
            disabled={busy || !config}
            placeholder="填写服务商支持的 Embedding 模型名"
            onChange={(event) => {
              setForm((previous) => ({ ...previous, model: event.target.value }));
              setTestResult(null);
            }}
          />
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            此模型独立于聊天模型；提供商需要支持 OpenAI 兼容的 /embeddings 接口。
          </Typography.Text>
        </div>

        <div>
          <Space style={{ width: '100%', justifyContent: 'space-between' }}>
            <Typography.Text>允许联网生成知识库向量</Typography.Text>
            <Switch
              checked={form.enabled}
              disabled={busy || !config}
              onChange={(enabled) => setForm((previous) => ({ ...previous, enabled }))}
            />
          </Space>
          <Typography.Paragraph type="secondary" style={{ marginTop: 6, marginBottom: 0 }}>
            保存并启用后，即授权 WF 将知识库中用于检索的策略、笔记段落和检索问题发送给所选服务商。
            关闭后暂停生成新向量，关键词检索仍可使用。
          </Typography.Paragraph>
        </div>

        {modelChanged && (
          <Alert type="info" showIcon message="更换模型、提供商或地址后，保存并启用联网将自动按新配置重建索引。" />
        )}

        <Space wrap>
          <Button
            type="primary"
            icon={<SaveOutlined />}
            loading={saving}
            disabled={busy || !config || !dirty}
            onClick={() => void handleSave()}
          >
            保存配置
          </Button>
          <Button
            icon={<ThunderboltOutlined />}
            loading={testing}
            disabled={busy || !config?.configured || dirty}
            onClick={() => void handleTest()}
          >
            测试 Embedding
          </Button>
          {config?.enabled && (
            <Button
              disabled={busy}
              onClick={() => void handleDisable()}
            >
              停用联网
            </Button>
          )}
          <Tag color={config?.configured ? 'green' : undefined}>
            {config?.configured ? '已配置' : '未配置'}
          </Tag>
          {config?.configured && <Tag color={config.enabled ? 'blue' : undefined}>{config.enabled ? '联网已启用' : '联网已关闭'}</Tag>}
        </Space>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          测试会按已保存的配置发送一段固定公开文本，验证接口返回真实向量。
          {dirty && ' 请先保存当前改动，再测试或重建索引。'}
          {config?.enabled && ' 停用联网将使用已保存的配置，并放弃当前未保存的改动。'}
        </Typography.Text>

        {testResult && (
          <Alert
            showIcon
            type={testResult.success ? 'success' : 'error'}
            message={testResult.success
              ? `测试成功 · ${testResult.latencyMs} ms · ${testResult.dimensions} 维`
              : testResult.message}
          />
        )}

        <div style={{ borderTop: '1px solid var(--ant-color-border-secondary, #d9d9d9)', paddingTop: 12 }}>
          <Space wrap style={{ width: '100%', justifyContent: 'space-between', marginBottom: 8 }}>
            <Typography.Text strong>知识库向量索引</Typography.Text>
            <Button
              icon={<ReloadOutlined />}
              loading={reindexing || status?.processing}
              disabled={busy || !config?.configured || !config.enabled || dirty || status?.processing}
              onClick={() => void handleReindex()}
            >
              重建索引
            </Button>
          </Space>
          {statusError && <Alert type="error" showIcon message={statusError} style={{ marginBottom: 8 }} />}
          {status && (
            <>
              <Descriptions column={2} size="small">
                <Descriptions.Item label="段落总数">{status.total}</Descriptions.Item>
                <Descriptions.Item label="已索引">{status.indexed}</Descriptions.Item>
                <Descriptions.Item label="待索引">{status.pending}</Descriptions.Item>
                <Descriptions.Item label="失败">{status.failed}</Descriptions.Item>
                <Descriptions.Item label="当前模型" span={2}>{status.model ?? '尚未配置'}</Descriptions.Item>
              </Descriptions>
              {status.total > 0 && (
                <Progress
                  percent={Math.min(100, Math.round(status.indexed / status.total * 100))}
                  status={status.processing ? 'active' : status.failed > 0 ? 'exception' : 'normal'}
                />
              )}
              <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                {status.processing ? '正在生成向量，索引状态将自动刷新。'
                  : status.total === 0 ? '导入投资策略或笔记后，即可建立向量索引。'
                    : !status.enabled ? '联网生成已关闭。启用并保存后可建立索引。'
                      : status.failed > 0 ? '部分段落索引失败，可点击重建索引重试。'
                        : status.pending > 0 ? '等待自动建立索引，状态将自动刷新。'
                        : '索引已完成。'}
              </Typography.Paragraph>
              {status.warning && <Alert type="warning" showIcon style={{ marginTop: 8 }} message={status.warning} />}
            </>
          )}
        </div>
      </Space>
    </Card>
  );
};

export default EmbeddingSettings;
