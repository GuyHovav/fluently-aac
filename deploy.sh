#!/bin/bash

# =============================================================================
# FluentlyAAC Deploy Script
# =============================================================================
# This script builds the APK and deploys it to an Android virtual device.
# It supports both Docker builds and local Gradle builds.
#
# Usage:
#   ./deploy.sh [options]
#
# Options:
#   --docker        - Build using Docker (default)
#   --local         - Build using local Gradle
#   --release       - Build release APK (default: debug)
#   --install-only  - Skip build, just install existing APK
#   --device <id>   - Deploy to specific device (default: first available)
#   --launch        - Launch the app after installation
#
# Examples:
#   ./deploy.sh                          # Docker build + deploy debug to first device
#   ./deploy.sh --local --launch         # Local build + deploy + launch
#   ./deploy.sh --release --device emulator-5554
#   ./deploy.sh --install-only --launch  # Just install and launch
# =============================================================================

set -e  # Exit on error

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Default values
BUILD_METHOD="docker"
BUILD_TYPE="debug"
INSTALL_ONLY=false
TARGET_DEVICE=""
LAUNCH_APP=false
PACKAGE_NAME="com.example.myaac"

# Parse options
while [[ $# -gt 0 ]]; do
    case $1 in
        --docker)
            BUILD_METHOD="docker"
            shift
            ;;
        --local)
            BUILD_METHOD="local"
            shift
            ;;
        --release)
            BUILD_TYPE="release"
            shift
            ;;
        --install-only)
            INSTALL_ONLY=true
            shift
            ;;
        --device)
            TARGET_DEVICE="$2"
            shift 2
            ;;
        --launch)
            LAUNCH_APP=true
            shift
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            exit 1
            ;;
    esac
done

# Function to print colored messages
print_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_step() {
    echo -e "${CYAN}➜${NC} $1"
}

# Print header
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}     FluentlyAAC Deploy System${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
echo ""

# Check if ADB is installed
if ! command -v adb &> /dev/null; then
    print_error "ADB is not installed. Please install Android SDK Platform Tools."
    exit 1
fi

print_success "ADB found at $(which adb)"

# Start ADB server if needed
print_step "Starting ADB server..."
adb start-server > /dev/null 2>&1

# Get list of connected devices
DEVICES=$(adb devices | grep -v "List of devices" | grep -v "^$" | grep -E "device$" | cut -f1)
DEVICE_COUNT=$(echo "$DEVICES" | grep -v "^$" | wc -l)

if [ "$DEVICE_COUNT" -eq 0 ]; then
    print_error "No Android devices or emulators detected!"
    echo ""
    print_info "Please start an Android emulator or connect a device."
    print_info "To list available emulators: emulator -list-avds"
    print_info "To start an emulator: emulator -avd <name> &"
    exit 1
fi

print_success "Found $DEVICE_COUNT device(s)"

# Select target device
if [ -n "$TARGET_DEVICE" ]; then
    # User specified a device
    if echo "$DEVICES" | grep -q "$TARGET_DEVICE"; then
        SELECTED_DEVICE="$TARGET_DEVICE"
        print_info "Using specified device: ${GREEN}$SELECTED_DEVICE${NC}"
    else
        print_error "Device '$TARGET_DEVICE' not found!"
        echo ""
        print_info "Available devices:"
        echo "$DEVICES"
        exit 1
    fi
else
    # Use first available device
    SELECTED_DEVICE=$(echo "$DEVICES" | head -n1)
    if [ "$DEVICE_COUNT" -gt 1 ]; then
        print_warning "Multiple devices found. Using: $SELECTED_DEVICE"
        print_info "Available devices:"
        echo "$DEVICES" | sed 's/^/  /'
        echo ""
        print_info "Use --device <id> to select a specific device"
    else
        print_info "Using device: ${GREEN}$SELECTED_DEVICE${NC}"
    fi
fi

echo ""

# Build APK if not install-only
if [ "$INSTALL_ONLY" = false ]; then
    print_step "Building $BUILD_TYPE APK..."
    echo ""
    
    if [ "$BUILD_METHOD" = "docker" ]; then
        print_info "Build method: ${CYAN}Docker${NC}"
        if [ "$BUILD_TYPE" = "debug" ]; then
            ./docker-build.sh build-debug
        else
            ./docker-build.sh build-release
        fi
    else
        print_info "Build method: ${CYAN}Local Gradle${NC}"
        if [ "$BUILD_TYPE" = "debug" ]; then
            ./gradlew assembleDebug
        else
            ./gradlew assembleRelease
        fi
    fi
    
    echo ""
    print_success "Build completed!"
else
    print_info "Skipping build (install-only mode)"
fi

# Determine APK path
if [ "$BUILD_TYPE" = "debug" ]; then
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
else
    APK_PATH="app/build/outputs/apk/release/app-release.apk"
fi

# Check if APK exists
if [ ! -f "$APK_PATH" ]; then
    print_error "APK not found at: $APK_PATH"
    exit 1
fi

print_info "APK location: $APK_PATH"
APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
print_info "APK size: $APK_SIZE"

echo ""

# Uninstall previous version (if exists)
print_step "Checking for previous installation..."
if adb -s "$SELECTED_DEVICE" shell pm list packages | grep -q "$PACKAGE_NAME"; then
    print_warning "Uninstalling previous version..."
    adb -s "$SELECTED_DEVICE" uninstall "$PACKAGE_NAME" > /dev/null 2>&1 || true
    print_success "Previous version uninstalled"
else
    print_info "No previous installation found"
fi

# Install APK
print_step "Installing APK to device..."
if adb -s "$SELECTED_DEVICE" install -r "$APK_PATH"; then
    print_success "APK installed successfully!"
else
    print_error "Installation failed!"
    exit 1
fi

echo ""

# Launch app if requested
if [ "$LAUNCH_APP" = true ]; then
    print_step "Launching application..."
    MAIN_ACTIVITY="$PACKAGE_NAME/.MainActivity"
    
    if adb -s "$SELECTED_DEVICE" shell am start -n "$MAIN_ACTIVITY" > /dev/null 2>&1; then
        print_success "Application launched!"
    else
        print_warning "Launch command sent (check device)"
    fi
    
    echo ""
    
    # Show logcat for the app
    print_info "To view app logs, run:"
    echo -e "  ${CYAN}adb -s $SELECTED_DEVICE logcat | grep $PACKAGE_NAME${NC}"
fi

echo ""
print_success "Deployment complete!"

# Show summary
echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}     Summary${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
echo -e "  Build method:  ${CYAN}$BUILD_METHOD${NC}"
echo -e "  Build type:    ${CYAN}$BUILD_TYPE${NC}"
echo -e "  Device:        ${CYAN}$SELECTED_DEVICE${NC}"
echo -e "  APK size:      ${CYAN}$APK_SIZE${NC}"
if [ "$LAUNCH_APP" = true ]; then
    echo -e "  Status:        ${GREEN}Installed and launched ✓${NC}"
else
    echo -e "  Status:        ${GREEN}Installed ✓${NC}"
fi
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"

# Quick tips
echo ""
print_info "Quick tips:"
echo "  • To launch manually: adb -s $SELECTED_DEVICE shell am start -n $PACKAGE_NAME/.MainActivity"
echo "  • To view logs: adb -s $SELECTED_DEVICE logcat | grep $PACKAGE_NAME"
echo "  • To uninstall: adb -s $SELECTED_DEVICE uninstall $PACKAGE_NAME"
