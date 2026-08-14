import React from 'react';
import { Layout, Menu, ConfigProvider, theme as antdTheme } from 'antd';
import {
  DashboardOutlined,
  CameraOutlined,
  AppstoreOutlined,
  LineChartOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import { useNavigate, useLocation } from 'react-router-dom';
import { AppProvider, useApp } from './storage';
import { SettingsProvider, useSettings } from './settings';
import AppRouter from './router';
import ErrorState from '../components/ErrorState';

const { Sider, Content } = Layout;

const menuItems = [
  { key: '/dashboard', icon: <DashboardOutlined />, label: '资产总览' },
  { key: '/snapshots', icon: <CameraOutlined />, label: '资产快照' },
  { key: '/categories', icon: <AppstoreOutlined />, label: '分类管理' },
  { key: '/trends', icon: <LineChartOutlined />, label: '趋势分析' },
  { key: '/settings', icon: <SettingOutlined />, label: '设置' },
];

const AppShell: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { error, reload } = useApp();
  const { token } = antdTheme.useToken();

  const selectedKey = menuItems
    .map((m) => m.key)
    .find((key) => location.pathname.startsWith(key)) ?? '/dashboard';

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        breakpoint="lg"
        collapsedWidth={64}
        style={{ background: '#001529' }}
      >
        <div
          style={{
            height: 48,
            margin: 16,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#fff',
            fontSize: 18,
            fontWeight: 600,
            whiteSpace: 'nowrap',
            overflow: 'hidden',
          }}
        >
          WealthFlow
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Content style={{ padding: 24, background: token.colorBgLayout, minHeight: '100vh' }}>
          {error ? <ErrorState message={error} onRetry={reload} /> : <AppRouter />}
        </Content>
      </Layout>
    </Layout>
  );
};

/** 按设置中的明暗主题包裹 ConfigProvider */
const ThemedApp: React.FC = () => {
  const { settings } = useSettings();
  return (
    <ConfigProvider
      theme={{
        algorithm:
          settings.theme === 'dark'
            ? antdTheme.darkAlgorithm
            : antdTheme.defaultAlgorithm,
      }}
    >
      <AppShell />
    </ConfigProvider>
  );
};

const App: React.FC = () => (
  <AppProvider>
    <SettingsProvider>
      <ThemedApp />
    </SettingsProvider>
  </AppProvider>
);

export default App;
