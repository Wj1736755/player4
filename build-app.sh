#!/bin/bash
# Build script with automatic versioning
# This script updates VERSION_NAME to current date and increments VERSION_CODE by 1 before building

set -e

BUILD_TYPE="${1:-debug}"

echo "================================"
echo "   Music Player Build Script"
echo "================================"
echo ""

# Check if gradle.properties exists
if [ ! -f "gradle.properties" ]; then
    echo "ERROR: gradle.properties not found!"
    exit 1
fi

# Read current VERSION_CODE
CURRENT_VERSION_CODE=$(grep "^VERSION_CODE=" gradle.properties | cut -d'=' -f2)

# Generate new version - increment VERSION_CODE by 1
NEW_DATE=$(date +%Y.%m.%d)
NEW_VERSION_CODE=$((CURRENT_VERSION_CODE + 1))

echo "Current VERSION_CODE: $CURRENT_VERSION_CODE"
echo "New VERSION_NAME:     $NEW_DATE"
echo "New VERSION_CODE:     $NEW_VERSION_CODE (+1 increment)"
echo ""

# Update gradle.properties
sed -i.bak "s/^VERSION_NAME=.*/VERSION_NAME=$NEW_DATE/" gradle.properties
sed -i.bak "s/^VERSION_CODE=.*/VERSION_CODE=$NEW_VERSION_CODE/" gradle.properties
rm gradle.properties.bak

echo "✓ gradle.properties updated!"
echo ""

# Determine build command
if [ "$BUILD_TYPE" = "release" ]; then
    BUILD_COMMAND="assembleRelease"
else
    BUILD_COMMAND="assembleDebug"
fi

echo "Building APK ($BUILD_TYPE)..."
echo "Running: ./gradlew $BUILD_COMMAND"
echo ""

# Run Gradle build
./gradlew $BUILD_COMMAND

if [ $? -eq 0 ]; then
    echo ""
    echo "================================"
    echo "   BUILD SUCCESSFUL!"
    echo "================================"
    echo ""
    FULL_VERSION="$NEW_DATE.$NEW_VERSION_CODE"
    echo "Full Version: $FULL_VERSION"
    echo "  VERSION_NAME: $NEW_DATE"
    echo "  VERSION_CODE: $NEW_VERSION_CODE"
    
    # Show output location
    if [ "$BUILD_TYPE" = "release" ]; then
        APK_PATH="app/build/outputs/apk/release/musicplayer-$FULL_VERSION-release.apk"
    else
        APK_PATH="app/build/outputs/apk/debug/musicplayer-$FULL_VERSION-debug.apk"
    fi
    
    if [ -f "$APK_PATH" ]; then
        echo "APK location: $APK_PATH"
    fi
else
    echo ""
    echo "================================"
    echo "   BUILD FAILED!"
    echo "================================"
    exit 1
fi

