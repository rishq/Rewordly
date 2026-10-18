# Play Console: Data safety mapping

How each Data Safety form answer maps to the code. Fill the console form exactly as below.

## Data collected — No

The form asks what data the app **collects** (i.e. transmits off the device). Rewordly sends only
the text of a word you explicitly look up, and only to fulfil that request:

- `Web browsing` / `App interactions` — **No**: no analytics, no trackers, no advertising SDKs
  (verified: zero such dependencies in `gradle/libs.versions.toml`).
- `User identifiers` — **No**: requests carry no account or device identifier.
- The word text itself is ephemeral request data, not "collected" user data under Play's
  definition, because it is not stored by the receiving service on our behalf and is not used for
  profiling.

## Data shared — No

No data is shared with third parties for advertising, analytics or any other purpose.

## Security practices

- **Data encrypted in transit**: Yes — all endpoints are HTTPS (`FreeApisModule.kt`,
  `AiProvider.kt`, `NetworkModule.kt`).
- **Data deletion request**: users delete everything by uninstalling or via in-app data cleanup;
  a URL for the policy: host `docs/privacy-policy.md` (e.g. GitHub Pages).

## Optional form entries triggered only by user action

If you tick that the app collects "Other user-generated content", explain: a generation prompt
(word lists) is sent to the AI provider the user personally configured with their own key
(`AiProviderClient`, only when `AiSettings.isReady`). Key storage is Android Keystore-encrypted
(`KeystoreSecretCipher`).
