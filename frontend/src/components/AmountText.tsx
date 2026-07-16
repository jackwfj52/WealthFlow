import React from 'react';
import { Typography } from 'antd';
import { formatAmount } from '../utils/amount';

interface Props {
  amount: string | number;
  style?: React.CSSProperties;
}

const AmountText: React.FC<Props> = ({ amount, style }) => (
  <Typography.Text style={{ fontVariantNumeric: 'tabular-nums', ...style }}>
    ¥{formatAmount(amount)}
  </Typography.Text>
);

export default AmountText;
