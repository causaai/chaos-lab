# Scripts

## create_component_instances.py

Creates or cleans up Chaos Lab component instances on OpenShift.

### What it does

- Prompts for component and count when arguments are omitted.
- Creates per-instance resource names and labels.
- Applies manifests with `oc`.
- Supports cleanup mode that deletes generated resources.
- In cleanup mode, waits until matching pods are fully terminated.

### Supported components

- `heap-oom`
- `heap-oom-prom`
- `system-oom`
- `perf-impact`

### Requirements

- Python 3
- `oc` installed and logged into a cluster

### Usage

Interactive:

```bash
python3 scripts/create_component_instances.py
```

Create instances:

```bash
python3 scripts/create_component_instances.py heap-oom --count 3
python3 scripts/create_component_instances.py system-oom --count 2 --namespace chaos-test
```

Dry run:

```bash
python3 scripts/create_component_instances.py perf-impact --count 2 --dry-run
```

Cleanup instances:

```bash
python3 scripts/create_component_instances.py heap-oom --count 3 --namespace test --cleanup
```

Cleanup dry run:

```bash
python3 scripts/create_component_instances.py heap-oom --count 3 --namespace test --cleanup --dry-run
```

Namespace behavior:

- By default, create mode auto-creates namespace if missing.
- Use `--no-create-namespace` to require namespace to already exist.

```bash
python3 scripts/create_component_instances.py heap-oom --count 2 --namespace test --no-create-namespace
```