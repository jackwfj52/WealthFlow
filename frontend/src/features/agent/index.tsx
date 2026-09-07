import React, { useState } from 'react';
import {
  Alert,
  Avatar,
  Badge,
  Button,
  Card,
  Col,
  DatePicker,
  Divider,
  Form,
  Input,
  InputNumber,
  List,
  Row,
  Space,
  Typography,
  message,
  theme,
} from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  FileAddOutlined,
  RobotOutlined,
  SendOutlined,
  UserOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import PageHeader from '../../components/PageHeader';
import AmountText from '../../components/AmountText';
import { useCategories, useSnapshots } from '../../app/storage';
import { apiAgentActions } from '../../services/apiAgentActions';
import type {
  CreateSnapshotDraftResult,
  PendingActionExecutionResult,
} from '../../services/apiAgentActions';

const { Text, Paragraph } = Typography;

type MessageRole = 'assistant' | 'user';

interface ChatMessage {
  id: number;
  role: MessageRole;
  content: string;
}

interface DraftEntryFormValues {
  snapshotDate: dayjs.Dayjs;
  amounts?: Record<string, string | number | undefined>;
}

const SUGGESTIONS = [
  '我这个月为什么资产下降？',
  '我的资产主要集中在哪里？',
  '帮我创建今天的资产快照',
  '近半年变化最大的分类是什么？',
];

const INITIAL_MESSAGES: ChatMessage[] = [
  {
    id: 1,
    role: 'assistant',
    content:
      '你好，我是 WealthFlow AI 助手。当前聊天尚未接入大模型，回复为演示内容；右侧"待确认操作"已接入真实的快照草案与确认流程。',
  },
];

