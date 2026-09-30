# Rewordly

Native Android app for learning English vocabulary through contextual cards (STEP 1: foundation + UI on local mock data).

- Kotlin, Jetpack Compose, Material 3, Navigation Compose (type-safe routes)
- Hilt, Room (words, examples, progress), DataStore (settings, onboarding, recent searches)
- Retrofit/OkHttp prepared but unused (no backend yet)
- Interface languages: Russian (default, `values/`) and English (`values-en/`), independent of the learning language

## Structure

```
app/src/main/java/com/rewordly/app/
  core/      common, database, datastore, navigation, network, ui (theme + components)
  domain/    model, repository (interfaces), usecase
  data/      local (mock vocabulary + mappers), remote, repository (implementations)
  feature/   splash, onboarding, home, learn, word, review, search, profile, settings
```

UI -> ViewModel (StateFlow) -> UseCase/Repository -> Room/DataStore.

## Requirements

JDK 17, Android SDK with platform 35 and build-tools 35.0.0 (`sdk.dir` in `local.properties` or `ANDROID_HOME`).

## Build & run

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # unsigned release APK (R8 enabled)
./gradlew installDebug           # install on a connected device/emulator
./gradlew ktlintCheck lintDebug testDebugUnitTest
```
