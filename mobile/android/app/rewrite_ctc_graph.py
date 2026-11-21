"""Turn the int8 CTC graph into Conv and MatMul ops Android ONNX Runtime can run.

The published graph uses ConvInteger and MatMulInteger. onnxruntime-android
1.22 has no kernel for those. Each integer op is followed by a cast and a
multiply by the activation and weight scales, which is the same as
dequantizing both tensors and running the float op.
"""

import sys
from collections import defaultdict
from pathlib import Path

import onnx
from onnx import helper

SOURCE = Path(__file__).with_name("ctc-model") / "model.int8.onnx"
TARGET = Path(__file__).with_name("ctc-model") / "model.onnx"


def attribute(node, name):
    return next(item for item in node.attribute if item.name == name)


def rewrite(model):
    produced = {output: node for node in model.graph.node for output in node.output}
    consumers = defaultdict(list)
    for node in model.graph.node:
        for name in node.input:
            if name:
                consumers[name].append(node)

    remove = set()
    added = []
    dequantized_weights = {}
    dequantized_inputs = {}
    for node in model.graph.node:
        if node.op_type not in ("ConvInteger", "MatMulInteger"):
            continue
        cast = consumers[node.output[0]][0]
        scale_mul = consumers[cast.output[0]][0]
        quantized_input = produced[node.input[0]]
        input_q, input_scale, input_zero = quantized_input.output
        weight_q, weight_zero = node.input[1], node.input[3]
        product = next(name for name in scale_mul.input if name != cast.output[0])
        weight_scale = next(name for name in produced[product].input if name != input_scale)
        if weight_q not in dequantized_weights:
            dequantized_weights[weight_q] = weight_q + "_f32"
            added.append(helper.make_node(
                "DequantizeLinear",
                [weight_q, weight_scale, weight_zero],
                [dequantized_weights[weight_q]],
                name=weight_q + "_dequant",
            ))
        if input_q not in dequantized_inputs:
            dequantized_inputs[input_q] = input_q + "_f32"
            added.append(helper.make_node(
                "DequantizeLinear",
                [input_q, input_scale, input_zero],
                [dequantized_inputs[input_q]],
                name=input_q + "_dequant",
            ))
        output = scale_mul.output[0]
        if node.op_type == "ConvInteger":
            added.append(helper.make_node(
                "Conv",
                [dequantized_inputs[input_q], dequantized_weights[weight_q]],
                [output],
                name=node.name + "_fp",
                dilations=attribute(node, "dilations").ints,
                group=attribute(node, "group").i,
                pads=attribute(node, "pads").ints,
                strides=attribute(node, "strides").ints,
            ))
        else:
            added.append(helper.make_node(
                "MatMul",
                [dequantized_inputs[input_q], dequantized_weights[weight_q]],
                [output],
                name=node.name + "_fp",
            ))
        remove.update([node.name, cast.name, scale_mul.name, produced[product].name])

    kept = [node for node in model.graph.node if node.name not in remove]
    del model.graph.node[:]
    model.graph.node.extend(kept)
    model.graph.node.extend(added)
    return model


def main():
    if TARGET.is_file() and b"ConvInteger" not in TARGET.read_bytes():
        return
    model = onnx.load(SOURCE, load_external_data=False)
    onnx.save(rewrite(model), TARGET)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"rewrite_ctc_graph: {error}", file=sys.stderr)
        sys.exit(1)
