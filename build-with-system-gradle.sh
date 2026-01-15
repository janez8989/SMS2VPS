#!/bin/bash

# Temporary build script using system Gradle
# This is a workaround while we resolve the wrapper issues

echo "Building project with system Gradle..."
echo "Note: This is a temporary solution while we fix the wrapper issues."

# Check if system Gradle is available
if ! command -v gradle &> /dev/null; then
    echo "Error: System Gradle not found"
    exit 1
fi

# Check system Gradle version
GRADLE_VERSION=$(gradle --version | grep "Gradle" | head -1 | awk '{print $2}')
echo "System Gradle version: $GRADLE_VERSION"

# Check if version is compatible (we need at least 8.0 for AGP 8.3.2)
if [[ "$GRADLE_VERSION" < "8.0" ]]; then
    echo "Warning: System Gradle version $GRADLE_VERSION may not be compatible with Android Gradle Plugin 8.3.2"
    echo "This build may fail due to version incompatibility"
    echo ""
fi

# Try to build the project
echo "Attempting to build project..."
gradle build

echo ""
echo "Build completed. Check the output above for any errors."
echo "To resolve the wrapper issues permanently, you'll need to:"
echo "1. Download a compatible gradle-wrapper.jar"
echo "2. Ensure network connectivity to download Gradle distributions"
echo "3. Or use a compatible system Gradle version (8.0+)"

