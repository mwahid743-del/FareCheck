# FareCheck
1. Push this folder to a GitHub repo (branch `main`). Actions builds the APK -> download artifact `farecheck-debug-apk`.
2. Install on the Samsung, open FareCheck, set targets, enable the accessibility service.
3. Tune parsing: `adb logcat -s FareCheck` shows the text read from each offer screen; adjust regexes in OfferParser.kt.
4. Confirm package names in Models.kt for your installed driver apps.
