FROM eclipse-temurin:17-jdk-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl unzip git python3 ca-certificates && rm -rf /var/lib/apt/lists/*
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV GRADLE_USER_HOME=/cache/gradle
RUN mkdir -p /opt/android-sdk/cmdline-tools && curl --fail --location --retry 4 https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip -o /tmp/sdk.zip && echo 'e025545c62a8e64c7559119566a569fb1dec5f60  /tmp/sdk.zip' | sha1sum -c - && unzip -q /tmp/sdk.zip -d /opt/android-sdk/cmdline-tools && mv /opt/android-sdk/cmdline-tools/cmdline-tools /opt/android-sdk/cmdline-tools/latest && rm /tmp/sdk.zip
ENV PATH=/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:${PATH}
RUN yes | sdkmanager --licenses >/dev/null
RUN sdkmanager 'platforms;android-34' 'platforms;android-36' 'build-tools;36.0.0'
WORKDIR /build
