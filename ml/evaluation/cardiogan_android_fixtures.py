"""Prepare opt-in Android CPU parity assets outside Git using four SYNTHETIC inputs.

Requires the checkpoint probe's successful conversion report and its pinned model artifact.
Never accepts or downloads participant data; this proves numerical software parity only.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--conversion-report", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    repository = Path(__file__).resolve().parents[2]
    if args.output.resolve().is_relative_to(repository):
        raise ValueError("Research model/fixture assets must remain outside the repository")
    conversion = json.loads(args.conversion_report.read_text())["conversion"]
    digest = hashlib.sha256()
    with args.model.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    if digest.hexdigest() != conversion["sha256"] or args.model.stat().st_size != conversion["bytes"]:
        raise ValueError("Model differs from successfully checked conversion")
    import numpy as np
    import tensorflow as tf
    interpreter = tf.lite.Interpreter(model_path=str(args.model), num_threads=1)
    interpreter.allocate_tensors()
    input_info = interpreter.get_input_details()[0]
    output_info = interpreter.get_output_details()[0]
    if list(input_info["shape"]) != [1, 512] or list(output_info["shape"]) != [1, 512]:
        raise ValueError("Unexpected tensor shape")
    args.output.mkdir(parents=True, exist_ok=True)
    for index, frequency in enumerate((1.0, 1.25, 1.5, 2.0)):
        fixture = np.sin(2 * np.pi * frequency * np.arange(512) / 128).astype(np.float32)[None, :]
        interpreter.set_tensor(input_info["index"], fixture)
        interpreter.invoke()
        output = interpreter.get_tensor(output_info["index"])
        if not np.isfinite(output).all():
            raise ValueError("Nonfinite host reference")
        (args.output / f"input-{index}.bin").write_bytes(fixture.astype("<f4").tobytes())
        (args.output / f"output-{index}.bin").write_bytes(output.astype("<f4").tobytes())
    shutil.copyfile(args.model, args.output / "research-model.tflite")
    manifest = {"synthetic_input_only": True, "fixtures": 4, "input_shape": [1, 512],
                "model_sha256": conversion["sha256"], "model_bytes": conversion["bytes"],
                "absolute_tolerance": conversion["absolute_tolerance"], "host_tensorflow": tf.__version__,
                "not_evidence_for": ["physiology", "camera_domain", "confidence", "real_device_performance"]}
    (args.output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(json.dumps(manifest))


if __name__ == "__main__":
    main()
