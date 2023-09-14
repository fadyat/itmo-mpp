#!/bin/bash

# Check for the correct number of command-line arguments
if [ $# -ne 1 ]; then
  echo "Usage: $0 <source_subfolder>"
  exit 1
fi

# Extract the source subfolder and construct the backup directory
source_subfolder="$1"
backup_directory="backups/$1"

# Check if the source subfolder exists
if [ -d "$source_subfolder" ]; then
  # Check if the backup directory already exists
  if [ -d "$backup_directory" ]; then
    echo "Backup directory already exists. Deleting old backup..."
    rm -rf "$backup_directory"
  fi

  # Create the backup directory
  mkdir -p "$backup_directory"

  # Copy the .git directory to the backup directory
  cp -r "$source_subfolder/.git" "$backup_directory"

  echo "Backup completed successfully."
else
  echo "Source subfolder not found. Backup aborted."
fi

