# ASIDO Nota Digital — Android APK

This is an installable Android app that keeps the familiar ASIDO Nota Digital interface and adds a native Android bridge for WhatsApp sharing.

## Native sharing behavior
When you tap **Kirim Nota ke WhatsApp**, the app passes the current white PNG invoice (including signatures) to Android. Android decodes the PNG, saves it to **Pictures/ASIDO Nota Digital**, then shares it with WhatsApp using the native Android ACTION_SEND intent, EXTRA_STREAM, ClipData, and FLAG_GRANT_READ_URI_PERMISSION. It sends the image file only; it does not send an empty text payload. You choose the customer and press Send.

## Other Android-specific support
- Voice input uses Android's Indonesian speech recognizer.
- Calendar reminders open the Android Calendar event editor with the note and detected date.
- The web note interface keeps the existing product catalog, editable prices, line items, signatures, history, backup import/export and PDF/PNG rendering.

## Important data note
The APK's WebView storage is separate from Chrome's storage. Before switching, export the backup JSON from the current Chrome version. In the APK, use **Impor cadangan** once to bring the existing product catalog and invoice history into the app. Data remains on the phone and is not written into the public GitHub repository.

## Build and install
The GitHub Actions workflow builds an installable debug APK. In GitHub, open **Actions → Build ASIDO Android APK**, then download the artifact named **ASIDO-Nota-Digital-Android-APK**. Open app-debug.apk on the Android phone and allow installation from that source if Android asks.

This debug APK is for direct testing and is not signed for Play Store distribution.