const Agent: React.FC = () => {
  const { token } = theme.useToken();
  const { categories } = useCategories();
  const { refresh: refreshSnapshots } = useSnapshots();

  const [input, setInput] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>(INITIAL_MESSAGES);
  const [form] = Form.useForm<DraftEntryFormValues>();

  const [draft, setDraft] = useState<CreateSnapshotDraftResult | null>(null);
  const [execution, setExecution] = useState<PendingActionExecutionResult | null>(null);
  const [creatingDraft, setCreatingDraft] = useState(false);
  const [confirming, setConfirming] = useState(false);

  const executed = execution?.status === 'EXECUTED';

  const sendMessage = (text?: string) => {
    const content = (text ?? input).trim();
    if (!content) return;

    setMessages((previous) => [
      ...previous,
      { id: Date.now(), role: 'user', content },
      {
        id: Date.now() + 1,
        role: 'assistant',
        content: content.includes('快照')
          ? '聊天尚未接入模型，无法自动生成草案。请在右侧手动填写快照日期与分类金额，然后点击"生成待确认草案"。'
          : '聊天尚未接入模型，当前回复仅为演示内容。',
      },
    ]);
    setInput('');
  };

  const generateDraft = async () => {
    try {
      const values = await form.validateFields();
      const snapshotDate = values.snapshotDate.format('YYYY-MM-DD');

      const items = Object.entries(values.amounts ?? {})
        .filter(
          ([, amount]) =>
            amount !== undefined && amount !== null && amount !== ''
        )
        .map(([categoryId, amount]) => ({
          categoryId,
          amount: Number(amount).toFixed(2),
        }))
        .filter((item) => Number(item.amount) > 0);

      if (items.length === 0) {
        message.error('至少需要填写一个分类的金额');
        return;
      }

      setCreatingDraft(true);
      setExecution(null);

      const created = await apiAgentActions.createSnapshotDraft({
        snapshotDate,
        items,
      });

      setDraft(created);
      message.success('草案已生成，请核对后确认');
    } catch (err) {
      if (err instanceof Error) {
        message.error(err.message);
      }
    } finally {
      setCreatingDraft(false);
    }
  };

  const confirmDraft = async () => {
    if (!draft || confirming) return;

    setConfirming(true);
    try {
      const result = await apiAgentActions.confirmAction(draft.actionId);
      setExecution(result);

      if (result.status === 'EXECUTED') {
        refreshSnapshots();
        message.success(result.displaySummary || '快照已创建');
      } else {
        message.warning(result.displaySummary || `操作未完成（${result.status}）`);
      }
    } catch (err) {
      if (err instanceof Error) {
        message.error(err.message);
      }
    } finally {
      setConfirming(false);
    }
  };

  const resetDraft = () => {
    setDraft(null);
    setExecution(null);
    form.resetFields();
  };

  const executedSnapshot = execution?.snapshot;

  return (
    <>
      <PageHeader
        title="AI 助手"
        subtitle="理解资产数据，并在你确认后协助完成操作"
      />

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={15}>
          <Card
            title={<Space><RobotOutlined /> 与 WealthFlow 对话</Space>}
            styles={{ body: { padding: 0 } }}
          >
            <Alert
              showIcon
              type="info"
              icon={<RobotOutlined />}
              message="聊天尚未接入大模型"
              description="当前聊天回复为演示内容。右侧的草案生成与确认已连接真实接口。"
              style={{ margin: 16, marginBottom: 0 }}
            />

            <div
              style={{
                height: 460,
                overflowY: 'auto',
                padding: 20,
                background: token.colorBgLayout,
              }}
            >
              <List
                split={false}
                dataSource={messages}
                renderItem={(item) => {
                  const isUser = item.role === 'user';
                  return (
                    <List.Item style={{ justifyContent: isUser ? 'flex-end' : 'flex-start', padding: '6px 0' }}>
                      <Space align="start" direction={isUser ? 'horizontal' : 'horizontal'}>
                        {!isUser && (
                          <Avatar icon={<RobotOutlined />} style={{ background: token.colorPrimary }} />
                        )}
                        <div
                          style={{
                            maxWidth: 460,
                            padding: '10px 14px',
                            borderRadius: 12,
                            whiteSpace: 'pre-wrap',
                            background: isUser ? token.colorPrimary : token.colorBgContainer,
                            color: isUser ? token.colorTextLightSolid : token.colorText,
                            boxShadow: `0 1px 2px ${token.colorBorderSecondary}`,
                          }}
                        >
                          {item.content}
                        </div>
                        {isUser && <Avatar icon={<UserOutlined />} />}
                      </Space>
                    </List.Item>
                  );
                }}
              />
            </div>

            <Divider style={{ margin: 0 }} />
            <div style={{ padding: 16 }}>
              <Space wrap size={[8, 8]} style={{ marginBottom: 12 }}>
                {SUGGESTIONS.map((suggestion) => (
                  <Button key={suggestion} size="small" onClick={() => sendMessage(suggestion)}>
                    {suggestion}
                  </Button>
                ))}
              </Space>
              <Input.Search
                value={input}
                placeholder="例如：帮我创建今天的资产快照"
                enterButton={<Button type="primary" icon={<SendOutlined />}>发送</Button>}
                onChange={(event) => setInput(event.target.value)}
                onSearch={() => sendMessage()}
              />
            </div>
          </Card>
        </Col>

        <Col xs={24} xl={9}>
          <Card
            title="待确认操作"
            extra={
              executed ? (
                <Badge status="success" text="已执行" />
              ) : draft ? (
                <Badge status="warning" text="等待确认" />
              ) : (
                <Badge status="default" text="未生成" />
              )
            }
          >
            {executed && execution ? (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Space align="start">
                  <CheckCircleOutlined style={{ color: token.colorSuccess }} />
                  <div>
                    <Text strong>创建资产快照</Text>
                    <Paragraph type="secondary" style={{ margin: '4px 0 0' }}>
                      {execution.displaySummary}
                    </Paragraph>
                  </div>
                </Space>

                {executedSnapshot && (
                  <Card size="small" style={{ background: token.colorFillAlter }}>
                    <Space direction="vertical" size={8} style={{ width: '100%' }}>
                      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
                        <Text type="secondary">快照日期</Text>
                        <Text strong>{executedSnapshot.snapshotDate}</Text>
                      </Space>
                      <Divider style={{ margin: 0 }} />
                      {executedSnapshot.items.map((item) => (
                        <Space
                          key={item.categoryId}
                          style={{ justifyContent: 'space-between', width: '100%' }}
                        >
                          <Text>{item.categoryName}</Text>
                          <AmountText amount={item.amount} />
                        </Space>
                      ))}
                      <Divider style={{ margin: 0 }} />
                      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
                        <Text strong>合计</Text>
                        <AmountText amount={executedSnapshot.totalAmount} style={{ fontSize: 16 }} />
                      </Space>
                    </Space>
                  </Card>
                )}

                <Button block onClick={resetDraft}>
                  重新生成草案
                </Button>
              </Space>
            ) : draft ? (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Space align="start">
                  <WarningOutlined style={{ color: token.colorWarning }} />
                  <div>
                    <Text strong>创建资产快照</Text>
                    <Paragraph type="secondary" style={{ margin: '4px 0 0' }}>
                      {draft.displaySummary}
                    </Paragraph>
                  </div>
                </Space>

                <Card size="small" style={{ background: token.colorFillAlter }}>
                  <Space direction="vertical" size={8} style={{ width: '100%' }}>
                    <Space style={{ justifyContent: 'space-between', width: '100%' }}>
                      <Text type="secondary">快照日期</Text>
                      <Text strong>{draft.snapshotDate}</Text>
                    </Space>
                    <Divider style={{ margin: 0 }} />
                    {draft.items.map((item) => (
                      <Space
                        key={item.categoryId}
                        style={{ justifyContent: 'space-between', width: '100%' }}
                      >
                        <Text>{item.categoryName}</Text>
                        <AmountText amount={item.amount} />
                      </Space>
                    ))}
                    <Divider style={{ margin: 0 }} />
                    <Space style={{ justifyContent: 'space-between', width: '100%' }}>
                      <Text strong>合计</Text>
                      <AmountText amount={draft.totalAmount} style={{ fontSize: 16 }} />
                    </Space>
                  </Space>
                </Card>

                <Space style={{ width: '100%' }}>
                  <Button
                    type="primary"
                    block
                    icon={<CheckCircleOutlined />}
                    loading={confirming}
                    disabled={confirming}
                    onClick={confirmDraft}
                  >
                    确认创建
                  </Button>
                  <Button block icon={<CloseCircleOutlined />} disabled>
                    取消
                  </Button>
                </Space>

                <Text type="secondary" style={{ fontSize: 12 }}>
                  取消接口尚未提供，暂不支持取消草案；未确认的草案将在有效期后自动失效。
                </Text>

                <Text type="secondary" style={{ fontSize: 12 }}>
                  操作编号：{draft.actionId} · 有效期至 {draft.expiresAt.replace('T', ' ')}
                </Text>
              </Space>
            ) : (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Text type="secondary">
                  暂无待确认草案。填写快照日期与各分类金额后生成草案，确认前不会写入任何资产数据。
                </Text>

                <Form form={form} layout="vertical">
                  <Form.Item
                    name="snapshotDate"
                    label="快照日期"
                    rules={[{ required: true, message: '请选择快照日期' }]}
                  >
                    <DatePicker
                      style={{ width: '100%' }}
                      disabledDate={(date) => date.isAfter(dayjs(), 'day')}
                    />
                  </Form.Item>

                  {categories.length === 0 ? (
                    <Alert
                      showIcon
                      type="info"
                      message="暂无分类"
                      description="请先在「分类管理」页面创建分类，再回来生成草案。"
                    />
                  ) : (
                    <>
                      <Divider style={{ margin: '4px 0 8px' }} />
                      <Text type="secondary">各分类金额（留空或 0 不计入）</Text>
                      {categories.map((category) => (
                        <Form.Item
                          key={category.id}
                          name={['amounts', category.id]}
                          label={category.name}
                          style={{ marginBottom: 8 }}
                        >
                          <InputNumber
                            style={{ width: '100%' }}
                            min={0}
                            precision={2}
                            stringMode
                            placeholder="金额（元）"
                          />
                        </Form.Item>
                      ))}
                    </>
                  )}

                  <Button
                    type="primary"
                    block
                    icon={<FileAddOutlined />}
                    loading={creatingDraft}
                    onClick={generateDraft}
                  >
                    生成待确认草案
                  </Button>
                </Form>
              </Space>
            )}
          </Card>

          <Card title="Agent 工作方式" size="small" style={{ marginTop: 16 }}>
            <Space direction="vertical" size={6}>
              <Text>1. 理解你的问题或操作意图</Text>
              <Text>2. 查询本地数据或创建操作草案</Text>
              <Text>3. 展示数据依据与影响范围</Text>
              <Text>4. 仅在你确认后执行写入</Text>
            </Space>
          </Card>
        </Col>
      </Row>
    </>
  );
};

export default Agent;
