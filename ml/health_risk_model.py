"""
Unified Health Risk MLP — shared trunk, 3 output heads.

Input (7 floats, normalised to [0,1]):
  [heart_rate_bpm, spo2_percent, respiratory_rate, skin_temp_c,
   sweat_rate_pct_per_min, imu_magnitude, hr_reserve_pct]

Output heads (each 3 logits):
  vitals_risk    : 0=Low  1=Medium  2=High
  dehydration    : 0=Low  1=Medium  2=High
  overexertion   : 0=Safe 1=Caution 2=Stop
"""

import torch
import torch.nn as nn

from config import (
    HR_RISK_INPUT_DIM, HR_RISK_HIDDEN, HR_RISK_DROPOUT,
    HR_RISK_VITALS_CLASSES, HR_RISK_DEHYDRATION_CLASSES, HR_RISK_OVEREXERTION_CLASSES,
)


class HealthRiskMLP(nn.Module):
    def __init__(self):
        super().__init__()
        dims = [HR_RISK_INPUT_DIM] + HR_RISK_HIDDEN
        layers = []
        for i in range(len(dims) - 1):
            layers += [nn.Linear(dims[i], dims[i + 1]), nn.ReLU(), nn.Dropout(HR_RISK_DROPOUT)]
        self.trunk = nn.Sequential(*layers)
        feat = HR_RISK_HIDDEN[-1]
        self.head_vitals      = nn.Linear(feat, HR_RISK_VITALS_CLASSES)
        self.head_dehydration = nn.Linear(feat, HR_RISK_DEHYDRATION_CLASSES)
        self.head_overexertion = nn.Linear(feat, HR_RISK_OVEREXERTION_CLASSES)

    def forward(self, x: torch.Tensor):
        """
        Args:
            x: (batch, 7) normalised input
        Returns:
            dict of logits tensors, each (batch, 3)
        """
        z = self.trunk(x)
        return {
            "vitals":      self.head_vitals(z),
            "dehydration": self.head_dehydration(z),
            "overexertion": self.head_overexertion(z),
        }

    def forward_flat(self, x: torch.Tensor) -> torch.Tensor:
        """Flat output (batch, 9) for TFLite export — concat all 3 heads."""
        out = self.forward(x)
        return torch.cat([out["vitals"], out["dehydration"], out["overexertion"]], dim=1)
