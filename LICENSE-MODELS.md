# Licenses of the trained models and third-party data

The source code of GestureControl is licensed under the MIT License (see [LICENSE](LICENSE)).
The trained model files are derived from public datasets and are distributed under the terms
those datasets require:

| Files | Trained on | License |
|---|---|---|
| `app/src/main/assets/models/pose_mlp.tflite`, `pose_mlp.json`; `ml/artifacts/static/*`; `gestures/src/test/resources/ml/pose_mlp_*`; `app/src/androidTest/assets/pose_vectors.csv` | HaGRIDv2 (hand landmarks and images) and IPN Hand (low-light pairs) | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) |
| `ml/artifacts/dynamic/*` | IPN Hand | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) |

Attribution:

- **HaGRIDv2** — A. Nuzhdin, A. Nagaev, A. Sautin, A. Kapitanov, K. Kvanchiani, "HaGRIDv2: 1M Images for Static and Dynamic Hand Gesture Recognition", arXiv:2412.01508. https://github.com/hukenovs/hagrid (CC BY-SA 4.0 variant, see the dataset's license file).
- **IPN Hand** — G. Benitez-Garcia, J. Olivares-Mercado, G. Sanchez-Perez, K. Yanai, "IPN Hand: A Video Dataset and Benchmark for Real-Time Continuous Hand Gesture Recognition", ICPR 2020, arXiv:2005.02134. https://gibranbenitez.github.io/IPN_Hand/ (CC BY 4.0).
- **MediaPipe Hand Landmarker** (`hand_landmarker.task`) — Google, Apache License 2.0. Not stored in this repository: the build downloads it from Google's model storage.

No dataset images or videos are included in this repository; only small derived files (model weights,
test vectors, accuracy reports).
