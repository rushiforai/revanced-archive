# Сборка в Docker

Исходники этого репозитория — отдельное дополнение, которое использует закреплённый Gradle-toolchain ReVanced. Собственный Gradle wrapper здесь не дублируется. Официальные патчи не включаются в итоговый `.rvp`.

## Подготовка

Нужны Git и Docker с Linux-контейнерами. В примерах используется Bash, рабочий каталог — корень этого репозитория.

```bash
docker build -t vot-revanced-builder .
git clone https://gitlab.com/ReVanced/revanced-patches.git toolchain
git -C toolchain checkout 015fe10a23ea5b919a3fda6e7da2c176517ae75d
docker volume create vot-gradle-cache
```

При первом заполнении Gradle-кэша требуются учётные данные GitHub Packages с доступом на чтение пакетов. Задайте переменные окружения `ORG_GRADLE_PROJECT_githubPackagesUsername` и `ORG_GRADLE_PROJECT_githubPackagesPassword` средствами своей оболочки. Не записывайте токен в исходники, Dockerfile или команду, которую собираетесь публиковать.

```bash
docker run --rm \
  --mount "type=bind,source=$PWD/toolchain,target=/build" \
  --mount "type=bind,source=$PWD,target=/standalone" \
  --mount type=volume,source=vot-gradle-cache,target=/cache/gradle \
  --env ORG_GRADLE_PROJECT_githubPackagesUsername \
  --env ORG_GRADLE_PROJECT_githubPackagesPassword \
  --workdir /build vot-revanced-builder \
  bash ./gradlew :patches:verifyStandalone \
  -I /standalone/build.init.gradle --no-daemon --max-workers=2 --console=plain
```

Для уже заполненного кэша можно убрать передачу переменных и добавить к Gradle `--offline -PgithubPackagesUsername=offline -PgithubPackagesPassword=offline`.

Результат: **`build/patches/libs/vot-standalone-0.3.1.rvp`**. На Windows используйте абсолютные пути в bind mounts и LF-окончания строк в `toolchain/gradlew`.

## Проверка применения на чистом APK

Положите в корень проекта:

- `clean-youtube-20.40.45.apk` — чистый APK нужной версии;
- `official-patches-6.2.1.rvp` — официальный набор именно **6.2.1**.

Хэши файлов использованной проверки находятся в [build-record.json](verification/0.3.1/build-record.json). Текущий адрес API ReVanced может уже отдавать другую версию — сверяйте версию и SHA-256.

К команде Gradle выше добавьте `:patches:exerciseVot -Dvot.apk=/standalone/clean-youtube-20.40.45.apk`. Проверка загрузит оба `.rvp`, применит стандартный выбор официальных патчей и VOT, скомпилирует DEX и ресурсы и соберёт неподписанный диагностический APK в `build/exercise/`. Он не устанавливается автоматически.

Локальные проверки протокола, OAuth, синхронизации и аудиовыхода запускаются в PowerShell с JDK:

```powershell
./checks/run.ps1
```

`stubs/` содержит только заглушки для компиляции; они не попадают в Android-расширение. `verifyStandalone` проверяет один публичный патч и отсутствие общих расширений ReVanced в комплекте.
