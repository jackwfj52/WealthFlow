import React from 'react';
import { formatRate } from '../utils/rate';
import { contrastTextColor } from '../utils/color';

interface Props {
  rate: number;
  color: string;
}

/** 增长率小徽标：金额左侧展示，颜色由档位规则决定；宽度固定为能容纳 -100.00% */
const GrowthBadge: React.FC<Props> = ({ rate, color }) => (
  <span
    style={{
      display: 'inline-block',
      width: '8ch',
      textAlign: 'center',
      padding: '1px 2px',
      borderRadius: 4,
      fontSize: 12,
      lineHeight: '18px',
      background: color,
      color: contrastTextColor(color),
      fontVariantNumeric: 'tabular-nums',
      whiteSpace: 'nowrap',
      overflow: 'hidden',
    }}
  >
    {formatRate(rate)}
  </span>
);

export default GrowthBadge;
