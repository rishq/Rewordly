# Privacy Policy / Политика конфиденциальности

_Last updated / Обновлено: 2026-10-18. Applies to Rewordly for Android ("the app")._
_Действует для Android-приложения Rewordly («приложение»)._

## English

**What the app does.** Rewordly is a vocabulary trainer. Your words, progress, review history and
settings are stored on your device (Room database + DataStore) and, unless you disable it, in your
own Google Drive via Android Auto Backup. There is no Rewordly account, no analytics, no advertising
and no third-party trackers.

**Information the app never collects.** We operate no backend that receives your data by default.
The app does not read contacts, location, microphone or camera. No device identifiers are collected.

**Network requests the app can make.**

| Service | When | What is sent |
|---|---|---|
| MyMemory (`api.mymemory.translated.net`) | Translating a word you look up or add | The English word text |
| Wiktionary (`en.wiktionary.org`) | Enriching a word's definition | The English word text |
| Your chosen AI provider (OpenAI, Anthropic or Google Gemini) | Only if you enter your own API key | Word lists and generation prompts |
| Your own Rewordly backend | Only if the build was configured with a backend URL | Word lists and generation prompts |

Word texts are sent to the services above solely to fulfil your request. Requests carry no user
identifier; the HTTP User-Agent is `Rewordly/<version>`.

**Your API key.** If you provide an AI provider key, it is encrypted with a key held in the
Android Keystore before it is written to disk. It never leaves the device except in the encrypted
request to the provider you chose.

**Automatic cloud backup.** Unless you turn it off in Settings → Data → Cloud backup, Android Auto
Backup copies your app data (words, progress, review history and settings) into your own Google
Drive account. On Android 9+ it is end-to-end encrypted with your device screen lock where the
device supports it, and the copy is restored automatically when you reinstall the app. Turning the
switch off stops future backups; it does not delete a copy already in your Drive.

**Backups and exports.** A manual backup or CSV export writes a file to a location you pick through
the Android file picker. The app itself never uploads those files.

**Deleting data.** Uninstalling the app or using in-app data cleanup permanently deletes all local
data. There is nothing on our servers to delete; a cloud backup, if enabled, is held in your Google
Drive and is managed by Google.

**Children.** The app contains no content targeted at children and does not knowingly collect data
from anyone, including children.

**Contact.** Open an issue at https://github.com/rishq/Rewordly.

## Русский

**Что делает приложение.** Rewordly — тренажёр словарного запаса. Слова, прогресс, история
повторений и настройки хранятся на устройстве (база Room + DataStore) и, если вы это не отключили,
в вашем собственном Google Drive через Android Auto Backup. Аккаунта Rewordly, аналитики, рекламы
и сторонних трекеров нет.

**Что приложение не собирает.** По умолчанию нет нашего сервера, принимающего ваши данные.
Приложение не читает контакты, геолокацию, микрофон и камеру. Идентификаторы устройства не
собираются.

**Сетевые запросы, которые приложение может делать.**

| Сервис | Когда | Что отправляется |
|---|---|---|
| MyMemory (`api.mymemory.translated.net`) | Перевод слова, которое вы ищете или добавляете | Текст английского слова |
| Wiktionary (`en.wiktionary.org`) | Пополнение определения слова | Текст английского слова |
| Ваш AI-провайдер (OpenAI, Anthropic или Google Gemini) | Только если вы ввели свой API-ключ | Списки слов и промпты генерации |
| Ваш собственный бэкенд Rewordly | Только если сборка настроена на его URL | Списки слов и промпты генерации |

Тексты слов отправляются перечисленным сервисам только для выполнения вашего запроса. В запросах
нет идентификатора пользователя; HTTP User-Agent — `Rewordly/<версия>`.

**Ваш API-ключ.** Ключ AI-провайдера шифруется ключом из Android Keystore перед записью на диск.
Он покидает устройство только внутри зашифрованного запроса к выбранному вами провайдеру.

**Автоматическая резервная копия.** Пока вы не выключите её в «Настройки → Данные → Резервная копия
в облаке», Android Auto Backup копирует данные приложения (слова, прогресс, историю повторений и
настройки) в ваш собственный аккаунт Google Drive. На Android 9+ копия шифруется сквозным шифрованием
с помощью блокировки экрана, где это поддерживается, и восстанавливается автоматически при
переустановке. Выключение переключателя останавливает новые копии, но не удаляет уже сохранённую.

**Резервные копии и экспорт.** Ручной бэкап или CSV-экспорт записывает файл туда, куда вы укажете
через системный файловый пикер. Само приложение эти файлы никуда не выгружает.

**Удаление данных.** Удаление приложения или встроенная очистка данных необратимо стирает все
локальные данные. На наших серверах хранить нечего; облачная копия, если она включена, лежит в вашем
Google Drive и управляется Google.

**Дети.** Приложение не содержит контента для детей и не собирает данные ни от кого, включая детей.

**Связь.** Создайте issue: https://github.com/rishq/Rewordly.
