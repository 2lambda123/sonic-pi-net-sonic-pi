#!/bin/bash
set -e # Quit script on error
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
WORKING_DIR="$(pwd)"

QT_INSTALL_LOCATION=/usr/lib/qt6 "${SCRIPT_DIR}"/linux-build-gui.sh "$@"

# Restore working directory as it was prior to this script running...
cd "${WORKING_DIR}"
