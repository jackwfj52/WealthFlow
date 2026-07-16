import React from 'react';
import { Typography, Space } from 'antd';

const { Title } = Typography;

interface Props {
  title: string;
  subtitle?: string;
  extra?: React.ReactNode;
}

const PageHeader: React.FC<Props> = ({ title, subtitle, extra }) => (
  <Space
    style={{
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center',
      marginBottom: 24,
      flexWrap: 'wrap',
      gap: 12,
    }}
  >
    <div>
      <Title level={3} style={{ margin: 0 }}>
        {title}
      </Title>
      {subtitle && (
        <Typography.Text type="secondary" style={{ marginTop: 4, display: 'block' }}>
          {subtitle}
        </Typography.Text>
      )}
    </div>
    {extra && <div>{extra}</div>}
  </Space>
);

export default PageHeader;
