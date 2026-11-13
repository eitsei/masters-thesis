#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [ ! -d "venv" ]; then
    echo "Creating Python virtual environment at $SCRIPT_DIR/venv..."
    python3 -m venv venv
fi

echo "Activating virtual environment..."
source venv/bin/activate

echo "Upgrading pip and wheel..."
pip install --upgrade pip wheel

echo "Installing requirements from requirements.txt..."
pip install -r requirements.txt

echo "Setup complete! Activate environment with: source venv/bin/activate"
