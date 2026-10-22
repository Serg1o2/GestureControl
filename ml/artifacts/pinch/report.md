# Pinch detector (pinch_mlp)

Threshold 0.5 (≤ 0.3% false positives on validation negatives).

| test source | pinches | recall | negatives | false positives |
|---|---|---|---|---|
| HaGRID annotations | 4989 | 99.9% | 218806 | 0.09% |
| HaGRID our MediaPipe | 108 | 100.0% | 1775 | 0.06% |
| IPN clean (negatives) | 0 | nan% | 41148 | 0.04% |
| IPN low light (negatives) | 0 | nan% | 16089 | 0.14% |
