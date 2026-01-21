#!/bin/bash

# =============================================================================
# FluentlyAAC Docker Build Script
# =============================================================================
# This script provides optimized Docker builds with BuildKit support
# for faster compilation times and better caching.
#
# Usage:
#   ./docker-build.sh [command] [options]
#
# Commands:
#   build-debug     - Build debug APK (fastest, recommended for development)
#   build-release   - Build release APK
#   test            - Run unit tests
#   lint            - Run lint checks
#   clean           - Clean build artifacts
#   rebuild         - Force rebuild Docker image (use after dependency changes)
#   shell           - Open a shell in the container
#
# Options:
#   --time          - Show build time
#   --no-cache      - Build without cache (slower, use for troubleshooting)
# =============================================================================

set -e  # Exit on error

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Enable BuildKit for faster builds
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1

# Default values
COMMAND="${1:-build-debug}"
SHOW_TIME=false
NO_CACHE=""

# Parse options
shift || true
while [[ $# -gt 0 ]]; do
    case $1 in
        --time)
            SHOW_TIME=true
            shift
            ;;
        --no-cache)
            NO_CACHE="--no-cache"
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

# Function to show build time
time_command() {
    if [ "$SHOW_TIME" = true ]; then
        /usr/bin/time -f "\n⏱ Build completed in %E (elapsed time)" "$@"
    else
        "$@"
    fi
}

# Print header
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}     FluentlyAAC Docker Build System${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
echo ""

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    print_error "Docker is not installed. Please install Docker first."
    exit 1
fi

# Check if docker-compose is installed
if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    print_error "docker-compose is not installed. Please install docker-compose first."
    exit 1
fi

# Use 'docker compose' if available, otherwise fall back to 'docker-compose'
if docker compose version &> /dev/null; then
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

print_info "BuildKit enabled: ${GREEN}✓${NC}"
print_info "Build mode: ${GREEN}${COMMAND}${NC}"

if [ -n "$NO_CACHE" ]; then
    print_warning "Cache disabled (builds will be slower)"
fi

echo ""

# Execute the appropriate command
case $COMMAND in
    build-debug)
        print_info "Building debug APK..."
        time_command $DOCKER_COMPOSE run --rm build-debug
        print_success "Debug APK built successfully!"
        print_info "Output: app/build/outputs/apk/debug/"
        ;;
    
    build-release)
        print_info "Building release APK..."
        time_command $DOCKER_COMPOSE run --rm build-release
        print_success "Release APK built successfully!"
        print_info "Output: app/build/outputs/apk/release/"
        ;;
    
    test)
        print_info "Running unit tests..."
        time_command $DOCKER_COMPOSE run --rm test
        print_success "Tests completed!"
        print_info "Results: app/build/test-results/"
        ;;
    
    lint)
        print_info "Running lint checks..."
        time_command $DOCKER_COMPOSE run --rm lint
        print_success "Lint checks completed!"
        print_info "Results: app/build/reports/lint-results/"
        ;;
    
    clean)
        print_info "Cleaning build artifacts..."
        time_command $DOCKER_COMPOSE run --rm clean
        print_success "Build artifacts cleaned!"
        ;;
    
    rebuild)
        print_warning "Rebuilding Docker image (this will take longer)..."
        time_command $DOCKER_COMPOSE build $NO_CACHE
        print_success "Docker image rebuilt!"
        ;;
    
    shell)
        print_info "Opening shell in container..."
        $DOCKER_COMPOSE run --rm build-debug /bin/bash
        ;;
    
    *)
        print_error "Unknown command: $COMMAND"
        echo ""
        echo "Available commands:"
        echo "  build-debug     - Build debug APK (fastest)"
        echo "  build-release   - Build release APK"
        echo "  test            - Run unit tests"
        echo "  lint            - Run lint checks"
        echo "  clean           - Clean build artifacts"
        echo "  rebuild         - Rebuild Docker image"
        echo "  shell           - Open shell in container"
        echo ""
        echo "Options:"
        echo "  --time          - Show build time"
        echo "  --no-cache      - Build without cache"
        exit 1
        ;;
esac

echo ""
print_success "Done!"

# Show quick tips
if [ "$COMMAND" = "build-debug" ] || [ "$COMMAND" = "build-release" ]; then
    echo ""
    print_info "Quick tips:"
    echo "  • For faster incremental builds, just run this script again"
    echo "  • Gradle cache is preserved between builds"
    echo "  • Only rebuild the image (./docker-build.sh rebuild) when dependencies change"
    echo "  • Use --time flag to measure build performance"
fi
