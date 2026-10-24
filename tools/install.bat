@echo off
chcp 65001 >nul
REM Использование: install.bat 192.168.1.50 путь\к\app-release.apk
REM Нужен adb (Android platform-tools) в PATH.

if "%~2"=="" (
  echo Использование: install.bat IP_ТЕЛЕВИЗОРА app-release.apk
  exit /b 1
)

adb connect %1:5555
adb -s %1:5555 install -r %2
adb -s %1:5555 shell cmd package set-home-activity com.hartens.fastlauncher/.MainActivity

REM Ускорение интерфейса (откат: поставить 1.0 вместо 0.5)
adb -s %1:5555 shell settings put global window_animation_scale 0.5
adb -s %1:5555 shell settings put global transition_animation_scale 0.5
adb -s %1:5555 shell settings put global animator_duration_scale 0.5

echo Готово. Нажмите кнопку Home на пульте.
