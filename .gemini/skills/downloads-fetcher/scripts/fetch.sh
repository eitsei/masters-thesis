#!/bin/bash

DOWNLOADS_DIR="/mnt/c/Users/ajasu/Downloads"

list_files() {
    echo "Listing recent files in Windows Downloads:"
    # List files sorted by time, newest first, showing top 20
    ls -tp "$DOWNLOADS_DIR" | grep -v / | head -n 20
}

fetch_file() {
    FILE_NAME="$1"
    TARGET_DIR="$2"

    if [ -z "$FILE_NAME" ] || [ -z "$TARGET_DIR" ]; then
        echo "Error: Usage: fetch_file <filename> <target_directory>"
        return 1
    fi

    SOURCE_PATH="$DOWNLOADS_DIR/$FILE_NAME"
    
    if [ ! -f "$SOURCE_PATH" ]; then
        echo "Error: File '$FILE_NAME' not found in Downloads."
        return 1
    fi

    mkdir -p "$TARGET_DIR"
    cp "$SOURCE_PATH" "$TARGET_DIR/"
    echo "Success: Copied '$FILE_NAME' to '$TARGET_DIR/'"
}

case "$1" in
    list)
        list_files
        ;;
    fetch)
        fetch_file "$2" "$3"
        ;;
    *)
        echo "Usage: $0 {list|fetch <filename> <target_dir>}"
        exit 1
        ;;
esac
