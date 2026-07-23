#!/bin/bash
set -e

# Get directories
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_DIR="$(cd "$SCRIPT_DIR/../../../.." && pwd)"

# Verify token.txt exists
TOKEN_FILE="$WORKSPACE_DIR/token.txt"
if [ ! -f "$TOKEN_FILE" ]; then
  echo "Error: token.txt not found at $TOKEN_FILE" >&2
  exit 1
fi

# Export GIT_ASKPASS to use our askpass script
export GIT_ASKPASS="$SCRIPT_DIR/askpass.sh"
# Disable SSH/terminal prompt for password
export GIT_TERMINAL_PROMPT=0

# Ensure remote 'overleaf' exists
if ! git remote | grep -q "^overleaf$"; then
  echo "Adding overleaf remote..."
  git remote add overleaf https://git@git.overleaf.com/69e3841827002aeac06eb2e8
fi

ACTION="$1"

if [ "$ACTION" = "push" ]; then
  echo "Staging any untracked/modified files under thesis/..."
  git add thesis/
  
  if ! git diff-index --quiet HEAD -- thesis/; then
    echo "Committing local changes to thesis/..."
    git commit -m "thesis: automatic sync before push"
  else
    echo "No local changes to commit."
  fi
  
  echo "Pushing subtree to Overleaf..."
  git subtree push --prefix=thesis overleaf master
  echo "Push completed successfully!"

elif [ "$ACTION" = "pull" ]; then
  # Check if there are uncommitted changes in thesis/
  if ! git diff-index --quiet HEAD -- thesis/; then
    echo "Error: You have uncommitted changes in thesis/. Please commit or stash them first." >&2
    exit 1
  fi
  
  echo "Pulling subtree from Overleaf..."
  git subtree pull --prefix=thesis overleaf master --squash
  echo "Pull completed successfully!"

else
  echo "Usage: $0 {push|pull}" >&2
  exit 1
fi
