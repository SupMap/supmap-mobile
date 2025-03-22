#!/bin/bash

echo "🔍 Vérification de l'émulateur..."

# Démarrer l'émulateur avec interface graphique
echo "🚀 Démarrage de l'émulateur..."
emulator -avd test_device -no-audio -gpu swiftshader_indirect -no-boot-anim -no-window &

# Attendre que l'émulateur soit complètement démarré
echo "⏳ Attente du démarrage de l'émulateur..."
$ANDROID_HOME/platform-tools/adb wait-for-device

# Attendre que le système soit complètement démarré
$ANDROID_HOME/platform-tools/adb shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done'
echo "✅ Émulateur démarré !"

# Construire l'application
echo "🔨 Construction de l'application..."
./gradlew assembleDebug --no-daemon

# Installer sur l'émulateur
echo "📲 Installation de l'application..."
$ANDROID_HOME/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk

# Lancer l'application
echo "🚀 Lancement de l'application..."
$ANDROID_HOME/platform-tools/adb shell am start -n "com.example.supmap/.MainActivity"

# Garder le conteneur en vie
tail -f /dev/null