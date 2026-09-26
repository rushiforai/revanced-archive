# URL-источник для ReVanced Manager

Публичный адрес:

```text
https://raw.githubusercontent.com/SergeyBelentev/voice-over-translation-revanced/main/patches.json
```

Manager 2.6.0 загружает этот JSON, читает `version`, `created_at`, `download_url`, `description` и скачивает `.rvp`. Отдельный сервер, GitHub Pages и вход в GitHub для пользователей не нужны.

Формат проверен по исходникам Manager [ReVancedAsset.kt](https://github.com/ReVanced/revanced-manager/blob/v2.6.0/app/src/main/java/app/revanced/manager/network/dto/ReVancedAsset.kt) и [RemoteSource.kt](https://github.com/ReVanced/revanced-manager/blob/v2.6.0/app/src/main/java/app/revanced/manager/domain/sources/RemoteSource.kt). `created_at` содержит время публикации UTC без суффикса часового пояса, поскольку поле декодируется как `LocalDateTime`. Поле подписи необязательно; подпись в этом источнике не заявляется.

## Как выпускать следующие версии

1. Соберите и проверьте новый `.rvp` с новым номером версии.
2. Создайте публичный стабильный GitHub Release с тегом `vX.Y.Z`, приложите `vot-standalone-X.Y.Z.rvp` и контрольные суммы.
3. Запустите из корня репозитория:

```bash
python scripts/update-source.py vX.Y.Z
```

4. Проверьте изменения `patches.json`, закоммитьте и отправьте в `main`.

Скрипт проверяет опубликованный релиз, размер, SHA-256, версию внутри RVP и наличие собственного расширения. Он использует публичные endpoints без токена. Ссылка в `download_url` привязана к конкретному тегу, чтобы версия метаданных и скачиваемый файл не расходились.

Manager сравнивает строку `version` с уже загруженной: для обновления нужен новый номер. Само создание релиза не меняет `patches.json`; выполните шаги 3–4. GitHub Raw может некоторое время возвращать закэшированный JSON — повторите проверку обновления позже.

После обновления набора пользователю всё равно нужно заново пропатчить чистый APK и установить результат. Следите за совместимостью новых релизов с версиями YouTube и официальных патчей и обновляйте инструкцию вместе с метаданными.
