import React, { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Avatar,
  Badge,
  Button,
  Card,
  Col,
  Collapse,
  DatePicker,
  Divider,
  Form,
  Input,
  InputNumber,
  List,
  Modal,
  Row,
  Select,
  Space,
  Typography,
  Tag,
  message,
  theme,
} from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  FileAddOutlined,
  PlusOutlined,
  RobotOutlined,
  SendOutlined,
  UserOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { Link } from 'react-router-dom';
import PageHeader from '../../components/PageHeader';
import AmountText from '../../components/AmountText';
import { useCategories, useSnapshots } from '../../app/storage';
import { apiAgentActions } from '../../services/apiAgentActions';
import type {
  PendingActionCancellationResult,
  CreateSnapshotDraftResult,
  DeleteSnapshotDraftResult,
  BatchSnapshotDraftResult,
  PendingActionExecutionResult,
} from '../../services/apiAgentActions';
import { aiProviderService } from '../../services/apiAgentProviders';
import type { AiProviderConfig } from '../../services/apiAgentProviders';
import { apiAgentConversations } from '../../services/apiAgentConversations';
import type { AgentConversation, AgentConversationMessage } from '../../services/apiAgentConversations';
import { apiAgentPermission, type AiPermissionLevel } from '../../services/apiAgentPermission';
import BatchDraftDetails from './BatchDraftDetails';
import { ApiError } from '../../services/apiClient';
import type { KnowledgeSource } from '../../services/apiKnowledge';
import type { FinancialNewsArticle } from '../../services/apiFinancialNews';
import NewsSources from './NewsSources';
import WebSources from './WebSources';
import type { WebSearchSource } from '../../services/apiWebSearch';
import { streamAgentMessage, type AgentProgressEvent } from '../../services/apiAgentStream';
import type { CryptoMarketContext } from '../../services/apiCryptoMarket';
import AgentActivity from './AgentActivity';
import CryptoMarkets from './CryptoMarkets';
import PortfolioBasis from './PortfolioBasis';
import type { PortfolioContext } from '../../services/apiPortfolio';
import { hexToRgba } from '../../utils/color';
import './index.css';

const { Text, Paragraph } = Typography;

type MessageRole = 'assistant' | 'user';

interface ChatMessage {
  id: string | number;
  role: MessageRole;
  content: string;
  /** 模型返回前显示计算占位 */
  loading?: boolean;
  steps?: string[];
  sources?: KnowledgeSource[];
  newsSources?: FinancialNewsArticle[];
  webSources?: WebSearchSource[];
  cryptoMarkets?: CryptoMarketContext[];
  progress?: AgentProgressEvent[];
  startedAt?: number;
  lastContactAt?: number;
  portfolioContext?: PortfolioContext | null;
}

interface DraftEntryFormValues {
  snapshotDate: dayjs.Dayjs;
  amounts?: Record<string, string | number | undefined>;
}

/** 待确认草案：创建与删除互斥，由后端保证同一响应中只有一个非空 */
type PendingDraft =
  | { kind: 'create'; data: CreateSnapshotDraftResult }
  | { kind: 'batch'; data: BatchSnapshotDraftResult }
  | { kind: 'delete'; data: DeleteSnapshotDraftResult };

const SUGGESTIONS = [
  '根据我的生效投资策略，现在的资产配置有哪些需要关注的地方？',
  '查找我的纳指定投策略，并列出继续投入和暂停的条件。',
];

function actionStatusClaim(reply: string) {
  return /(?:成功|我已|我已经|已为你|已经为你).{0,10}(?:生成|创建|新增|修改|删除|保存(?!的)|执行)|(?:^|[。！\n])(?:已|已经).{0,8}(?:生成|创建|新增|修改|删除|保存(?!的)|执行)|(?:草案|快照).{0,8}(?:已|已经).{0,8}(?:生成|创建|新增|修改|删除)|(?:草案|快照).{0,8}(?:生成好了|准备好了|做好了|完成了)|(?:草案|快照|操作).{0,8}(?:等你|等待你).{0,8}(?:审核|确认)|(?:请你|请在).{0,8}(?:右侧|草案).{0,8}(?:审核|确认)/.test(reply);
}

