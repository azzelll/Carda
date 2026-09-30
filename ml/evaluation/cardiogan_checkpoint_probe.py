"""Restore an inventoried research checkpoint and smoke-test CPU with SYNTHETIC input only.

No raw signal, ECG trace, health metric or model confidence is exported. This does not validate
phone-camera inference. Upstream CC-BY-NC-4.0 code is read from external scratch, not vendored.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import time


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--checkpoint", type=Path, required=True)
    parser.add_argument("--scratch", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--tflite", type=Path, help="Optional research float32 conversion; outside repository only")
    args = parser.parse_args()
    repository = Path(__file__).resolve().parents[2]
    if args.tflite is not None and args.tflite.resolve().is_relative_to(repository):
        raise ValueError("Unvalidated model artifacts must remain outside the repository")
    inventory = json.loads(Path(__file__).with_name("cardiogan_source_inventory.json").read_text())
    expected = {entry["name"]: entry["sha256"] for entry in inventory["files"]}
    for name in ["LICENSE", "codes/module.py", "codes/layers.py"]:
        payload = (args.source / name).read_bytes()
        if hashlib.sha256(payload).hexdigest() != expected[name]:
            raise ValueError(f"Inventoried source checksum differs: {name}")
    args.scratch.mkdir(parents=True, exist_ok=True)
    os.environ["TF_USE_LEGACY_KERAS"] = "1"
    os.environ["KERAS_HOME"] = str(args.scratch / "keras-cache")
    import numpy as np
    import tensorflow as tf
    reader = tf.train.load_checkpoint(str(args.checkpoint))
    dtypes = reader.get_variable_to_dtype_map()
    dtype = dtypes["Gen_PPG2ECG/layer_with_weights-0/kernel/.ATTRIBUTES/VARIABLE_VALUE"].name
    if dtype not in ("float32", "float64"):
        raise ValueError("Unsupported source generator dtype")
    tf.keras.backend.set_floatx(dtype)
    tf.random.set_seed(7)
    with tempfile.TemporaryDirectory(prefix="carda-cardiogan-adapter-", dir=args.scratch) as directory:
        adapted = Path(directory)
        module = (args.source / "codes/module.py").read_text().replace(
            "keras.Input(shape=input_shape)", "keras.Input(shape=(input_shape,))")
        layers = (args.source / "codes/layers.py").read_text().replace(
            "import tensorflow_addons as tfa", """class _UnsupportedAddons:
    class layers:
        @staticmethod
        def InstanceNormalization(*args, **kwargs):
            raise RuntimeError('Instance normalization is not supported by this adapter')
