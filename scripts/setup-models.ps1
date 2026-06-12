$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ModelsRoot = Join-Path $ProjectRoot "models"
$LibsRoot = Join-Path $ProjectRoot "core\libs"
$SherpaVersion = "1.12.10"

function Ensure-Dir($path) {
    New-Item -ItemType Directory -Force -Path $path | Out-Null
}

function Download-File($url, $dest) {
    if (Test-Path $dest) {
        $size = (Get-Item $dest).Length
        if ($size -gt 0) {
            Write-Host "Skip existing ($size bytes): $dest"
            return
        }
    }
    Write-Host "Downloading $url"
    $parent = Split-Path -Parent $dest
    Ensure-Dir $parent
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        try {
            Invoke-WebRequest -Uri $url -OutFile $dest -UseBasicParsing
            return
        } catch {
            Write-Warning "Attempt $attempt failed: $_"
            Start-Sleep -Seconds 5
        }
    }
    throw "Failed to download $url"
}

Ensure-Dir $ModelsRoot
Ensure-Dir (Join-Path $ModelsRoot "silero")
Ensure-Dir (Join-Path $ModelsRoot "minilm")
Ensure-Dir (Join-Path $ModelsRoot "sherpa-ru")
Ensure-Dir $LibsRoot

# Silero VAD (~1 MB)
Download-File `
    "https://github.com/snakers4/silero-vad/raw/master/src/silero_vad/data/silero_vad.onnx" `
    (Join-Path $ModelsRoot "silero\silero_vad.onnx")

# MiniLM ONNX + tokenizer (~90 MB model)
Download-File `
    "https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/onnx/model.onnx" `
    (Join-Path $ModelsRoot "minilm\model.onnx")
Download-File `
    "https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/tokenizer.json" `
    (Join-Path $ModelsRoot "minilm\tokenizer.json")

# Sherpa-ONNX Java API + Windows x64 natives (optional — large download)
try {
    Download-File `
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$SherpaVersion/sherpa-onnx-v$SherpaVersion.jar" `
        (Join-Path $LibsRoot "sherpa-onnx-v$SherpaVersion.jar")
    Download-File `
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$SherpaVersion/sherpa-onnx-native-lib-win-x64-v$SherpaVersion.jar" `
        (Join-Path $LibsRoot "sherpa-onnx-native-lib-win-x64-v$SherpaVersion.jar")
} catch {
    Write-Warning "Sherpa JARs not downloaded (STT will use fallback): $_"
}

# GigaAM v2 Russian offline transducer (NeMo)
$SherpaArchive = Join-Path $ModelsRoot "sherpa-ru\sherpa-onnx-nemo-transducer-giga-am-v2-russian-2025-04-19.tar.bz2"
$RuModelUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-nemo-transducer-giga-am-v2-russian-2025-04-19.tar.bz2"
$TokensPath = Join-Path $ModelsRoot "sherpa-ru\sherpa-onnx-nemo-transducer-giga-am-v2-russian-2025-04-19\tokens.txt"
if (-not (Test-Path $TokensPath)) {
    try {
        if (-not (Test-Path $SherpaArchive)) {
            Download-File $RuModelUrl $SherpaArchive
        }
        Write-Host "Extracting GigaAM v2 Russian STT model..."
        tar -xjf $SherpaArchive -C (Join-Path $ModelsRoot "sherpa-ru")
    } catch {
        Write-Warning "GigaAM v2 model not downloaded: $_"
        Write-Warning "You can manually place the .tar.bz2 into models/sherpa-ru/ and extract it."
    }
}

Write-Host ""
Write-Host "Models ready in: $ModelsRoot"
Write-Host "Sherpa JARs in: $LibsRoot"
Write-Host "Run: .\gradlew.bat :core:test"
