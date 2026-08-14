import React from 'react';
import { Typography } from 'antd';
import { formatCurrency } from '../utils/amount';
import { useSettings } from '../app/settings';

interface Props {
  amount: string | number;
  style?: React.CSSProperties;
}

const AmountText: React.FC<Props> = ({ amount, style }) => {
  const { settings } = useSettings();
  return (
    <Typography.Text style={{ fontVariantNumeric: 'tabular-nums', ...style }}>
      {formatCurrency(amount, settings.currencySymbol, {
        thousands: settings.thousandsSeparator,
      })}
    </Typography.Text>
  );
};

export default AmountText;
