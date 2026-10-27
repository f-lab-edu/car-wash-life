#!/usr/bin/env python3
"""Ask Codex to review ERDs when the persistent Java model changes."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[2]
STATE_PATH = Path("docs/erd/model-state.json")
POLICY_PATH = ".codex/hooks/erd-update.md"
TOKEN = re.compile(
    r'//[^\n]*|/\*[\s\S]*?\*/|"""[\s\S]*?"""|'
    r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|[A-Za-z_$][\w$]*|\S'
)
MAPPING_ANNOTATIONS = set("""
Entity Embeddable MappedSuperclass Table SecondaryTable SecondaryTables
Access Inheritance DiscriminatorColumn DiscriminatorValue PrimaryKeyJoinColumn
Id EmbeddedId IdClass GeneratedValue SequenceGenerator TableGenerator
Basic Column Version Enumerated Temporal Lob Convert Converts Embedded
AttributeOverride AttributeOverrides AssociationOverride AssociationOverrides
ManyToOne OneToOne OneToMany ManyToMany JoinColumn JoinColumns JoinTable
ElementCollection CollectionTable MapKey MapKeyColumn MapKeyJoinColumn
OrderBy OrderColumn Transient Type JdbcTypeCode
""".split())
MODEL_ANNOTATIONS = {"Entity", "Embeddable", "MappedSuperclass"}


def skip_group(tokens, index, opener, closer):
    depth = 1
    index += 1
    while index < len(tokens) and depth:
        if tokens[index] == opener:
            depth += 1
        elif tokens[index] == closer:
            depth -= 1
        index += 1
    if depth:
        raise ValueError("Java declaration is incomplete")
    return index


def declarations(tokens):
    """Separate annotations from a declaration; retain only mapping annotations."""
    annotations, plain = [], []
    index = 0
    while index < len(tokens):
        if tokens[index] != "@":
            plain.append(tokens[index])
            index += 1
            continue
        start = index
        index += 1
        parts = [tokens[index]]
        index += 1
        while index + 1 < len(tokens) and tokens[index] == ".":
            parts.append(tokens[index + 1])
            index += 2
        if index < len(tokens) and tokens[index] == "(":
            index = skip_group(tokens, index, "(", ")")
        if parts[-1] in MAPPING_ANNOTATIONS:
            annotations.append((parts[-1], " ".join(tokens[start:index])))
    return annotations, plain


def model_signature(source):
    tokens = [m.group() for m in TOKEN.finditer(source)
              if not m.group().startswith(("//", "/*"))]
    for index, token in enumerate(tokens[:-1]):
        if token not in {"class", "record"} or not re.fullmatch(r"[A-Za-z_$][\w$]*", tokens[index + 1]):
            continue
        if index and tokens[index - 1] == ".":
            continue
        start = max((i + 1 for i, t in enumerate(tokens[:index]) if t == ";"), default=0)
        opening = index
        while opening < len(tokens) and tokens[opening] != "{":
            if tokens[opening] == "(":
                opening = skip_group(tokens, opening, "(", ")")
            else:
                opening += 1
        annotations, header = declarations(tokens[start:opening])
        if not any(name in MODEL_ANNOTATIONS for name, _ in annotations):
            continue
        fields, chunk, parens = [], [], 0
        cursor = opening + 1
        while cursor < len(tokens):
            current = tokens[cursor]
            if current == "}" and parens == 0:
                break
            if current == "{" and parens == 0:
                # Skip method, constructor, nested-class and initializer bodies.
                cursor = skip_group(tokens, cursor, "{", "}")
                if "=" not in chunk:
                    chunk = []
                continue
            if current == ";" and parens == 0:
                field_annotations, field = declarations(chunk)
                field = field[:field.index("=")] if "=" in field else field
                if (len(field) >= 2 and "(" not in field
                        and not {"static", "transient"}.intersection(field)
                        and not any(name == "Transient" for name, _ in field_annotations)):
                    fields.append(([value for _, value in field_annotations], " ".join(field)))
                chunk = []
            else:
                chunk.append(current)
                if current == "(":
                    parens += 1
                elif current == ")":
                    parens -= 1
            cursor += 1
        if cursor >= len(tokens):
            raise ValueError("Java model body is incomplete")
        return {"annotations": [value for _, value in annotations],
                "declaration": " ".join(header), "fields": fields}
    return None


def model_state(root):
    models = {}
    for path in sorted((root / "src/main/java").rglob("*.java")):
        signature = model_signature(path.read_text(encoding="utf-8"))
        if signature is not None:
            encoded = json.dumps(signature, sort_keys=True, ensure_ascii=False).encode("utf-8")
            models[path.relative_to(root).as_posix()] = hashlib.sha256(encoded).hexdigest()
    return {"version": 1, "models": models}


def record_state(root):
    state = model_state(root)
    (root / STATE_PATH).write_text(
        json.dumps(state, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    return state


def stop_result(payload, root):
    if payload.get("hook_event_name") != "Stop" or payload.get("stop_hook_active") is True:
        return {}
    previous = json.loads((root / STATE_PATH).read_text(encoding="utf-8"))
    if previous.get("version") != 1 or not isinstance(previous.get("models"), dict):
        raise ValueError("Unsupported ERD model state")
    current = model_state(root)
    changed = sorted(path for path in set(previous["models"]) | set(current["models"])
                     if previous["models"].get(path) != current["models"].get(path))
    if not changed:
        return {}
    paths = json.dumps(changed[:8], ensure_ascii=False)
    return {
        "decision": "block",
        "reason": (
            "엔티티의 영속 구조가 ERD 확인 기준과 달라졌습니다. "
            "변경 경로(데이터): " + paths + ". "
            + POLICY_PATH + "를 읽고 실제 관계와 필드에 맞춰 README 요약 ERD, "
            "상세 ERD와 간결한 Wiki 원고의 갱신 필요 여부를 확인하세요. "
            "필요한 수정과 검증을 마친 뒤 "
            "python3 .codex/hooks/erd_sync.py --record 로 확인 기준을 갱신하세요. "
            "그림 변경이 불필요하면 그 이유를 보고하세요. "
            "사용자가 중단·대기·구현 보류를 지시했다면 이를 우선하고, "
            "별도 지시 없는 Wiki 게시와 푸시는 하지 마세요."
        ),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--record", action="store_true", help="Record the model after reviewing ERDs")
    args = parser.parse_args()
    if args.record:
        state = record_state(ROOT)
        print("ERD 확인 기준 갱신: {}개 영속 모델".format(len(state["models"])))
        return
    try:
        payload = json.load(sys.stdin)
        if not isinstance(payload, dict):
            raise ValueError("Hook input must be a JSON object")
        result = stop_result(payload, ROOT)
    except (OSError, ValueError, IndexError) as error:
        result = {"systemMessage": "ERD 자동 검사를 완료하지 못했습니다. "
                  + POLICY_PATH + "에 따라 확인하세요. (" + type(error).__name__ + ")"}
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
