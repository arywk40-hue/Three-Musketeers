#!/usr/bin/env python3
"""
Convert trained PyTorch FallCNN to TFLite.

Pipeline: PyTorch → ONNX → TensorFlow → TFLite

Usage:
  python convert_to_tflite.py --model models/fall_cnn.pth --out models/fall_detection.tflite
"""

import argparse
import os
import sys
from pathlib import Path

import numpy as np
import torch

from config import WINDOW_SIZE, NUM_AXES, NUM_CLASSES
from model import FallCNN


def export_to_onnx(model: torch.nn.Module, onnx_path: str):
    """Export PyTorch model to ONNX."""
    model.eval()
    dummy_input = torch.randn(1, NUM_AXES, WINDOW_SIZE)
    torch.onnx.export(
        model,
        dummy_input,
        onnx_path,
        input_names=["input"],
        output_names=["output"],
        dynamic_axes={"input": {0: "batch"}, "output": {0: "batch"}},
        opset_version=17,
    )
    print(f"Exported ONNX: {onnx_path}")


def onnx_to_tflite(onnx_path: str, tflite_path: str) -> bool:
    """Convert ONNX to TFLite via onnx2tf or manual approach."""
    # Try onnx2tf
    try:
        import onnx2tf
        print("Converting ONNX → TFLite via onnx2tf...")
        onnx2tf.convert(
            input_onnx_file_path=onnx_path,
            output_folder_path=str(Path(tflite_path).parent),
            output_integer_quantized_tflite=False,
            verbosity="error",
        )
        # onnx2tf saves as *_float32.tflite
        generated = str(Path(tflite_path).parent / "fall_cnn_float32.tflite")
        if os.path.exists(generated):
            os.rename(generated, tflite_path)
            return True
    except ImportError:
        print("onnx2tf not available.")

    # Fallback: use tf directly with onnx-tf
    try:
        import onnx_tf
        import tensorflow as tf

        print("Converting ONNX → TF → TFLite...")
        onnx_model = onnx.load(onnx_path)
        tf_rep = onnx_tf.backend.prepare(onnx_model)
        tf_save_dir = str(Path(tflite_path).parent / "tf_model")
        tf_rep.export_graph(tf_save_dir)

        converter = tf.lite.TFLiteConverter.from_saved_model(tf_save_dir)
        tflite_model = converter.convert()
        with open(tflite_path, "wb") as f:
            f.write(tflite_model)
        return True
    except ImportError:
        print("onnx-tf not available.")

    # Last fallback: manual reconstruction in TF + train from scratch not viable here
    print("No TFLite conversion backend available.")
    print("Install one of:")
    print("  pip install onnx2tf")
    print("  pip install onnx-tf tensorflow")
    return False


def verify_tflite(tflite_path: str):
    """Run a quick sanity check on the TFLite model."""
    try:
        import tensorflow as tf
        interpreter = tf.lite.Interpreter(model_path=tflite_path)
        interpreter.allocate_tensors()
        input_details = interpreter.get_input_details()
        output_details = interpreter.get_output_details()

        print(f"TFLite input: {input_details[0]['shape']} ({input_details[0]['dtype']})")
        print(f"TFLite output: {output_details[0]['shape']} ({output_details[0]['dtype']})")

        # Run dummy inference
        dummy = np.random.randn(*input_details[0]['shape']).astype(np.float32)
        interpreter.set_tensor(input_details[0]['index'], dummy)
        interpreter.invoke()
        output = interpreter.get_tensor(output_details[0]['index'])
        print(f"TFLite inference OK: output shape {output.shape}")
        return True
    except ImportError:
        print("TensorFlow not available for verification.")
        print(f"TFLite model saved to {tflite_path} — verify manually.")
        return False
    except Exception as e:
        print(f"Verification failed: {e}")
        return False


def main():
    parser = argparse.ArgumentParser(description="Convert PyTorch model to TFLite")
    parser.add_argument("--model", default="models/fall_cnn.pth", help="PyTorch model path")
    parser.add_argument("--out", default="models/fall_detection.tflite", help="Output TFLite path")
    args = parser.parse_args()

    model_path = os.path.abspath(args.model)
    tflite_path = os.path.abspath(args.out)
    os.makedirs(os.path.dirname(tflite_path), exist_ok=True)
    onnx_path = tflite_path.replace(".tflite", ".onnx")

    if not os.path.exists(model_path):
        print(f"Error: model not found: {model_path}", file=sys.stderr)
        sys.exit(1)

    # Load model
    device = torch.device("cpu")
    model = FallCNN().to(device)
    model.load_state_dict(torch.load(model_path, map_location=device, weights_only=True))
    print(f"Loaded PyTorch model from {model_path}")

    # Export to ONNX
    export_to_onnx(model, onnx_path)

    # Convert to TFLite
    if onnx_to_tflite(onnx_path, tflite_path):
        file_size = os.path.getsize(tflite_path)
        print(f"TFLite model: {tflite_path} ({file_size / 1024:.1f} KB)")
        verify_tflite(tflite_path)
    else:
        print(f"ONNX model saved to {onnx_path} — manual TFLite conversion required.")
        sys.exit(1)


if __name__ == "__main__":
    main()
