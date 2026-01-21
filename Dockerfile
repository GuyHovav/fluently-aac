# syntax=docker/dockerfile:1.4
FROM ubuntu:22.04

# Metadata
LABEL maintainer="FluentlyAAC Team"
LABEL description="Android build environment for FluentlyAAC with BuildKit optimizations"
LABEL version="2.0"

# Set environment variables
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools
ENV DEBIAN_FRONTEND=noninteractive
ENV GRADLE_USER_HOME=/root/.gradle

# Build arguments for parallel builds
ARG GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.parallel=true -Dorg.gradle.workers.max=4"

# Install dependencies
RUN apt-get update && apt-get install -y \
    openjdk-17-jdk \
    wget \
    unzip \
    git \
    && rm -rf /var/lib/apt/lists/*

# Install Android SDK Command Line Tools with cache mount
# Version: commandlinetools-linux-11076708_latest.zip matches latest stable
RUN --mount=type=cache,target=/tmp/sdk-cache \
    mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && if [ ! -f /tmp/sdk-cache/cmdline-tools.zip ]; then \
        wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/sdk-cache/cmdline-tools.zip; \
    fi \
    && unzip -q /tmp/sdk-cache/cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools \
    && mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest

# Accept licenses and install platform tools and SDK
RUN yes | sdkmanager --licenses \
    && sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# Set working directory
WORKDIR /app

# Copy gradle wrapper and properties first (better caching)
COPY gradle/ gradle/
COPY gradlew gradlew.bat ./
COPY gradle.properties .
COPY settings.gradle.kts .

# Make gradlew executable
RUN chmod +x gradlew

# Copy build files to cache dependencies
COPY build.gradle.kts .
COPY app/build.gradle.kts app/

# Pre-download dependencies with cache mount (MUCH faster!)
# BuildKit will persist /root/.gradle across builds
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon --parallel || true

# Copy the rest of the source code
# This layer changes frequently, so it's last
COPY . .

# Default command builds debug APK with cache mount
CMD ["sh", "-c", "./gradlew assembleDebug --no-daemon --parallel"]