function asksToChangeData(text: string) {
  if (/应该|是否|应不应该|要不要|该不该|建议|如何/.test(text)) return false;
  if (/知识库|策略|笔记|资料|复盘/.test(text) && !/快照|资产金额|分类金额/.test(text)) return false;
  return /新增|添加|创建|生成|补齐|补录|修改|调整|更改|改为|设为|删除|移除|清空/.test(text)
    && /快照|资产|金额|分类|数据|股票|现金/.test(text);
}

const INITIAL_MESSAGES: ChatMessage[] = [
  {
    id: 1,
    role: 'assistant',
    content:
      '你好，我可以参考知识库中的生效投资策略、复盘和研究资料，结合资产快照分析资产配置与投资决策，并标出资料来源。也可以查询近期财经讯息；启用联网搜索后还能补查公开网页并展示来源。你可以查询完整资产历史；数据修改按设置中的 AI 权限处理。',
  },
];

const ACTIVE_CONVERSATION_KEY = 'wealthflow-active-conversation';
const toChatMessage = (item: AgentConversationMessage): ChatMessage => ({
  id: item.id, role: item.role, content: item.content, steps: item.response?.steps,
  sources: item.response?.sources ?? [], newsSources: item.response?.newsSources ?? [],
  webSources: item.response?.webSources ?? [],
  cryptoMarkets: item.response?.cryptoMarkets ?? [],
  portfolioContext: item.response?.portfolioContext ?? null,
});

const knowledgeTypeLabel = (type: KnowledgeSource['type']) => ({
  STRATEGY: '投资策略', INVESTMENT_REVIEW: '投资复盘', RESEARCH: '研究资料', GENERAL_NOTE: '普通笔记',
}[type] ?? type);

const KnowledgeSources: React.FC<{ sources: KnowledgeSource[] }> = ({ sources }) => (
  <Collapse size="small" style={{ marginTop: 12, whiteSpace: 'normal' }} items={[{
    key: 'sources', label: `参考资料 (${sources.length})`,
    children: <Space orientation="vertical" size={8} style={{ width: '100%' }}>{sources.map((source) => {
      const query = new URLSearchParams({ documentId: source.documentId, chunkId: source.id, version: String(source.version) });
      return <Card key={`${source.documentId}-${source.id}-${source.version}`} size="small" style={{ width: '100%' }}>
        <Space orientation="vertical" size={4} style={{ width: '100%' }}>
          <Link to={`/knowledge?${query}`}>{source.title}</Link>
          <Space wrap size={4}><Tag>{knowledgeTypeLabel(source.type)}</Tag><Text type="secondary" style={{ fontSize: 12 }}>v{source.version} · {source.updatedAt?.replace('T', ' ').slice(0, 19)}</Text></Space>
          {source.section && <Text strong style={{ fontSize: 12 }}>{source.section}</Text>}
          <Paragraph ellipsis={{ rows: 3, expandable: true, symbol: '展开' }} style={{ whiteSpace: 'pre-wrap', margin: 0, fontSize: 12 }}>{source.excerpt}</Paragraph>
        </Space>
      </Card>;
    })}</Space>,
  }]} />
);

