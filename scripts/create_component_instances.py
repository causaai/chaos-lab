#!/usr/bin/env python3

"""Create or clean up Chaos Lab component instances on OpenShift.

The script prompts for a component and instance count when arguments are not
provided, then rewrites the selected manifest so each instance gets unique
resource names and labels before applying or deleting it with `oc`.
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[1]


@dataclass(frozen=True)
class ComponentSpec:
    key: str
    manifest_path: Path
    base_name: str
    has_service: bool = False


COMPONENTS = {
    "heap-oom": ComponentSpec(
        key="heap-oom",
        manifest_path=ROOT / "heap-oom" / "manifests" / "deploy.yaml",
        base_name="heap-oom",
        has_service=True,
    ),
    "heap-oom-prom": ComponentSpec(
        key="heap-oom-prom",
        manifest_path=ROOT / "heap-oom-prom" / "manifests" / "deploy.yaml",
        base_name="heap-oom-prom",
    ),
    "system-oom": ComponentSpec(
        key="system-oom",
        manifest_path=ROOT / "system-oom" / "manifests" / "deploy.yaml",
        base_name="system-oom",
        has_service=True,
    ),
    "perf-impact": ComponentSpec(
        key="perf-impact",
        manifest_path=ROOT / "perf-impact" / "manifests" / "deploy.yaml",
        base_name="auth-cache",
        has_service=True,
    ),
}


def get_default_namespace() -> str:
    env_namespace = os.environ.get("OC_NAMESPACE") or os.environ.get("NAMESPACE")
    if env_namespace:
        return env_namespace.strip()

    try:
        result = subprocess.run(
            ["oc", "project", "-q"],
            check=True,
            capture_output=True,
            text=True,
        )
    except (FileNotFoundError, subprocess.CalledProcessError):
        return "default"

    namespace = result.stdout.strip()
    return namespace or "default"


def prompt_choice(options: Iterable[str]) -> str:
    option_list = list(options)
    print("Available components:")
    for index, option in enumerate(option_list, start=1):
        print(f"  {index}. {option}")

    while True:
        raw_choice = input("Select a component: ").strip()
        if raw_choice.isdigit():
            selected_index = int(raw_choice)
            if 1 <= selected_index <= len(option_list):
                return option_list[selected_index - 1]
        if raw_choice in option_list:
            return raw_choice
        print("Enter one of the numbered options or a component name.")


def prompt_positive_int(prompt: str, default: int | None = None) -> int:
    while True:
        suffix = f" [{default}]" if default is not None else ""
        raw_value = input(f"{prompt}{suffix}: ").strip()
        if not raw_value and default is not None:
            return default
        if raw_value.isdigit() and int(raw_value) > 0:
            return int(raw_value)
        print("Enter a positive whole number.")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Create or clean up Chaos Lab component instances on OpenShift."
    )
    parser.add_argument(
        "component",
        nargs="?",
        choices=sorted(COMPONENTS),
        help="Component to deploy. If omitted, an interactive prompt is shown.",
    )
    parser.add_argument(
        "--count",
        type=int,
        help="Number of instances to create. If omitted, an interactive prompt is shown.",
    )
    parser.add_argument(
        "--namespace",
        help="OpenShift namespace to target. Defaults to the current oc project.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print the generated manifests/actions instead of applying them.",
    )
    parser.add_argument(
        "--cleanup",
        action="store_true",
        help="Delete instances instead of creating them.",
    )
    parser.add_argument(
        "--no-create-namespace",
        action="store_true",
        help="Do not create the namespace automatically if it does not exist.",
    )
    return parser.parse_args()


def replace_line_value(document: str, field: str, old_value: str, new_value: str) -> str:
    pattern = re.compile(
        rf"^(\s*{re.escape(field)}:\s*){re.escape(old_value)}\s*$",
        re.MULTILINE,
    )
    return pattern.sub(lambda match: f"{match.group(1)}{new_value}", document)


def transform_manifest(
    manifest_text: str,
    component: ComponentSpec,
    instance_name: str,
    namespace: str,
) -> str:
    documents: list[str] = []
    service_name = f"{instance_name}-service"

    for raw_document in re.split(r"(?m)^\s*---\s*$", manifest_text):
        document = raw_document.strip()
        if not document:
            continue
        if re.search(r"(?m)^\s*kind:\s*Namespace\s*$", document):
            continue

        transformed = replace_line_value(document, "name", component.base_name, instance_name)
        transformed = replace_line_value(transformed, "app", component.base_name, instance_name)

        if component.has_service:
            transformed = replace_line_value(
                transformed,
                "name",
                f"{component.base_name}-service",
                service_name,
            )

        transformed = re.sub(
            r"^(\s*namespace:\s*).*$",
            lambda match: f"{match.group(1)}{namespace}",
            transformed,
            flags=re.MULTILINE,
        )
        documents.append(transformed)

    return "\n---\n".join(documents) + "\n"


def apply_manifest(namespace: str, manifest_text: str) -> None:
    try:
        result = subprocess.run(
            ["oc", "apply", "-n", namespace, "-f", "-"],
            input=manifest_text,
            text=True,
            capture_output=True,
            check=False,
        )
    except FileNotFoundError as exc:
        raise RuntimeError("The `oc` command is required but was not found in PATH.") from exc

    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip() or "oc apply failed")

    output = result.stdout.strip()
    if output:
        print(output)


def delete_manifest(namespace: str, manifest_text: str) -> None:
    try:
        result = subprocess.run(
            ["oc", "delete", "-n", namespace, "-f", "-", "--ignore-not-found=true"],
            input=manifest_text,
            text=True,
            capture_output=True,
            check=False,
        )
    except FileNotFoundError as exc:
        raise RuntimeError("The `oc` command is required but was not found in PATH.") from exc

    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip() or "oc delete failed")

    output = result.stdout.strip()
    if output:
        print(output)


def wait_for_pods_terminated(namespace: str, app_label: str, timeout_seconds: int = 180) -> None:
    deadline = time.time() + timeout_seconds
    announced_wait = False

    while True:
        try:
            result = subprocess.run(
                [
                    "oc",
                    "get",
                    "pods",
                    "-n",
                    namespace,
                    "-l",
                    f"app={app_label}",
                    "--no-headers",
                ],
                capture_output=True,
                text=True,
                check=False,
            )
        except FileNotFoundError as exc:
            raise RuntimeError("The `oc` command is required but was not found in PATH.") from exc

        if result.returncode != 0:
            raise RuntimeError(result.stderr.strip() or "Failed to check pod termination status")

        remaining = [line for line in result.stdout.splitlines() if line.strip()]
        if not remaining:
            print(f"Pods terminated for app={app_label}")
            return

        if time.time() >= deadline:
            raise RuntimeError(
                f"Timed out waiting for pods to terminate for app={app_label}. "
                f"Still present: {', '.join(line.split()[0] for line in remaining)}"
            )

        if not announced_wait:
            print(f"Waiting for pods to terminate for app={app_label}...")
            announced_wait = True

        time.sleep(2)


def ensure_namespace_exists(namespace: str, create_if_missing: bool) -> None:
    try:
        exists_check = subprocess.run(
            ["oc", "get", "namespace", namespace],
            capture_output=True,
            text=True,
            check=False,
        )
    except FileNotFoundError as exc:
        raise RuntimeError("The `oc` command is required but was not found in PATH.") from exc

    if exists_check.returncode == 0:
        return

    if not create_if_missing:
        raise RuntimeError(
            f"Namespace '{namespace}' does not exist. "
            "Create it first or omit --no-create-namespace."
        )

    create_result = subprocess.run(
        ["oc", "create", "namespace", namespace],
        capture_output=True,
        text=True,
        check=False,
    )
    if create_result.returncode != 0 and "AlreadyExists" not in create_result.stderr:
        raise RuntimeError(
            create_result.stderr.strip()
            or create_result.stdout.strip()
            or f"Failed to create namespace '{namespace}'"
        )

    created_output = create_result.stdout.strip()
    if created_output:
        print(created_output)


def main() -> int:
    args = parse_args()

    component_key = args.component or prompt_choice(sorted(COMPONENTS))
    component = COMPONENTS[component_key]

    count = args.count if args.count is not None else prompt_positive_int(
        "How many instances should be created?",
        default=1,
    )
    if count <= 0:
        print("Instance count must be greater than zero.", file=sys.stderr)
        return 2

    namespace = args.namespace or get_default_namespace()

    if not component.manifest_path.exists():
        print(f"Manifest not found: {component.manifest_path}", file=sys.stderr)
        return 1

    manifest_text = component.manifest_path.read_text(encoding="utf-8")

    action = "cleanup" if args.cleanup else "create"

    print(f"Action: {action}")
    print(f"Component: {component.key}")
    print(f"Namespace: {namespace}")
    print(f"Instances: {count}")

    if not args.dry_run and not args.cleanup:
        ensure_namespace_exists(namespace, create_if_missing=not args.no_create_namespace)

    for index in range(1, count + 1):
        instance_name = f"{component.base_name}-{index}"
        rendered_manifest = transform_manifest(
            manifest_text,
            component=component,
            instance_name=instance_name,
            namespace=namespace,
        )

        print(f"\n--- Instance {index}/{count}: {instance_name} ---")
        if args.dry_run:
            print(rendered_manifest, end="")
            continue

        if args.cleanup:
            delete_manifest(namespace, rendered_manifest)
            wait_for_pods_terminated(namespace, app_label=instance_name)
        else:
            apply_manifest(namespace, rendered_manifest)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())