# Быстрый лаунчер для Hartens HTY-32H068B-VZ (Яндекс ТВ, Android TV 11)

Лёгкий домашний экран без рекламы и рекомендаций. Только системный SDK, без библиотек.

- Верхний ряд — избранное. При первом запуске закрепляются (по названию): YouTube/SmartTube, Zetflix, Lampa, Wink, VK Видео, Twitch, Happ.
- Ниже — сетка всех приложений. Долгое нажатие OK: в избранное / скрыть / о приложении / удалить.
- Кнопка «VPN» открывает Happ.
- Меню «Настройки»: Android, Wi-Fi, VPN, показать скрытые, сбросить избранное, «Скрыть всё, кроме избранного».

Приложения Zetflix и Lampa устанавливаются отдельно, лаунчер лишь показывает их.

## Сборка
Actions → Build APK → Run workflow → артефакт `hartens-launcher` (внутри `app-release.apk`).

## Установка
```
adb connect IP:5555
adb install -r app-release.apk
adb shell cmd package set-home-activity com.hartens.fastlauncher/.MainActivity
```
Откат к заводскому лаунчеру:
```
adb shell cmd package set-home-activity com.yandex.tv.home/.HomeActivity
adb uninstall com.hartens.fastlauncher
```

## VPN-ключ
Ключ в репозиторий не кладётся. Храните его в `tools/key.txt` (файл в .gitignore) и отправляйте в Happ скриптом `tools/send-key-to-happ.ps1` через «Web Import» в приложении.

## Отключение лишнего
`tools/debloat.sh IP list` сохраняет список пакетов; отключение обратимо (`pm disable-user`, возврат через `restore`).