tfa = _UnsupportedAddons()""")
        (adapted / "module.py").write_text(module)
        (adapted / "layers.py").write_text(layers)
        (adapted / "LICENSE").write_bytes((args.source / "LICENSE").read_bytes())
        sys.path.insert(0, str(adapted))
        import module as upstream
        generator = upstream.generator_attention()  # Default uses layer_norm, not addons.
        checkpoint = tf.train.Checkpoint(Gen_PPG2ECG=generator)
        restored = checkpoint.restore(str(args.checkpoint))
        restored.assert_existing_objects_matched()  # Never accept uninitialized/partial model weights.
        restored.expect_partial()  # Only unused opposite generator/discriminators/optimizers remain.
        x = np.sin(2 * np.pi * 1.25 * np.arange(512) / 128).astype(dtype)[None, :]
        start = time.perf_counter()
        first = generator(x, training=False).numpy()
        latency = time.perf_counter() - start
        second = generator(x, training=False).numpy()
        if first.shape != (1, 512) or not np.isfinite(first).all():
            raise ValueError("Invalid smoke output shape or nonfinite values")
        if not np.array_equal(first, second):
            raise ValueError("CPU smoke is not deterministic")
        report = {
            "status": "restored_cpu_synthetic_smoke_only",
            "source_repository": inventory["repository"], "source_commit": inventory["commit"],
            "source_license": "CC-BY-NC-4.0", "tensorflow": tf.__version__,
            "source_dtype": dtype, "input_shape": [1, 512], "output_shape": list(first.shape),
            "generator_parameters": generator.count_params(), "all_model_objects_restored": True,
            "synthetic_input_only": True, "deterministic": True,
            "first_call_seconds": round(latency, 6), "output_sha256": hashlib.sha256(first.tobytes()).hexdigest(),
            "source_adaptations": ["Input shape expressed as tuple",
                "unused addons instance_norm explicitly unsupported; default layer_norm unchanged"],
            "unverified": ["physiological_validity", "camera_domain", "confidence_calibration",
                           "r_peak_rr_evaluation", "LiteRT_conversion", "Android_CPU_memory_latency"],
        }
        if args.tflite is not None:
            # Frozen software-only numerical tolerance in normalized waveform units, not physiology.
            tolerance = 1e-5
            tf.keras.backend.set_floatx("float32")
            generator32 = upstream.generator_attention()
            original_weights = generator.get_weights()
            if [w.shape for w in generator32.get_weights()] != [w.shape for w in original_weights]:
                raise ValueError("Float32 architecture weight shapes differ")
            generator32.set_weights([w.astype(np.float32) for w in original_weights])

            class ConversionModule(tf.Module):
                def __init__(self):
                    super().__init__()
                    self.generator = generator32

                @tf.function(input_signature=[tf.TensorSpec([1, 512], tf.float32, name="ppg")])
                def infer(self, ppg):
                    return {"estimated_ecg": self.generator(ppg, training=False)}

            wrapper = ConversionModule()
            concrete = wrapper.infer.get_concrete_function()
            converter = tf.lite.TFLiteConverter.from_concrete_functions([concrete], wrapper)
            converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
            model = converter.convert()  # No Select TF ops or hardware delegate dependency.
            interpreter = tf.lite.Interpreter(model_content=model, num_threads=1)
            interpreter.allocate_tensors()
            input_detail = interpreter.get_input_details()[0]
            output_detail = interpreter.get_output_details()[0]
            if list(input_detail["shape"]) != [1, 512] or list(output_detail["shape"]) != [1, 512]:
                raise ValueError("Converted tensor contract differs")
            max_dtype_error = 0.0
            max_lite_error = 0.0
            call_times = []
            for frequency in (1.0, 1.25, 1.5, 2.0):
                fixture = np.sin(2 * np.pi * frequency * np.arange(512) / 128)[None, :]
                source_output = generator(fixture.astype(dtype), training=False).numpy()
                float_output = generator32(fixture.astype(np.float32), training=False).numpy()
                interpreter.set_tensor(input_detail["index"], fixture.astype(np.float32))
                start = time.perf_counter()
                interpreter.invoke()
                call_times.append(time.perf_counter() - start)
                lite_output = interpreter.get_tensor(output_detail["index"])
                if not np.isfinite(lite_output).all():
                    raise ValueError("Nonfinite converted output")
                max_dtype_error = max(max_dtype_error, float(np.max(np.abs(source_output - float_output))))
                max_lite_error = max(max_lite_error, float(np.max(np.abs(float_output - lite_output))))
            if max_dtype_error > tolerance or max_lite_error > tolerance:
                raise ValueError(f"Frozen parity tolerance failed: dtype={max_dtype_error}, lite={max_lite_error}")
            args.tflite.write_bytes(model)
            report["conversion"] = {"research_only": True, "format": "float32_builtin_ops",
                "bytes": len(model), "sha256": hashlib.sha256(model).hexdigest(),
                "synthetic_fixtures": 4, "absolute_tolerance": tolerance,
                "max_float64_to_float32_error": max_dtype_error, "max_float32_to_lite_error": max_lite_error,
                "host_cpu_invocation_seconds": [round(t, 6) for t in call_times]}
            report["unverified"].remove("LiteRT_conversion")
        args.output.write_text(json.dumps(report, indent=2) + "\n")
        print(json.dumps(report))


if __name__ == "__main__":
    main()
