FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/emulator
ENV ANDROID_EMU_NETWORK_BRIDGE=true
ENV ANDROID_EMU_NETWORK_HOST=true

# Installation des dépendances
RUN apt-get update && apt-get install -y \
    wget \
    unzip \
    openjdk-17-jdk \
    libpulse0 \
    libgl1 \
    libxcomposite1 \
    libxcursor1 \
    libxi6 \
    libxtst6 \
    x11-xserver-utils \
    xauth \
    x11-apps \
    libgl1-mesa-glx \
    libgl1-mesa-dri \
    mesa-utils \
    libc6 \
    libdbus-1-3 \
    libfontconfig1 \
    libpulse0 \
    libtinfo5 \
    libx11-6 \
    libxcb1 \
    libxdamage1 \
    libxext6 \
    libxfixes3 \
    zlib1g \
    libgl1 \
    && rm -rf /var/lib/apt/lists/*

# Installation Android SDK
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    wget https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip -O cmdline-tools.zip && \
    unzip cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools && \
    mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && \
    rm cmdline-tools.zip

# Accepter les licences
RUN yes | sdkmanager --licenses

# Installer les composants nécessaires
RUN sdkmanager --install "platform-tools" "platforms;android-35" \
    "build-tools;34.0.0" \
    "system-images;android-35;default;x86_64" \
    "emulator"

# Créer un émulateur
RUN echo "no" | avdmanager create avd \
    -n test_device \
    -k "system-images;android-35;default;x86_64" \
    --device "pixel" \
    --force

WORKDIR /app

# Copier le projet
COPY . .

# Configuration de l'émulateur
RUN mkdir -p /root/.android/avd/test_device.avd && \
    echo "hw.lcd.density=420" >> /root/.android/avd/test_device.avd/config.ini && \
    echo "hw.lcd.height=1920" >> /root/.android/avd/test_device.avd/config.ini && \
    echo "hw.lcd.width=1080" >> /root/.android/avd/test_device.avd/config.ini && \
    echo "hw.gpu.enabled=yes" >> /root/.android/avd/test_device.avd/config.ini && \
    echo "hw.gpu.mode=auto" >> /root/.android/avd/test_device.avd/config.ini && \
    echo "hw.ramSize=2048" >> /root/.android/avd/test_device.avd/config.ini

# Script de démarrage
RUN echo '#!/bin/bash\n\
echo "🔍 Démarrage des services..."\n\
\n\
# Test de connectivité vers l API\n\
echo "🔍 Test de connectivité vers l API..."\n\
wget http://host.docker.internal:8080/api/auth/register -O - || echo "⚠️ API non accessible"\n\
\n\
# Démarrer l émulateur avec des paramètres optimisés\n\
echo "🚀 Démarrage de l émulateur..."\n\
${ANDROID_HOME}/emulator/emulator \
    -avd test_device \
    -no-audio \
    -no-snapshot \
    -no-boot-anim \
    -gpu auto \
    -dns-server 8.8.8.8 \
    -qemu -no-reboot &\n\
\n\
# Attendre que l émulateur soit prêt\n\
echo "⏳ Attente du démarrage de l émulateur..."\n\
${ANDROID_HOME}/platform-tools/adb wait-for-device\n\
\n\
# Attendre le démarrage complet\n\
${ANDROID_HOME}/platform-tools/adb shell "while [[ -z \$(getprop sys.boot_completed) ]]; do sleep 1; done"\n\
echo "✅ Émulateur démarré !"\n\
\n\
# Configuration réseau de l émulateur\n\
echo "🌐 Configuration du réseau..."\n\
${ANDROID_HOME}/platform-tools/adb shell settings put global http_proxy 10.0.2.2:8080\n\
\n\
# Construire l application\n\
echo "🔨 Construction de l application..."\n\
./gradlew assembleDebug --no-daemon\n\
\n\
# Installer l application\n\
echo "📲 Installation de l application..."\n\
${ANDROID_HOME}/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk\n\
\n\
# Lancer l application\n\
echo "🚀 Lancement de l application..."\n\
${ANDROID_HOME}/platform-tools/adb shell am start -n "com.example.supmap/.MainActivity"\n\
\n\
# Garder le conteneur en vie\n\
tail -f /dev/null' > /usr/local/bin/start.sh && chmod +x /usr/local/bin/start.sh

ENTRYPOINT ["/usr/local/bin/start.sh"]