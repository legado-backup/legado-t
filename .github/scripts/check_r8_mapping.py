#!/usr/bin/env python3
"""Verify names required by reflection remain stable in an R8 mapping file."""

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any


DEFAULT_CONTRACT = Path(__file__).with_name("r8-reflection-contracts.json")


def parse_mapping(mapping_text: str) -> dict[str, tuple[str, list[str]]]:
    classes: dict[str, tuple[str, list[str]]] = {}
    current_class: str | None = None

    for line in mapping_text.splitlines():
        if line and not line[0].isspace() and " -> " in line and line.endswith(":"):
            original, obfuscated = line[:-1].split(" -> ", 1)
            current_class = original
            classes[current_class] = (obfuscated, [])
            continue

        if current_class is None or not line[:1].isspace() or " -> " not in line:
            continue

        member = line.strip()
        # R8 method mappings can have line ranges and return types. Contracts
        # use fields, whose mapping has the stable form: "type name -> name".
        if "(" in member or ")" in member:
            continue
        match = re.fullmatch(r".+\s+([\w$]+)\s+->\s+([\w$]+)", member)
        if match:
            classes[current_class][1].append(f"{match.group(1)} -> {match.group(2)}")

    return classes


def find_keep_classes(source_root: Path) -> set[str]:
    declarations: set[str] = set()
    package_pattern = re.compile(r"^\s*package\s+([\w.]+)", re.MULTILINE)
    type_pattern = re.compile(
        r"(?:(?:@[\w.]+(?:\([^)]*\))?)\s*)*"
        r"(?:(?:public|internal|private|protected|abstract|open|final|data|sealed|value|expect|actual|static)\s+)*"
        r"(?:annotation\s+)?(?:data\s+)?(?:class|object|interface|enum\s+class)\s+([A-Za-z_$][\w$]*)"
    )

    for source_file in (*source_root.rglob("*.kt"), *source_root.rglob("*.java")):
        text = source_file.read_text(encoding="utf-8")
        package = package_pattern.search(text)
        if package is None:
            continue
        package_name = package.group(1)
        type_stack: list[tuple[int, str]] = []
        previous_declaration_end = 0

        for match in type_pattern.finditer(text):
            depth = text.count("{", 0, match.start()) - text.count("}", 0, match.start())
            while type_stack and type_stack[-1][0] >= depth:
                type_stack.pop()

            name = match.group(1)
            prefix = text[previous_declaration_end:match.start()]
            if "@Keep" in match.group(0) or re.search(r"@Keep\b", prefix):
                qualified = "$".join([*(item[1] for item in type_stack), name])
                declarations.add(f"{package_name}.{qualified}")

            type_stack.append((depth, name))
            previous_declaration_end = match.end()

    return declarations


def check_mapping(
    mapping_text: str,
    contract: dict[str, Any],
    source_keep_classes: set[str] | None = None,
) -> list[str]:
    classes = parse_mapping(mapping_text)
    errors: list[str] = []

    if source_keep_classes is not None:
        contracted_keep_classes = set(contract.get("kept_classes", []))
        for class_name in sorted(source_keep_classes - contracted_keep_classes):
            errors.append(f"@Keep declaration missing from contract: {class_name}")
        for class_name in sorted(contracted_keep_classes - source_keep_classes):
            errors.append(f"@Keep contract has no source annotation: {class_name}")

    for class_name in contract.get("kept_classes", []):
        mapped = classes.get(class_name)
        if mapped is None:
            errors.append(f"@Keep class is missing from mapping: {class_name}")
        elif mapped[0] != class_name:
            errors.append(f"@Keep class was renamed: {class_name} -> {mapped[0]}")

    for class_name in contract.get("classes", []):
        mapped = classes.get(class_name)
        if mapped is None:
            errors.append(f"Required class is missing from mapping: {class_name}")
        elif mapped[0] != class_name:
            errors.append(f"Required class was renamed: {class_name} -> {mapped[0]}")

    for field in contract.get("fields", []):
        class_name = field["class"]
        field_name = field["name"]
        mapped = classes.get(class_name)
        if mapped is None:
            errors.append(f"Cannot verify field {class_name}.{field_name}: class missing")
            continue
        renamed = next(
            (entry.split(" -> ", 1)[1] for entry in mapped[1]
             if entry.startswith(f"{field_name} -> ")),
            None,
        )
        # A preserved class name (full -keep) implies preserved member names, and
        # R8 then omits unchanged identities from mapping.txt. Only report when a
        # rename is actually observed, or when a renamed class drops the field.
        if renamed is not None and renamed != field_name:
            errors.append(
                f"Required field was renamed: {class_name}.{field_name} -> {renamed}"
            )
        elif renamed is None and mapped[0] != class_name:
            errors.append(
                f"Required field is missing from mapping: {class_name}.{field_name}"
            )

    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mapping", type=Path, help="R8 mapping.txt generated by release build")
    parser.add_argument(
        "--source-root", type=Path, default=Path(__file__).parents[2] / "app/src/main",
        help="Source tree used to verify @Keep inventory completeness",
    )
    parser.add_argument(
        "--contract", type=Path, default=DEFAULT_CONTRACT,
        help="JSON reflection contract (defaults to the repository contract file)",
    )
    args = parser.parse_args()

    if not args.mapping.is_file():
        print(f"R8 mapping file not found: {args.mapping}", file=sys.stderr)
        return 2
    if not args.contract.is_file():
        print(f"R8 reflection contract not found: {args.contract}", file=sys.stderr)
        return 2

    if not args.source_root.is_dir():
        print(f"Source root not found: {args.source_root}", file=sys.stderr)
        return 2

    contract = json.loads(args.contract.read_text(encoding="utf-8"))
    source_keep_classes = find_keep_classes(args.source_root)
    errors = check_mapping(
        args.mapping.read_text(encoding="utf-8"), contract, source_keep_classes
    )
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1

    print(f"R8 mapping satisfies reflection contract: {args.mapping}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
