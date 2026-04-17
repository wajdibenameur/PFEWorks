from __future__ import annotations

import torch
from torch import nn


class TabularSeverityNet(nn.Module):
    def __init__(self, input_dim: int, hidden_sizes: list[int], dropout: float = 0.0):
        super().__init__()
        layers = []
        previous = input_dim
        for hidden in hidden_sizes:
            layers.append(nn.Linear(previous, hidden))
            layers.append(nn.ReLU())
            if dropout > 0:
                layers.append(nn.Dropout(dropout))
            previous = hidden
        layers.append(nn.Linear(previous, 3))
        self.network = nn.Sequential(*layers)

    def forward(self, inputs: torch.Tensor) -> torch.Tensor:
        return self.network(inputs)
