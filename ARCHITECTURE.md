# Architecture

- **UI:** React 18 + Vite, rendered inside Android WebView.
- **Reference effect:** `liquid-gooey` only, used around the composer add control.
- **Native bridge:** Kotlin `MainActivity` provides OkHttp requests, secure API-key storage, share/copy, TTS and continuous in-app SpeechRecognizer callbacks.
- **Storage:** conversations/settings/projects/schedules in WebView local storage; API key separately encrypted with Android Keystore AES-GCM.
- **Files:** WebChromeClient handles Android file chooser; camera input uses capture intent through the WebView chooser.
- **Networking:** API calls originate through native OkHttp when running on Android.
