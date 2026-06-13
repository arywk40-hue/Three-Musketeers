"""
1D-CNN for IMU-based fall detection.

Architecture:
  Input:  (WINDOW_SIZE, NUM_AXES) — e.g. (400, 3) for 2s at 200Hz
  Conv1D(32, 5) → ReLU → MaxPool(2)
  Conv1D(64, 5) → ReLU → MaxPool(2)
  Conv1D(128, 3) → ReLU → GlobalAvgPool
  Dense(64) → ReLU → Dropout(0.5)
  Dense(2) → Softmax

  Output: [P_NoFall, P_Fall]
"""

import torch
import torch.nn as nn

from config import WINDOW_SIZE, NUM_AXES, NUM_CLASSES, CONV1_FILTERS, CONV1_KERNEL, \
    CONV2_FILTERS, CONV2_KERNEL, CONV3_FILTERS, CONV3_KERNEL, DENSE_UNITS, DROPOUT


class FallCNN(nn.Module):
    def __init__(
        self,
        input_channels: int = NUM_AXES,
        sequence_length: int = WINDOW_SIZE,
        num_classes: int = NUM_CLASSES,
    ):
        super().__init__()
        self.conv1 = nn.Sequential(
            nn.Conv1d(input_channels, CONV1_FILTERS, kernel_size=CONV1_KERNEL, padding="same"),
            nn.ReLU(),
            nn.MaxPool1d(2),
        )
        self.conv2 = nn.Sequential(
            nn.Conv1d(CONV1_FILTERS, CONV2_FILTERS, kernel_size=CONV2_KERNEL, padding="same"),
            nn.ReLU(),
            nn.MaxPool1d(2),
        )
        self.conv3 = nn.Sequential(
            nn.Conv1d(CONV2_FILTERS, CONV3_FILTERS, kernel_size=CONV3_KERNEL, padding="same"),
            nn.ReLU(),
        )
        self.global_pool = nn.AdaptiveAvgPool1d(1)
        self.classifier = nn.Sequential(
            nn.Linear(CONV3_FILTERS, DENSE_UNITS),
            nn.ReLU(),
            nn.Dropout(DROPOUT),
            nn.Linear(DENSE_UNITS, num_classes),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        """
        Args:
            x: (batch, channels, sequence_length) = (batch, 3, 400)
        Returns:
            logits: (batch, num_classes)
        """
        x = self.conv1(x)
        x = self.conv2(x)
        x = self.conv3(x)
        x = self.global_pool(x).squeeze(-1)
        x = self.classifier(x)
        return x


def count_parameters(model: nn.Module) -> int:
    return sum(p.numel() for p in model.parameters() if p.requires_grad)
