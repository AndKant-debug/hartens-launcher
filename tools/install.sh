#!/usr/bin/env bash
# Использование: ./install.sh 192.168.1.50 app-release.apk
set -e
IP="$1"; APK="$2"
[ -z "$IP" ] || [ -z "$APK" ] && { echo "Использование: $0 IP_ТЕЛЕВИЗОРА app-release.apk"; exit 1; }

adb connect "$IP:5555"
adb -s "$IP:5555" install -r "$APK"
adb -s "$IP:5555" shell cmd package set-home-activity com.hartens.fastlauncher/.MainActivity

# Ускорение интерфейса (откат: поставить 1.0 вместо 0.5)
adb -s "$IP:5555" shell settings put global window_animation_scale 0.5
adb -s "$IP:5555" shell settings put global transition_animation_scale 0.5
adb -s "$IP:5555" shell settings put global animator_duration_scale 0.5

echo "Готово. Нажмите Home на пульте."
