FROM ubuntu:22.04

# Set environment variables
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools
ENV DEBIAN_FRONTEND=noninteractive

# Install dependencies
RUN apt-get update && apt-get install -y \
    openjdk-17-jdk \
    wget \
    unzip \
    git \
    && rm -rf /var/lib/apt/lists/*

# Install Android SDK Command Line Tools
# Version: commandlinetools-linux-11076708_latest.zip matches latest stable as of knowledge cutoff
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdline-tools.zip \
    && unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools \
    && mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
    && rm /tmp/cmdline-tools.zip

# Accept licenses and install platform tools and SDK
RUN yes | sdkmanager --licenses \
    && sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# Set working directory
WORKDIR /app

# Copy wrapper first for better caching
COPY gradle/ gradle/
COPY gradlew .
COPY gradle.properties .
COPY settings.gradle.kts .
# Make gradlew executable
RUN chmod +x gradlew

# Download dependencies (this will fail if build.gradle.kts isn't there, so we copy it first)
# However, for a simple Dockerfile, we usually just mount the source. 
# But to make the image pre-warmed, we can copy build files.
# Let's keep it simple: The user execution will mount the source code.
# The image itself just provides the environment.

CMD ["./gradlew", "assembleDebug"]
