import React, { useMemo, useState } from 'react';
import {
  Alert,
  Avatar,
  Badge,
  Button,
  Card,
  Col,
  Divider,
  Input,
  List,
  Row,
  Space,
  Tag,
  Typography,
  message,
  theme,
} from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  DatabaseOutlined,
  RobotOutlined,
  SendOutlined,
  UserOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import PageHeader from '../../components/PageHeader';

const { Text, Paragraph } = Typography;

type MessageRole = 'assistant' | 'user';
type ActionStatus = 'PENDING' | 'EXECUTED' | 'CANCELLED';

interface ChatMessage {
  id: number;
  role: MessageRole;
  content: string;
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
      '你好，我是 WealthFlow AI 助手。我可以帮你解读资产趋势、查看分类变化，并在你确认后执行创建快照等操作。',
  },
];

const Agent: React.FC = () => {
  const { token } = theme.useToken();
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>(INITIAL_MESSAGES);
  const [actionStatus, setActionStatus] = useState<ActionStatus>('PENDING');

  const actionConfig = useMemo(() => {
    if (actionStatus === 'EXECUTED') {
      return {
        badge: '已执行',
        badgeStatus: 'success' as const,
        icon: <CheckCircleOutlined style={{ color: token.colorSuccess }} />,
        description: '界面演示：已模拟创建结果。接入确认接口后才会写入真实快照。',
      };
    }
    if (actionStatus === 'CANCELLED') {
      return {
        badge: '已取消',
        badgeStatus: 'default' as const,
        icon: <CloseCircleOutlined style={{ color: token.colorTextSecondary }} />,
        description: '该草案已取消，不会修改任何资产数据。',
      };
    }
    return {
      badge: '等待确认',
      badgeStatus: 'warning' as const,
      icon: <WarningOutlined style={{ color: token.colorWarning }} />,
      description: '请核对日期、分类和金额；确认前不会写入任何资产数据。',
    };
  }, [actionStatus, token]);

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
          ? '我已为你准备了一份快照创建草案，请在右侧核对后确认。'
          : '这是界面演示。接入 Agent 工具后，我会先查询本地真实数据，再基于结果回答你。',
      },
    ]);
    setInput('');
  };

  const confirmDraft = () => {
    setActionStatus('EXECUTED');
    message.success('演示操作已确认；尚未写入真实资产数据');
  };

  const cancelDraft = () => {
    setActionStatus('CANCELLED');
    message.info('草案已取消');
  };

  return (
    <>
      <PageHeader
        title="AI 助手"
        subtitle="理解资产数据，并在你确认后协助完成操作"
        extra={<Tag color="blue" icon={<RobotOutlined />}>界面演示</Tag>}
      />

      <Alert
        showIcon
        type="info"
        icon={<DatabaseOutlined />}
        message="当前为前端交互演示"
        description="聊天回答、草案确认和取消仅用于展示界面流程，暂未连接模型、Agent 工具或真实资产写入接口。"
        style={{ marginBottom: 16 }}
      />

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={15}>
          <Card
            title={<Space><RobotOutlined /> 与 WealthFlow 对话</Space>}
            styles={{ body: { padding: 0 } }}
          >
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
            extra={<Badge status={actionConfig.badgeStatus} text={actionConfig.badge} />}
          >
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <Space align="start">
                {actionConfig.icon}
                <div>
                  <Text strong>创建资产快照</Text>
                  <Paragraph type="secondary" style={{ margin: '4px 0 0' }}>
                    {actionConfig.description}
                  </Paragraph>
                </div>
              </Space>

              <Card size="small" style={{ background: token.colorFillAlter }}>
                <Space direction="vertical" size={8} style={{ width: '100%' }}>
                  <Space style={{ justifyContent: 'space-between', width: '100%' }}>
                    <Text type="secondary">快照日期</Text>
                    <Text strong>2026-09-05</Text>
                  </Space>
                  <Divider style={{ margin: 0 }} />
                  <Space style={{ justifyContent: 'space-between', width: '100%' }}>
                    <Text>现金</Text>
                    <Text>¥30,000.00</Text>
                  </Space>
                  <Space style={{ justifyContent: 'space-between', width: '100%' }}>
                    <Text>股票</Text>
                    <Text>¥180,000.00</Text>
                  </Space>
                  <Space style={{ justifyContent: 'space-between', width: '100%' }}>
                    <Text>基金</Text>
                    <Text>¥120,000.00</Text>
                  </Space>
                  <Divider style={{ margin: 0 }} />
                  <Space style={{ justifyContent: 'space-between', width: '100%' }}>
                    <Text strong>合计</Text>
                    <Text strong style={{ fontSize: 16 }}>¥330,000.00</Text>
                  </Space>
                </Space>
              </Card>

              {actionStatus === 'PENDING' ? (
                <Space style={{ width: '100%' }}>
                  <Button type="primary" block icon={<CheckCircleOutlined />} onClick={confirmDraft}>
                    确认创建
                  </Button>
                  <Button block icon={<CloseCircleOutlined />} onClick={cancelDraft}>
                    取消
                  </Button>
                </Space>
              ) : (
                <Button block onClick={() => setActionStatus('PENDING')}>
                  重置演示草案
                </Button>
              )}

              <Text type="secondary" style={{ fontSize: 12 }}>
                操作编号：demo-create-snapshot-001 · 有效期：10 分钟
              </Text>
            </Space>
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
