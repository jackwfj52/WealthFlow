import React from 'react';
import { Layout, Menu } from 'antd';
import {
  DashboardOutlined,
  CameraOutlined,
  AppstoreOutlined,
  LineChartOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import { useNavigate, useLocation } from 'react-router-dom';
import { AppProvider } from './storage';
import AppRouter from './router';

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
        <Content style={{ padding: 24, background: '#f5f5f5', minHeight: '100vh' }}>
          <AppRouter />
        </Content>
      </Layout>
    </Layout>
  );
};

const App: React.FC = () => (
  <AppProvider>
    <AppShell />
  </AppProvider>
);

export default App;
