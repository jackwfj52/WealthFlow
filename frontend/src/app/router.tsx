import React, { lazy, Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { Spin } from 'antd';

const Dashboard = lazy(() => import('../features/dashboard'));
const Snapshots = lazy(() => import('../features/snapshots'));
const Categories = lazy(() => import('../features/categories'));
const Trends = lazy(() => import('../features/trends'));
const Settings = lazy(() => import('../features/settings'));

const Loading = () => (
  <Spin size="large" style={{ display: 'block', marginTop: 120 }} />
);

const AppRouter: React.FC = () => (
  <Suspense fallback={<Loading />}>
    <Routes>
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="/dashboard" element={<Dashboard />} />
      <Route path="/snapshots" element={<Snapshots />} />
      <Route path="/categories" element={<Categories />} />
      <Route path="/trends" element={<Trends />} />
      <Route path="/settings" element={<Settings />} />
    </Routes>
  </Suspense>
);

export default AppRouter;
