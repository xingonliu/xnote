"""Host-only U2NETP probe. Android admission requires the separate device test."""

import argparse
import hashlib
import json
import platform
import time
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
import psutil
from PIL import Image

# -- Constants

MODEL_SHA256 = "309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8"
SAMPLE_SHA256 = "8d13d397fcbc4c3742d1d30d00cabe53c7b1ecd0a8cbf5a73ab0f78330fca12f"

# -- Functions


def run(asset_root: Path, output: Path, arena: bool) -> None:
    model = asset_root / "u2netp.onnx"
    sample = asset_root / "sample.jpg"
    if hashlib.sha256(model.read_bytes()).hexdigest() != MODEL_SHA256:
        raise ValueError("Model SHA-256 mismatch")
    if hashlib.sha256(sample.read_bytes()).hexdigest() != SAMPLE_SHA256:
        raise ValueError("Sample SHA-256 mismatch")
    graph = onnx.load(model)
    onnx.checker.check_model(graph)
    operators = sorted({node.op_type for node in graph.graph.node})
    opsets = {item.domain: item.version for item in graph.opset_import}
    del graph
    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    options.inter_op_num_threads = 1
    options.enable_cpu_mem_arena = arena
    options.enable_mem_pattern = arena
    process = psutil.Process()
    before = process.memory_info().rss
    started = time.perf_counter()
    session = ort.InferenceSession(str(model), sess_options=options, providers=["CPUExecutionProvider"])
    load_ms = (time.perf_counter() - started) * 1000
    original = Image.open(sample).convert("RGB")
    data = np.asarray(original.resize((320, 320), Image.Resampling.LANCZOS), dtype=np.float32)
    data = data / max(float(data.max()), 1)
    data = (data - np.array([0.485, 0.456, 0.406], dtype=np.float32)) / np.array(
        [0.229, 0.224, 0.225], dtype=np.float32
    )
    data = data.transpose(2, 0, 1)[None]
    timings = []
    for _ in range(5):
        started = time.perf_counter()
        prediction = session.run(["1959"], {"input.1": data})[0][0, 0]
        timings.append((time.perf_counter() - started) * 1000)
        if not np.isfinite(prediction).all() or float(prediction.max() - prediction.min()) <= 0.5:
            raise ValueError("Non-finite or degenerate sample prediction")
    low, high = float(prediction.min()), float(prediction.max())
    mask = Image.fromarray(((prediction - low) / (high - low) * 255).astype(np.uint8))
    mask = mask.resize(original.size, Image.Resampling.LANCZOS)
    original.putalpha(mask)
    output.parent.mkdir(parents=True, exist_ok=True)
    original.save(output.with_suffix(".png"))
    report = {
        "scope": "host CPU only; does not prove Android compatibility, latency or memory",
        "platform": platform.platform(),
        "ort": ort.__version__,
        "model_sha256": MODEL_SHA256,
        "sample_sha256": SAMPLE_SHA256,
        "model_bytes": model.stat().st_size,
        "opsets": opsets,
        "operators": operators,
        "cpu_arena_and_memory_pattern": arena,
        "input": [[item.name, item.shape, item.type] for item in session.get_inputs()],
        "requested_output": "1959",
        "load_ms": load_ms,
        "inference_ms": timings,
        "rss_before_bytes": before,
        "rss_after_bytes": process.memory_info().rss,
        "process_peak_working_set_bytes": getattr(process.memory_info(), "peak_wset", None),
        "mask_min_max": [low, high],
    }
    del session
    report["rss_after_session_close_bytes"] = process.memory_info().rss
    output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--assets", type=Path, default=Path("build/s12/assets"))
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--arena", action="store_true")
    args = parser.parse_args()
    run(args.assets, args.output, args.arena)


if __name__ == "__main__":
    main()