const Agent: React.FC = () => {
  const { token } = theme.useToken();
  const { categories } = useCategories();
  const { refresh: refreshSnapshots } = useSnapshots();

  const [input, setInput] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>(INITIAL_MESSAGES);
  const [conversations, setConversations] = useState<AgentConversation[]>([]);
  const [activeConversationId, setActiveConversationId] = useState<string | null>(null);
  const [loadingConversation, setLoadingConversation] = useState(true);
  const [messagePage, setMessagePage] = useState(0);
  const [hasMoreMessages, setHasMoreMessages] = useState(false);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const conversationGeneration = useRef(0);
  const streamAbort = useRef<AbortController | null>(null);
  useEffect(() => () => streamAbort.current?.abort(), []);
  const olderRequest = useRef<symbol | null>(null);
  const [form] = Form.useForm<DraftEntryFormValues>();

  const [providers, setProviders] = useState<AiProviderConfig[]>([]);
  const [aiPermission, setAiPermission] = useState<AiPermissionLevel | null>(null);
  const [selectedProviderId, setSelectedProviderId] = useState<string | undefined>();
  const [sending, setSending] = useState(false);

  const [draft, setDraft] = useState<PendingDraft | null>(null);
  const [execution, setExecution] = useState<PendingActionExecutionResult | null>(null);
  const [creatingDraft, setCreatingDraft] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [cancelling, setCancelling] = useState(false);

  const executed = execution?.status === 'EXECUTED';
  const cancelled = draft?.data.status === 'CANCELLED';
  const deleteOperation = draft?.kind === 'delete' || draft?.data.actionType === 'BATCH_DELETE_SNAPSHOTS';
  const pending = draft?.data.status === 'PENDING' && !executed;
  const actionError = (err: unknown) => {
    if (err instanceof ApiError && [40403, 40904, 40905].includes(err.code)) {
      setDraft(null);
      setExecution(null);
    }
    if (err instanceof Error) message.error(err.message);
  };

  const loadConversation = async (id: string) => {
    const generation = ++conversationGeneration.current;
    olderRequest.current = null;
    setLoadingOlder(false);
    setLoadingConversation(true);
    try {
      const detail = await apiAgentConversations.detail(id);
      if (generation !== conversationGeneration.current) return;
      const saved = detail.messages;
      setMessages(saved.length ? saved.map(toChatMessage) : INITIAL_MESSAGES);
      setMessagePage(0);
      setHasMoreMessages(detail.hasMore);
      setActiveConversationId(id);
      localStorage.setItem(ACTIVE_CONVERSATION_KEY, id);
      if (detail.conversation.providerId) setSelectedProviderId(detail.conversation.providerId);
      setDraft(null);
      setExecution(null);
      const lastResult = [...saved].reverse().find(item =>
        item.response?.draft || item.response?.deleteDraft || item.response?.batchDraft || item.response?.execution);
      const response = lastResult?.response;
      const restored: PendingDraft | null = response?.batchDraft
        ? { kind: 'batch', data: response.batchDraft }
        : response?.deleteDraft ? { kind: 'delete', data: response.deleteDraft }
        : response?.draft ? { kind: 'create', data: response.draft } : null;
      if (restored) {
        try {
          const { status } = await apiAgentConversations.actionStatus(id, restored.data.actionId);
          if (generation !== conversationGeneration.current) return;
          setDraft({ ...restored, data: { ...restored.data, status } } as PendingDraft);
          if (status === 'EXECUTED') setExecution({
            actionId: restored.data.actionId, actionType: restored.data.actionType,
            status, displaySummary: restored.data.displaySummary, snapshot: null,
          });
        } catch {
          if (generation !== conversationGeneration.current) return;
          setDraft({ ...restored, data: { ...restored.data, status: 'EXPIRED' } } as PendingDraft);
        }
      } else if (response?.execution) setExecution(response.execution);
    } catch (err) {
      if (generation === conversationGeneration.current)
        message.error(err instanceof Error ? err.message : '读取会话失败');
    } finally {
      if (generation === conversationGeneration.current) setLoadingConversation(false);
    }
  };

  useEffect(() => {
    let active = true;
    apiAgentConversations.list().then(async list => {
      if (!active) return;
      let preferredId = localStorage.getItem(ACTIVE_CONVERSATION_KEY);
      try {
        const legacy = sessionStorage.getItem('wealthflow-agent-session-v2');
        if (legacy && !sessionStorage.getItem('wealthflow-agent-session-imported')) {
          const old = JSON.parse(legacy) as { messages?: ChatMessage[]; providerId?: string };
          const oldMessages = (old.messages ?? []).filter(item =>
            !item.loading && (item.role === 'user' || item.role === 'assistant') && typeof item.content === 'string');
          if (oldMessages.length) {
            const imported = await apiAgentConversations.create();
            await apiAgentConversations.importLegacy(imported.id,
              oldMessages.slice(-40).map(({ role, content }) => ({ role, content })));
            preferredId = imported.id;
            if (old.providerId) setSelectedProviderId(old.providerId);
            list = await apiAgentConversations.list();
          }
          sessionStorage.setItem('wealthflow-agent-session-imported', '1');
        }
      } catch { message.warning('旧标签页的会话未能导入，原始记录仍保留在浏览器中'); }
      if (!list.length) list = [await apiAgentConversations.create()];
      if (!active) return;
      setConversations(list);
      await loadConversation(list.some(item => item.id === preferredId) ? preferredId! : list[0].id);
    }).catch(err => {
      if (active) { setLoadingConversation(false); message.error(err instanceof Error ? err.message : '读取会话失败'); }
    });
    return () => {
      active = false;
      conversationGeneration.current += 1;
      olderRequest.current = null;
    };
  }, []);

  const refreshConversations = async () => setConversations(await apiAgentConversations.list());
  const createConversation = async () => {
    if (sending || confirming || cancelling || creatingDraft) return;
    try {
      const created = await apiAgentConversations.create();
      await refreshConversations();
      await loadConversation(created.id);
    } catch (err) { message.error(err instanceof Error ? err.message : '新建会话失败'); }
  };
  const renameConversation = async (item: AgentConversation) => {
    const title = window.prompt('会话名称', item.title)?.trim();
    if (!title || title === item.title) return;
    try { await apiAgentConversations.rename(item.id, title); await refreshConversations(); }
    catch (err) { message.error(err instanceof Error ? err.message : '重命名失败'); }
  };
  const deleteConversation = (item: AgentConversation) => {
    Modal.confirm({ title: '删除会话？', content: `“${item.title}”的聊天记录将被永久删除。`,
      okText: '删除', okButtonProps: { danger: true }, cancelText: '取消',
      onOk: async () => {
        await apiAgentConversations.delete(item.id);
        let remaining = await apiAgentConversations.list();
        if (!remaining.length) remaining = [await apiAgentConversations.create()];
        setConversations(remaining);
        if (item.id === activeConversationId) await loadConversation(remaining[0].id);
      },
    });
  };
  const loadOlder = async () => {
    if (!activeConversationId || !hasMoreMessages || loadingConversation || olderRequest.current) return;
    const generation = conversationGeneration.current;
    const request = Symbol();
    olderRequest.current = request;
    setLoadingOlder(true);
    try {
      const nextPage = messagePage + 1;
      const detail = await apiAgentConversations.detail(activeConversationId, nextPage);
      if (generation !== conversationGeneration.current || olderRequest.current !== request) return;
      setMessages(previous => {
        const ids = new Set(previous.map(item => item.id));
        return [...detail.messages.filter(item => !ids.has(item.id)).map(toChatMessage), ...previous];
      });
      setMessagePage(nextPage);
      setHasMoreMessages(detail.hasMore);
    } catch (err) {
      if (generation === conversationGeneration.current)
        message.error(err instanceof Error ? err.message : '读取消息失败');
    } finally {
      if (olderRequest.current === request) {
        olderRequest.current = null;
        setLoadingOlder(false);
      }
    }
  };

  useEffect(() => {
    let cancelledRequest = false;
    aiProviderService
      .getAll()
      .then((list) => {
        if (cancelledRequest) return;
        const usable = list.filter(provider => provider.configured);
        setProviders(usable);
        setSelectedProviderId((current) => usable.some(p => p.providerId === current) ? current : usable[0]?.providerId);
      })
      .catch((err) => {
        if (cancelledRequest) return;
        if (err instanceof Error) {
          message.error(err.message);
        }
      });
    return () => {
      cancelledRequest = true;
    };
  }, []);

  useEffect(() => {
    let active = true;
    apiAgentPermission.get()
      .then(result => { if (active) setAiPermission(result.level); })
      .catch(err => { if (active) message.error(err instanceof Error ? err.message : 'AI 权限读取失败'); });
    return () => { active = false; };
  }, []);

  const sendMessage = async (text?: string) => {
    const content = (text ?? input).trim();
    if (!content || sending || confirming || cancelling || creatingDraft || !selectedProviderId || !activeConversationId || loadingConversation) return;
    if (pending) { message.info('请先确认或取消右侧草案，再发起新的请求'); return; }
    if (!aiPermission) { message.info('正在读取 AI 权限，请稍后再发送'); return; }

    setSending(true);
    try {
      const latest = await apiAgentPermission.get();
      if (latest.level !== aiPermission) {
        setAiPermission(latest.level);
        message.warning('AI 权限已变更，请查看当前档位后重新发送');
        setSending(false);
        return;
      }
    } catch (err) {
      message.error(err instanceof Error ? err.message : 'AI 权限读取失败');
      setSending(false);
      return;
    }

    const userMessage: ChatMessage = {
      id: Date.now(),
      role: 'user',
      content,
    };
    const placeholderId = Date.now() + 1;

    setMessages((previous) => [
      ...previous,
      userMessage,
      {
        id: placeholderId,
        role: 'assistant',
        content: '',
        loading: true,
        progress: [],
        startedAt: Date.now(),
      },
    ]);
    setInput('');
    try {
      const controller = new AbortController();
      streamAbort.current = controller;
      const saved = await streamAgentMessage(activeConversationId, selectedProviderId, content, event => {
        setMessages(previous => previous.map(item => {
          if (item.id !== placeholderId) return item;
          const progress = [...(item.progress ?? [])];
          const index = progress.findIndex(step => step.id === event.id);
          if (index >= 0) progress[index] = event; else progress.push(event);
          return { ...item, progress, lastContactAt: Date.now() };
        }));
      }, () => setMessages(previous => previous.map(item => item.id === placeholderId ? { ...item, lastContactAt: Date.now() } : item)), controller.signal);
      const result = saved.response!;

      const hasOperation = Boolean(result.draft || result.deleteDraft || result.batchDraft || result.execution);
      const unsupportedClaim = !hasOperation && actionStatusClaim(result.reply);
      const shownReply = unsupportedClaim
        ? '模型声称操作已完成，但系统没有收到草案或执行结果。本次未生成草案，也未修改数据。请重新明确日期、分类和金额后再试。'
        : !hasOperation && !result.draftError && asksToChangeData(content)
        ? `${result.reply}\n\n系统状态：本次未生成草案，也未修改数据。`
        : result.reply;

      setMessages((previous) => previous.map((item) =>
        item.id === placeholderId
          ? { id: saved.id, role: 'assistant', content: shownReply, steps: result.steps,
              sources: result.sources ?? [], newsSources: result.newsSources ?? [], webSources: result.webSources ?? [],
              cryptoMarkets: result.cryptoMarkets ?? [], portfolioContext: result.portfolioContext ?? null,
              progress: item.progress, startedAt: item.startedAt, lastContactAt: item.lastContactAt }
          : item));
      void refreshConversations().catch(() => {});
      if (unsupportedClaim) message.warning('模型没有返回有效草案，本次未修改数据');

      if (result.draft) {
        setDraft({ kind: 'create', data: result.draft });
        setExecution(null);
        message.success('草案已生成，请核对后确认');
      }
      if (result.deleteDraft) {
        setDraft({ kind: 'delete', data: result.deleteDraft });
        setExecution(null);
        message.success('删除草案已生成，请核对后确认');
      }
      if (result.draftError) {
        message.warning(result.draftError);
      }
      if (result.batchDraft) {
        setDraft({ kind: 'batch', data: result.batchDraft });
        setExecution(null);
        message.success('批量草案已生成，请核对逐日明细');
      }
      if (result.execution) {
        setDraft(null);
        setExecution(result.execution);
        refreshSnapshots();
        message.success(result.execution.displaySummary || 'AI 操作已直接完成');
      }
    } catch (err) {
      setMessages(previous => previous.map(item => item.id === placeholderId
        ? { ...item, loading: false, content: err instanceof Error ? err.message : '助手处理失败，请刷新会话核对结果后重试' } : item));
      if (err instanceof Error) {
        message.error(err.message);
      }
    } finally {
      streamAbort.current = null;
      setSending(false);
      void apiAgentPermission.get().then(result => setAiPermission(result.level)).catch(() => {});
    }
  };

  const generateDraft = async () => {
    if (sending || pending || confirming || cancelling || creatingDraft || !activeConversationId) return;
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

      setDraft({ kind: 'create', data: created });
      const saved = await apiAgentConversations.attachManualDraft(activeConversationId, created);
      setMessages(previous => [...previous, toChatMessage(saved)]);
      void refreshConversations().catch(() => {});
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
    if (!draft || draft.data.status !== 'PENDING' || confirming || cancelling || sending) return;

    setConfirming(true);
    try {
      const result =
        draft.kind === 'batch'
          ? await apiAgentActions.confirmBatchAction(draft.data.actionId)
          : draft.kind === 'delete'
          ? await apiAgentActions.confirmDeleteAction(draft.data.actionId)
          : await apiAgentActions.confirmAction(draft.data.actionId);
      setExecution(result);

      if (result.status === 'EXECUTED') {
        refreshSnapshots();
        if (activeConversationId) {
          try {
            const saved = await apiAgentConversations.recordActionOutcome(activeConversationId, draft.data.actionId);
            setMessages(previous => [...previous, toChatMessage(saved)]);
            void refreshConversations().catch(() => {});
          } catch { message.warning('操作已执行，但会话记录未能更新；重新打开会话可查看最新状态'); }
        }
        message.success(
          result.displaySummary ||
            (draft.kind === 'delete' ? '快照已删除' : '快照已创建')
        );
      } else {
        message.warning(result.displaySummary || `操作未完成（${result.status}）`);
      }
    } catch (err) {
      actionError(err);
    } finally {
      setConfirming(false);
    }
  };

  const cancelDraft = async () => {
    if (!draft || draft.data.status !== 'PENDING' || confirming || cancelling) return;

    setCancelling(true);
    try {
      const result: PendingActionCancellationResult =
        await apiAgentActions.cancelAction(draft.data.actionId);

      setDraft((currentDraft) => {
        if (!currentDraft) return currentDraft;
        if (currentDraft.kind === 'batch') {
          return { kind: 'batch' as const, data: { ...currentDraft.data, status: result.status } };
        }
        if (currentDraft.kind === 'create') {
          return {
            kind: 'create' as const,
            data: {
              ...currentDraft.data,
              status: result.status,
              displaySummary: result.displaySummary,
              expiresAt: result.expiresAt,
            },
          };
        }
        return {
          kind: 'delete' as const,
          data: {
            ...currentDraft.data,
            status: result.status,
            displaySummary: result.displaySummary,
            expiresAt: result.expiresAt,
          },
        };
      });
      if (activeConversationId) {
        try {
          const saved = await apiAgentConversations.recordActionOutcome(activeConversationId, draft.data.actionId);
          setMessages(previous => [...previous, toChatMessage(saved)]);
          void refreshConversations().catch(() => {});
        } catch { message.warning('操作已取消，但会话记录未能更新；重新打开会话可查看最新状态'); }
      }
      message.success(result.displaySummary || '草案已取消');
    } catch (err) {
      actionError(err);
    } finally {
      setCancelling(false);
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
        subtitle="参考投资策略与资产快照进行分析，查询近期财经讯息，并展示回答来源"
      />

      <Card size="small" title="会话" style={{ marginBottom: 16 }}
        extra={<Button size="small" icon={<PlusOutlined />} onClick={createConversation}
          disabled={sending || confirming || cancelling || creatingDraft}>新建会话</Button>}>
        <div style={{ maxHeight: 150, overflowY: 'auto' }}>
          <Space wrap>
            {conversations.map(item => (
              <Space.Compact key={item.id}>
                <Button type={item.id === activeConversationId ? 'primary' : 'default'}
                  loading={loadingConversation && item.id === activeConversationId}
                  disabled={sending || confirming || cancelling || creatingDraft || loadingConversation}
                  onClick={() => loadConversation(item.id)}>{item.title}</Button>
                <Button icon={<EditOutlined />} aria-label={`重命名 ${item.title}`}
                  disabled={sending || confirming || cancelling || creatingDraft || loadingConversation}
                  onClick={() => renameConversation(item)} />
                <Button icon={<DeleteOutlined />} aria-label={`删除 ${item.title}`}
                  disabled={sending || confirming || cancelling || creatingDraft || loadingConversation}
                  onClick={() => deleteConversation(item)} />
              </Space.Compact>
            ))}
          </Space>
        </div>
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={19}>
          <Card
            title={<Space><RobotOutlined /> 与 WealthFlow 对话</Space>}
            styles={{ body: { padding: 0 } }}
          >
            <Alert
              showIcon
              type="info"
              icon={<RobotOutlined />}
              message="已接入 AI 模型"
              description={aiPermission === 'READ_ONLY'
                ? '当前为只读：AI 只能查询和分析。'
                : aiPermission === 'DIRECT'
                ? '当前为直接执行：你明确要求修改时，AI 操作经后端校验后立即写入。'
                : aiPermission === 'REVIEW'
                ? '当前为审核模式：AI 可以准备草案，写入前由你确认。'
                : '正在读取服务端 AI 权限设置。'}
              style={{ margin: 16, marginBottom: 0 }}
            />

            <div
              className="agent-chat-messages"
              style={{
                '--chat-scrollbar-thumb': hexToRgba(token.colorPrimary, 0.45),
                '--chat-scrollbar-thumb-hover': token.colorPrimary,
                height: 460,
                overflowY: 'auto',
                padding: 20,
                background: token.colorBgLayout,
              } as React.CSSProperties}
            >
              {hasMoreMessages && <Button block size="small" onClick={loadOlder}
                loading={loadingOlder} disabled={loadingConversation || sending}>加载更早消息</Button>}
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
                          {item.loading ? (
                            <AgentActivity events={item.progress ?? []} active startedAt={item.startedAt ?? Date.now()} lastContactAt={item.lastContactAt} />
                          ) : (
                            <>
                              {item.content}
                              {!!item.progress?.length && <Collapse size="small" style={{ marginTop: 8 }} items={[{
                                key: 'progress', label: `处理过程 (${item.progress.length}步)`,
                                children: <AgentActivity events={item.progress} active={false} startedAt={item.startedAt ?? 0} />,
                              }]} />}
                              {!!item.steps?.length && <div style={{ marginTop: 8 }}><Text type="secondary" style={{ fontSize: 12 }}>{item.steps.join(' → ')}</Text></div>}
                              {!!item.sources?.length && <KnowledgeSources sources={item.sources} />}
                              {!!item.newsSources?.length && <NewsSources sources={item.newsSources} />}
                              {!!item.webSources?.length && <WebSources sources={item.webSources} />}
                              {!!item.cryptoMarkets?.length && <CryptoMarkets contexts={item.cryptoMarkets} />}
                              {item.portfolioContext && <PortfolioBasis context={item.portfolioContext} />}
                            </>
                          )}
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
              {providers.length === 0 ? (
                <Alert
                  showIcon
                  type="warning"
                  message="请先到设置页面配置 AI 提供商"
                  description="配置 AI 提供商后才能对话；修改方式由设置页中的 AI 权限决定。"
                  style={{ marginBottom: 12 }}
                />
              ) : (
                <Space style={{ width: '100%', marginBottom: 12 }}>
                  <Text type="secondary">模型：</Text>
                  <Select
                    style={{ minWidth: 240 }}
                    value={selectedProviderId}
                    onChange={setSelectedProviderId}
                    options={providers.map((provider) => ({
                      value: provider.providerId,
                      label: `${provider.displayName}（${provider.model}）`,
                    }))}
                  />
                </Space>
              )}

              <Space direction="vertical" style={{ width: '100%', marginBottom: 12 }}>
                {pending && <Alert type="info" showIcon message="请先核对右侧草案并确认或取消，再继续对话" />}
              </Space>
              <Space className="agent-chat-suggestions" wrap size={8} style={{ width: '100%', marginBottom: 12 }}>
                {SUGGESTIONS.map((suggestion) => (
                  <Button
                    key={suggestion}
                    size="small"
                    disabled={sending || pending || confirming || cancelling || providers.length === 0 || !aiPermission || loadingConversation || !activeConversationId}
                    onClick={() => sendMessage(suggestion)}
                  >
                    {suggestion}
                  </Button>
                ))}
              </Space>
              <Input.Search
                value={input}
                placeholder="例如：根据我的定投策略，当前资产配置有什么需要关注？"
                maxLength={2000}
                enterButton={
                  <Button
                    type="primary"
                    icon={<SendOutlined />}
                    loading={sending}
                    disabled={pending || confirming || cancelling || creatingDraft || providers.length === 0 || !selectedProviderId || !aiPermission || loadingConversation || !activeConversationId}
                  >
                    发送
                  </Button>
                }
                onChange={(event) => setInput(event.target.value)}
                onSearch={() => sendMessage()}
              />
            </div>
          </Card>
        </Col>

        <Col xs={24} xl={5}>
          <Card
            title="操作状态"
            extra={
              executed ? (
                <Badge status="success" text="已执行" />
              ) : cancelled ? (
                <Badge status="default" text="已取消" />
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
                    <Text strong>
                      快照操作已完成
                    </Text>
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
                  发起新操作
                </Button>
              </Space>
            ) : draft ? (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Space align="start">
                  {cancelled ? (
                    <CloseCircleOutlined style={{ color: token.colorTextSecondary }} />
                  ) : deleteOperation ? (
                    <DeleteOutlined style={{ color: token.colorError }} />
                  ) : (
                    <WarningOutlined style={{ color: token.colorWarning }} />
                  )}
                  <div>
                    <Text strong>
                      {draft.kind === 'batch' ? '批量快照操作' : draft.kind === 'delete' ? '删除资产快照' : '创建资产快照'}
                    </Text>
                    <Paragraph type="secondary" style={{ margin: '4px 0 0' }}>
                      {cancelled
                        ? draft.kind === 'delete'
                          ? '该草案已取消，不会删除任何资产数据。'
                          : '该草案已取消，不会写入任何资产数据。'
                        : draft.data.displaySummary}
                    </Paragraph>
                  </div>
                </Space>

                <Card size="small" style={{ background: token.colorFillAlter }}>
                  <Space direction="vertical" size={8} style={{ width: '100%' }}>
                    {draft.kind === 'batch' ? <BatchDraftDetails draft={draft.data} /> : draft.kind === 'create' ? (
                      <>
                        <Space style={{ justifyContent: 'space-between', width: '100%' }}>
                          <Text type="secondary">快照日期</Text>
                          <Text strong>{draft.data.snapshotDate}</Text>
                        </Space>
                        <Divider style={{ margin: 0 }} />
                        {draft.data.items.map((item) => (
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
                          <AmountText amount={draft.data.totalAmount} style={{ fontSize: 16 }} />
                        </Space>
                      </>
                    ) : (
                      draft.data.items.map((item) => (
                        <Space
                          key={item.snapshotDate}
                          style={{ justifyContent: 'space-between', width: '100%' }}
                        >
                          <Text>{item.snapshotDate} 快照</Text>
                          <AmountText amount={item.totalAmount} />
                        </Space>
                      ))
                    )}
                  </Space>
                </Card>

                {draft.data.status === 'PENDING' ? (
                  <Space style={{ width: '100%' }}>
                    <Button
                      type="primary"
                      danger={deleteOperation}
                      block
                      icon={
                        draft.kind === 'delete' ? (
                          <DeleteOutlined />
                        ) : (
                          <CheckCircleOutlined />
                        )
                      }
                      loading={confirming}
                      disabled={confirming || cancelling || sending}
                      onClick={confirmDraft}
                    >
                      {deleteOperation ? '确认删除（不可恢复）' : draft.kind === 'batch' ? '确认执行' : '确认创建'}
                    </Button>
                    <Button
                      block
                      danger={draft.kind !== 'delete'}
                      icon={<CloseCircleOutlined />}
                      loading={cancelling}
                      disabled={confirming || cancelling || sending}
                      onClick={cancelDraft}
                    >
                      取消草案
                    </Button>
                  </Space>
                ) : (
                  <Button block onClick={resetDraft}>
                    重新生成草案
                  </Button>
                )}

                <Text type="secondary" style={{ fontSize: 12 }}>
                  操作编号：{draft.data.actionId} · 有效期至{' '}
                  {draft.data.expiresAt.replace('T', ' ')}
                </Text>
              </Space>
            ) : aiPermission === 'READ_ONLY' ? (
              <Alert type="info" showIcon message="AI 当前为只读模式"
                description="AI 可以查询和分析资产数据。需要由 AI 新增、修改或删除快照时，请先在设置页调整权限。" />
            ) : (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Text type="secondary">
                  暂无待确认草案。你也可以手动填写金额生成草案；手动草案始终需要确认后才写入。
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
                    disabled={sending || confirming || cancelling || loadingConversation || !activeConversationId}
                    onClick={generateDraft}
                  >
                    生成待确认草案
                  </Button>
                </Form>
              </Space>
            )}
          </Card>

        </Col>
      </Row>
    </>
  );
};

export default Agent;
