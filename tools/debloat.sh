#!/usr/bin/env bash
# Управление лишними приложениями через ADB. Ничего не удаляет насовсем:
# приложение отключается для пользователя 0 и в любой момент возвращается командой restore.
#
#   ./debloat.sh IP list              # сохранить список всех пакетов в packages.txt
#   ./debloat.sh IP disable pkgs.txt  # отключить пакеты из файла (по одному в строке)
#   ./debloat.sh IP restore pkgs.txt  # вернуть обратно
set -e
IP="$1"; CMD="$2"; FILE="$3"
ADB="adb -s $IP:5555"
adb connect "$IP:5555" >/dev/null

case "$CMD" in
  list)
    $ADB shell pm list packages -f | sed 's/^package://' > packages.txt
    echo "Сохранено в packages.txt ($(wc -l < packages.txt) пакетов)"
    ;;
  disable)
    while read -r p; do
      [ -z "$p" ] && continue
      case "$p" in \#*) continue;; esac
      echo "disable $p"; $ADB shell pm disable-user --user 0 "$p"
    done < "$FILE"
    ;;
  restore)
    while read -r p; do
      [ -z "$p" ] && continue
      case "$p" in \#*) continue;; esac
      echo "enable $p"; $ADB shell pm enable "$p"
    done < "$FILE"
    ;;
  *) echo "Команды: list | disable файл | restore файл"; exit 1;;
esac
