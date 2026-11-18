#!/usr/bin/env bash
# =============================================================================
# A2D cloud - build a deployable tarball of the backend source.
#
# Usage: ./build.sh [--output <path>] [--source <path>]
#
# Options:
#   --output <path>   Output tarball path (default: ./a2d-cloud-<timestamp>.tar.gz)
#   --source <path>   Source directory containing a2d-cloud/ (default: ../../)
# =============================================================================

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

usage() {
    cat <<'EOF'
Usage: ./build.sh [--output <path>] [--source <path>]

Builds a deployable tarball of the a2d-cloud backend source.
EOF
}

OUTPUT=""
SOURCE=""

while [ $# -gt 0 ]; do
    case "$1" in
        -h|--help)
            usage
            exit 0
            ;;
        --output)
            OUTPUT="${2:-}"
            shift 2
            ;;
        --source)
            SOURCE="${2:-}"
            shift 2
            ;;
        --)
            shift
            break
            ;;
        *)
            usage >&2
            echo "error: unknown argument: $1" >&2
            exit 1
            ;;
    esac
done

# Defaults
SOURCE="${SOURCE:-$(realpath -m "$SCRIPT_DIR/../../")}"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
OUTPUT="${OUTPUT:-$SCRIPT_DIR/a2d-cloud-$TIMESTAMP.tar.gz}"

SRC_DIR="$SOURCE/a2d-cloud"

if [ ! -f "$SRC_DIR/pyproject.toml" ]; then
    echo "error: backend source not found at $SRC_DIR/pyproject.toml" >&2
    exit 1
fi

# Create tarball from the source directory
echo "Building tarball from $SRC_DIR"
tar -czf "$OUTPUT" -C "$SOURCE" a2d-cloud

echo "Created: $OUTPUT"
echo "Size: $(du -h "$OUTPUT" | cut -f1)"