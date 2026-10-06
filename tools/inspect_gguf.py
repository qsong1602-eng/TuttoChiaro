"""Read GGUF metadata without loading model tensors."""
import json
import struct
import sys

with open(sys.argv[1], "rb") as f:
    def number(fmt):
        return struct.unpack("<" + fmt, f.read(struct.calcsize(fmt)))[0]

    def string():
        return f.read(number("Q")).decode("utf-8")

    formats = {0: "B", 1: "b", 2: "H", 3: "h", 4: "I", 5: "i", 6: "f", 7: "?", 10: "Q", 11: "q", 12: "d"}

    def value(kind):
        if kind == 8:
            return string()
        if kind == 9:
            subtype, count = number("I"), number("Q")
            for _ in range(count):
                value(subtype)
            return {"array_length": count}
        return number(formats[kind])

    assert f.read(4) == b"GGUF"
    version, tensors, entries = number("I"), number("Q"), number("Q")
    result = {"version": version, "tensors": tensors}
    for _ in range(entries):
        key = string()
        result[key] = value(number("I"))
    print(json.dumps(result, ensure_ascii=True, indent=2))
