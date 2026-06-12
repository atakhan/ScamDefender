# ML Models

Models are not committed to git. Download them with:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\setup-models.ps1
```

Or place the GigaAM archive manually:

```
models/sherpa-ru/sherpa-onnx-nemo-transducer-giga-am-v2-russian-2025-04-19.tar.bz2
tar -xjf sherpa-onnx-nemo-transducer-giga-am-v2-russian-2025-04-19.tar.bz2
```

## Layout

```
models/
├── silero/silero_vad.onnx
├── minilm/model.onnx + tokenizer.json
└── sherpa-ru/
    └── sherpa-onnx-nemo-transducer-giga-am-v2-russian-2025-04-19/
        ├── encoder.int8.onnx
        ├── decoder.onnx
        ├── joiner.onnx
        ├── tokens.txt
        └── test_wavs/example.wav

core/libs/
├── sherpa-onnx-v*-java17.jar
└── sherpa-onnx-native-lib-win-x64-*.jar
```

## STT model

**Sherpa-ONNX GigaAM v2 Russian** — offline NeMo transducer (`model-type=nemo_transducer`).

Override location with `SCAMDEFENDER_MODELS` environment variable.
