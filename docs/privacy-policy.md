# Privacy Policy / Политика конфиденциальности

_Last updated / Обновлено: 2026-10-18. Applies to Rewordly for Android ("the app")._
_Действует для Android-приложения Rewordly («приложение»)._

## English

**What the app does.** Rewordly is a vocabulary trainer. Your words, progress, review history and
settings are stored **only on your device** (Room database + DataStore). There is no account, no
analytics, no advertising and no third-party trackers.

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

**Backups and exports.** Creating a backup or CSV export writes a file to a location you pick
through the Android file picker. Nothing is uploaded.

**Deleting data.** Uninstalling the app or using in-app data cleanup permanently deletes all local
data. There is nothing stored server-side to delete.

**Children.** The app contains no content targeted at children and does not knowingly collect data
from anyone, including children.

**Contact.** Open an issue at https://github.com/rishq/Rewordly.

## Русский

**Что делает приложение.** Rewordly — тренажёр словарного запаса. Слова, прогресс, история
повторений и настройки хранятся **только на устройстве** (база Room + DataStore). Нет аккаунтов,
аналитики, рекламы и сторонних трекеров.

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

**Резервные копии и экспорт.** Создание бэкапа или CSV-экспорта записывает файл туда, куда вы
укажете через системный файловый пикер. Ничего не выгружается в сеть.

**Удаление данных.** Удаление приложения или встроенная очистка данных необратимо стирает все
локальные данные. На серверах хранить нечего.

**Дети.** Приложение не содержит контента для детей и не собирает данные ни от кого, включая детей.

**Связь.** Создайте issue: https://github.com/rishq/Rewordly.
