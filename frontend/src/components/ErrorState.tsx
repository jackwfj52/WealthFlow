import React from 'react';
import { Result, Button } from 'antd';

interface Props {
  message?: string;
  onRetry?: () => void;
}

const ErrorState: React.FC<Props> = ({
  message = '加载失败，请稍后重试',
  onRetry,
}) => (
  <Result
    status="error"
    title="出错了"
    subTitle={message}
    extra={
      onRetry && (
        <Button type="primary" onClick={onRetry}>
          重试
        </Button>
      )
    }
  />
);

export default ErrorState;
