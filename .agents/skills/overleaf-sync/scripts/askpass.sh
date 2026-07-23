#!/bin/bash
# Print the token from the token.txt file in the workspace root
TOKEN_FILE="$(dirname "$0")/../../../../token.txt"
if [ -f "$TOKEN_FILE" ]; then
  cat "$TOKEN_FILE"
else
  # If running from another directory, try absolute path
  cat "/home/eitsei/dippa/token.txt"
fi
