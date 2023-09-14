#!/bin/bash

# Check for the correct number of command-line arguments
if [ $# -ne 1 ]; then
  echo "Usage: $0 <directory>"
  exit 1
fi

# Extract the directory from the command-line argument
directory="$1"

# Check if the directory exists
if [ -d "$directory" ]; then
  # Check if the .git directory exists
  if [ -d "$directory/.git" ]; then
    # Remove the .git directory
    rm -rf "$directory/.git"
    echo "Removed .git directory from $directory."
  else
    echo ".git directory not found in $directory."
  fi
else
  echo "Directory not found: $directory"
fi

