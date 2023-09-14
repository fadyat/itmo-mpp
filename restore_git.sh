#!/bin/bash

# Check for the correct number of command-line arguments
if [ $# -ne 1 ]; then
  echo "Usage: $0 <source_subfolder>"
  exit 1
fi

# Extract the source subfolder from the command-line argument
source_subfolder="$1"

# Specify the backup directory path
backup_directory="backups/$source_subfolder"

# Check if the source subfolder exists
if [ -d "$source_subfolder" ]; then
  # Check if the backup directory exists
  if [ -d "$backup_directory" ]; then
    # Copy the .git directory from the backup to the source subfolder
    cp -r "$backup_directory/.git" "$source_subfolder"

    echo "Restored .git directory from backup to $source_subfolder."
  else
    echo "Backup directory not found: $backup_directory"
  fi
else
  echo "Source subfolder not found: $source_subfolder"
fi

