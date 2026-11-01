# Interface translations

The app uses Android string resources, so the interface works offline. Existing translations are preserved. The development-only workflow `Generate interface translations` fills missing entries on `arena/azure-interface-translations` using Azure Translator. It does not translate user content, AI replies, code-generated messages, or the app at runtime. No Translator key is included in the APK.

The workflow requires the repository Actions secret `AZURE_TRANSLATOR_KEY` and uses the regional resource `uaenorth`. It sends only English interface strings, including formatting placeholders, to Azure. Each language request consumes characters from the Azure resource quota. The script rejects missing or duplicated protected formatting tokens and stops on API errors without printing keys. It appends results to the existing files, never overwriting existing translations.

All generated and previously existing translations need review by fluent speakers. Android layouts and programmatic error messages may still contain English. Do not treat resource coverage as proof of correct, complete localization. Run Android CI and test language switching on a device before release.
