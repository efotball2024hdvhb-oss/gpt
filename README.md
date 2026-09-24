# MindGPT 5.1 — Android

A polished Android AI client rebuilt against the supplied 35-screen mobile reference set.

## What is implemented

- Chat UI, RTL/LTR, Persian/English, Vazirmatn/Vazir-family typography
- OpenAI, Gemini, Anthropic, OpenRouter and OpenAI-compatible endpoints
- Fixed production chat model: `CodeCraft API dynamic model catalog` (hidden from the chat UI)
- Get Plus information screen with Telegram purchase contact
- Camera, gallery and file attachments
- Long-press message actions: copy, select, edit, share, branch, retry, web search
- Long-press chat actions: pin, rename, archive, project, delete
- In-app Android SpeechRecognizer with partial results and live level feedback (no external Google microphone activity)
- Full voice screen, inline dictation and Android TTS playback bar
- Images, Library, Projects, Scheduled, Plugins, Settings, Personalization, Voice, Data Controls and Appearance screens
- API key encrypted at rest with Android Keystore AES-GCM
- Keyboard-safe visualViewport layout
- `liquid-gooey` is the only Libraries.dev visual package used
- GitHub Actions builds the React UI, Android APK, checks APK size and publishes `releases/MindGPT-debug.apk`

## Build

```bash
npm install
npm run build
gradle --no-daemon :app:assembleDebug
```

The React build is emitted into `app/src/main/assets/web` and loaded through `WebViewAssetLoader` on a secure local origin.

## 5.3 update/signing note
MindGPT 5.3 uses one stable private release signing key for future in-app updates. The `.private/` directory is intentionally ignored by Git and is only used once to configure GitHub Actions secrets with `SETUP_GITHUB_SECRETS_TERMUX.sh`. Never commit or publish that directory.

The AI backend is CodeCraft API (`https://codecraftapi.com/v1`). The app loads the live model catalog from `/models` and stores model capabilities for model-aware image handling. The API key is injected at build time from the `CODECRAFT_API_KEY` GitHub secret; there is no API-key screen in the app.
