package id.asido.notadigital;

import android.app.Activity;
import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.content.ContentUris;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.webkit.JavascriptInterface;
import android.webkit.ServiceWorkerController;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.ValueCallback;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int VOICE_REQUEST = 7101;
    private static final int FILE_CHOOSER_REQUEST = 7102;
    private static final int CALENDAR_PERMISSION_REQUEST = 7103;
    private static final String APP_URL = "https://asidodavid.github.io/ASIDO-NOTA-IIGITAL/?nativeapk=1&build=1.3.0";
    private WebView web;
    private TextView status;
    private Uri lastPdfUri;
    private ValueCallback<Uri[]> filePathCallback;
    private String pendingTempoReminderJson;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF143C73);
        getWindow().setNavigationBarColor(0xFF000000);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF1F5FA);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(7), dp(8), dp(7));
        bar.setBackgroundColor(0xFF143C73);
        TextView title = new TextView(this);
        title.setText("ASIDO Nota Digital");
        title.setTextColor(-1);
        title.setTextSize(18);
        title.setTypeface(null, 1);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button reload = new Button(this);
        reload.setText("Muat ulang");
        reload.setAllCaps(false);
        reload.setTextSize(12);
        reload.setOnClickListener(v -> web.reload());
        bar.addView(reload);
        root.addView(bar);
        status = new TextView(this);
        status.setText("Membuka ASIDO Nota Digital…");
        status.setTextSize(11);
        status.setTextColor(0xFF526276);
        status.setPadding(dp(10), dp(3), dp(10), dp(3));
        root.addView(status);
        web = new WebView(this);
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        configureWebView();
        web.loadUrl(APP_URL);
    }

    private void configureWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportMultipleWindows(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        web.clearCache(true);
        try { ServiceWorkerController.getInstance().getServiceWorkerWebSettings().setCacheMode(WebSettings.LOAD_NO_CACHE); }
        catch (Exception ignored) { }
        web.addJavascriptInterface(new AndroidBridge(), "AndroidAsido");
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    Intent intent = params.createIntent();
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception e) {
                    filePathCallback = null;
                    toast("Tidak dapat membuka pemilih file.");
                    return false;
                }
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
                if ("asidodavid.github.io".equals(host)) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                catch (Exception e) { toast("Tidak dapat membuka tautan."); }
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                status.setText("Terhubung · Berbagi gambar memakai Android asli");
            }
        });
    }

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }

    private class AndroidBridge {
        @JavascriptInterface public void sharePng(String dataUrl, String fileName) {
            runOnUiThread(() -> nativeSharePng(dataUrl, fileName));
        }
        @JavascriptInterface public String savePdf(String dataUrl, String fileName) {
            return nativeSavePdf(dataUrl, fileName);
        }
        @JavascriptInterface public String shareSavedPdf() {
            Uri uri = getLastPdfUri();
            if (uri == null) return "ERROR|Buat dan simpan PDF terlebih dahulu.";
            runOnUiThread(() -> nativeSharePdf(uri));
            return "OK|Menu berbagi PDF dibuka.";
        }
        @JavascriptInterface public String openSavedPdf() {
            Uri uri = getLastPdfUri();
            if (uri == null) return "ERROR|Buat dan simpan PDF terlebih dahulu.";
            runOnUiThread(() -> nativeOpenPdf(uri));
            return "OK|PDF dibuka.";
        }
        @JavascriptInterface public void startVoiceInput() {
            runOnUiThread(MainActivity.this::startNativeVoice);
        }
        @JavascriptInterface public void addCalendarReminder(String note, String customer) {
            runOnUiThread(() -> createCalendarEvent(note, customer));
        }
        @JavascriptInterface public void scheduleTempoReminder(String dueDate, String customer, String amount, String reference) {
            try {
                JSONObject item = new JSONObject();
                item.put("dueDate", dueDate);
                item.put("customer", customer);
                item.put("amount", amount);
                item.put("reference", reference);
                JSONArray items = new JSONArray();
                items.put(item);
                runOnUiThread(() -> requestOrScheduleTempoReminders(items.toString()));
            } catch (Exception e) {
                runOnUiThread(() -> toast("Data pengingat Tempo tidak valid."));
            }
        }
        @JavascriptInterface public void scheduleTempoReminders(String json) {
            runOnUiThread(() -> requestOrScheduleTempoReminders(json));
        }
        @JavascriptInterface public String version() { return "1.3.0"; }
    }
    private void nativeSharePng(String dataUrl, String fileName) {
        Uri uri = null;
        try {
            if (dataUrl == null || !dataUrl.startsWith("data:image/png;base64,")) {
                toast("Data gambar PNG tidak valid. Coba buat nota lagi.");
                return;
            }
            byte[] bytes = android.util.Base64.decode(dataUrl.substring(dataUrl.indexOf(',') + 1), android.util.Base64.DEFAULT);
            if (bytes.length < 1000) {
                toast("Gambar nota kosong. Coba buat ulang.");
                return;
            }
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) {
                toast("Android tidak dapat membaca gambar nota.");
                return;
            }
            String name = safeName(fileName);
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/ASIDO Nota Digital");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                bitmap.recycle();
                toast("Tidak dapat membuat file gambar di Galeri.");
                return;
            }
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    getContentResolver().delete(uri, null, null);
                    bitmap.recycle();
                    toast("Gagal menulis gambar nota.");
                    return;
                }
            }
            bitmap.recycle();
            ContentValues ready = new ContentValues();
            ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
            getContentResolver().update(uri, ready, null, null);

            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("image/png");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.setClipData(ClipData.newUri(getContentResolver(), "Nota ASIDO", uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            String pkg = installed("com.whatsapp") ? "com.whatsapp" :
                    (installed("com.whatsapp.w4b") ? "com.whatsapp.w4b" : null);
            if (pkg != null) {
                send.setPackage(pkg);
                try {
                    startActivity(send);
                    toast("Lampiran gambar nota sudah disiapkan. Pilih pelanggan di WhatsApp.");
                } catch (ActivityNotFoundException e) {
                    send.setPackage(null);
                    startActivity(Intent.createChooser(send, "Kirim nota ASIDO"));
                }
            } else {
                startActivity(Intent.createChooser(send, "Kirim nota ASIDO"));
            }
        } catch (Exception e) {
            if (uri != null) {
                try { getContentResolver().delete(uri, null, null); } catch (Exception ignored) { }
            }
            toast("Berbagi gagal: " + (e.getMessage() == null ? "kesalahan Android" : e.getMessage()));
        }
    }

    private String nativeSavePdf(String dataUrl, String fileName) {
        Uri uri = null;
        try {
            if (dataUrl == null || !dataUrl.startsWith("data:application/pdf") || !dataUrl.contains(";base64,")) {
                return "ERROR|Data PDF tidak valid.";
            }
            byte[] bytes = android.util.Base64.decode(dataUrl.substring(dataUrl.indexOf(',') + 1), android.util.Base64.DEFAULT);
            if (bytes.length < 1000) return "ERROR|File PDF kosong atau terlalu kecil.";
            String name = fileName == null ? "ASIDO-Nota.pdf" : fileName.replaceAll("[^A-Za-z0-9._-]", "-");
            if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf")) name += ".pdf";
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/ASIDO");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) return "ERROR|Tidak dapat membuat file PDF di Downloads.";
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IllegalStateException("Tidak dapat menulis file PDF.");
                out.write(bytes);
                out.flush();
            }
            ContentValues ready = new ContentValues();
            ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
            getContentResolver().update(uri, ready, null, null);
            lastPdfUri = uri;
            getSharedPreferences("asido_native", MODE_PRIVATE).edit().putString("last_pdf_uri", uri.toString()).apply();
            return "OK|" + uri.toString();
        } catch (Exception e) {
            if (uri != null) {
                try { getContentResolver().delete(uri, null, null); } catch (Exception ignored) { }
            }
            return "ERROR|" + (e.getMessage() == null ? "Gagal menyimpan PDF." : e.getMessage());
        }
    }

    private Uri getLastPdfUri() {
        if (lastPdfUri != null) return lastPdfUri;
        String saved = getSharedPreferences("asido_native", MODE_PRIVATE).getString("last_pdf_uri", null);
        if (saved == null) return null;
        try { lastPdfUri = Uri.parse(saved); return lastPdfUri; }
        catch (Exception ignored) { return null; }
    }

    private void nativeSharePdf(Uri uri) {
        try {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("application/pdf");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.setClipData(ClipData.newUri(getContentResolver(), "Nota ASIDO PDF", uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            String pkg = installed("com.whatsapp") ? "com.whatsapp" :
                    (installed("com.whatsapp.w4b") ? "com.whatsapp.w4b" : null);
            if (pkg != null) {
                send.setPackage(pkg);
                try { startActivity(send); }
                catch (ActivityNotFoundException e) {
                    send.setPackage(null);
                    startActivity(Intent.createChooser(send, "Bagikan PDF nota ASIDO"));
                }
            } else {
                startActivity(Intent.createChooser(send, "Bagikan PDF nota ASIDO"));
            }
        } catch (Exception e) {
            toast("Gagal membagikan PDF: " + (e.getMessage() == null ? "kesalahan Android" : e.getMessage()));
        }
    }

    private void nativeOpenPdf(Uri uri) {
        try {
            Intent view = new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(uri, "application/pdf");
            view.setClipData(ClipData.newUri(getContentResolver(), "Nota ASIDO PDF", uri));
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(view, "Buka PDF nota ASIDO"));
        } catch (Exception e) {
            toast("Tidak ada aplikasi pembaca PDF atau file tidak bisa dibuka.");
        }
    }

    private String safeName(String input) {
        String n = input == null ? "ASIDO-Nota.png" : input.replaceAll("[^A-Za-z0-9._-]", "-");
        if (!n.toLowerCase(Locale.ROOT).endsWith(".png")) n += ".png";
        return n.length() > 90 ? n.substring(0, 86) + ".png" : n;
    }

    private boolean installed(String pkg) {
        try { getPackageManager().getPackageInfo(pkg, 0); return true; }
        catch (Exception e) { return false; }
    }

    private void startNativeVoice() {
        try {
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID");
            i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Contoh: Sapu ijuk 10 buah harga 15000");
            startActivityForResult(i, VOICE_REQUEST);
        } catch (ActivityNotFoundException e) {
            toast("Layanan input suara tidak tersedia di HP ini.");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback != null) {
                Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
            return;
        }
        if (requestCode == VOICE_REQUEST && resultCode == RESULT_OK && data != null) {
            ArrayList<String> list = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (list != null && !list.isEmpty()) {
                web.evaluateJavascript("window.onAsidoVoiceResult && window.onAsidoVoiceResult(" + JSONObject.quote(list.get(0)) + ")", null);
            }
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CALENDAR_PERMISSION_REQUEST) {
            boolean granted = grantResults.length >= 2;
            for (int result : grantResults) granted = granted && result == PackageManager.PERMISSION_GRANTED;
            if (granted && pendingTempoReminderJson != null) {
                String json = pendingTempoReminderJson;
                pendingTempoReminderJson = null;
                scheduleTempoRemindersNow(json);
            } else {
                pendingTempoReminderJson = null;
                toast("Izin Kalender tidak diberikan. Pengingat Tempo belum ditambahkan.");
            }
        }
    }

    private void requestOrScheduleTempoReminders(String json) {
        if (json == null || json.trim().isEmpty()) {
            toast("Tidak ada transaksi Tempo untuk dijadwalkan.");
            return;
        }
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            pendingTempoReminderJson = json;
            requestPermissions(new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR}, CALENDAR_PERMISSION_REQUEST);
            return;
        }
        scheduleTempoRemindersNow(json);
    }

    private long findWritableCalendarId() {
        Cursor cursor = null;
        long fallbackId = -1L;
        long googleId = -1L;
        long googlePrimaryId = -1L;
        try {
            String[] projection = new String[]{
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.ACCOUNT_TYPE,
                    CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                    CalendarContract.Calendars.VISIBLE,
                    CalendarContract.Calendars.IS_PRIMARY
            };
            cursor = getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null);
            if (cursor == null) return -1L;
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String accountType = cursor.getString(1);
                int access = cursor.getInt(2);
                int visible = cursor.getInt(3);
                int primary = cursor.getInt(4);
                if (visible != 1 || access < 500) continue;
                if (fallbackId < 0) fallbackId = id;
                if ("com.google".equalsIgnoreCase(accountType)) {
                    if (googleId < 0) googleId = id;
                    if (primary == 1) googlePrimaryId = id;
                }
            }
        } catch (Exception e) {
            return -1L;
        } finally {
            if (cursor != null) cursor.close();
        }
        if (googlePrimaryId >= 0) return googlePrimaryId;
        if (googleId >= 0) return googleId;
        return fallbackId;
    }

    private void scheduleTempoRemindersNow(String json) {
        try {
            JSONArray items = new JSONArray(json);
            if (items.length() == 0) {
                toast("Tidak ada transaksi Tempo untuk dijadwalkan.");
                return;
            }
            long calendarId = findWritableCalendarId();
            if (calendarId < 0) {
                toast("Tidak ditemukan kalender yang dapat ditulis. Pastikan akun Google tersedia di aplikasi Kalender.");
                return;
            }
            int created = 0;
            int alreadyExists = 0;
            int failed = 0;
            android.content.SharedPreferences prefs = getSharedPreferences("asido_tempo_reminders", MODE_PRIVATE);
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String dueDate = item.optString("dueDate", "");
                String customer = item.optString("customer", "Pelanggan");
                String amount = item.optString("amount", "");
                String reference = item.optString("reference", customer + dueDate);
                String[] parts = dueDate.split("-");
                if (parts.length != 3) { failed++; continue; }
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int day = Integer.parseInt(parts[2]);
                Calendar start = Calendar.getInstance();
                start.clear();
                start.set(year, month - 1, day, 9, 0, 0);
                if (start.get(Calendar.YEAR) != year || start.get(Calendar.MONTH) != month - 1 || start.get(Calendar.DAY_OF_MONTH) != day) {
                    failed++;
                    continue;
                }
                Calendar end = (Calendar) start.clone();
                end.add(Calendar.HOUR_OF_DAY, 1);
                String prefKey = "event_" + Integer.toHexString(reference.hashCode());
                if (prefs.contains(prefKey)) {
                    alreadyExists++;
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
                values.put(CalendarContract.Events.TITLE, "Jatuh Tempo ASIDO — " + customer);
                values.put(CalendarContract.Events.DESCRIPTION, "Pengingat pembayaran Tempo ASIDO.\nPelanggan: " + customer + "\nTotal: " + amount + "\nReferensi nota: " + reference + "\nDibuat otomatis oleh Pembukuan ASIDO.");
                values.put(CalendarContract.Events.DTSTART, start.getTimeInMillis());
                values.put(CalendarContract.Events.DTEND, end.getTimeInMillis());
                values.put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().getID());
                values.put(CalendarContract.Events.HAS_ALARM, 1);
                values.put(CalendarContract.Events.ALL_DAY, 0);
                Uri eventUri = getContentResolver().insert(CalendarContract.Events.CONTENT_URI, values);
                if (eventUri == null) { failed++; continue; }
                long eventId = ContentUris.parseId(eventUri);
                ContentValues reminder = new ContentValues();
                reminder.put(CalendarContract.Reminders.EVENT_ID, eventId);
                reminder.put(CalendarContract.Reminders.MINUTES, 0);
                reminder.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
                getContentResolver().insert(CalendarContract.Reminders.CONTENT_URI, reminder);
                prefs.edit().putLong(prefKey, eventId).apply();
                created++;
            }
            toast(created + " pengingat Tempo dibuat di kalender. " + alreadyExists + " sudah pernah dibuat. " + failed + " gagal.");
        } catch (Exception e) {
            toast("Gagal membuat pengingat Google Calendar: " + (e.getMessage() == null ? "kesalahan kalender" : e.getMessage()));
        }
    }

    private void createCalendarEvent(String note, String customer) {
        try {
            Matcher m = Pattern.compile("(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{2,4})").matcher(note == null ? "" : note);
            if (!m.find()) { toast("Cantumkan tanggal seperti 30/10/26 di Catatan."); return; }
            int day = Integer.parseInt(m.group(1));
            int month = Integer.parseInt(m.group(2));
            int year = Integer.parseInt(m.group(3));
            if (year < 100) year += 2000;
            Calendar start = Calendar.getInstance();
            start.clear();
            start.set(year, month - 1, day, 9, 0, 0);
            if (start.get(Calendar.DAY_OF_MONTH) != day || start.get(Calendar.MONTH) != month - 1) {
                toast("Tanggal pengingat tidak valid.");
                return;
            }
            Calendar end = (Calendar) start.clone();
            end.add(Calendar.HOUR_OF_DAY, 1);
            Intent event = new Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI);
            event.putExtra(CalendarContract.Events.TITLE, "Pengingat pembayaran — " + (customer == null ? "Pelanggan" : customer));
            event.putExtra(CalendarContract.Events.DESCRIPTION, note + "\nPengingat dari ASIDO Nota Digital.");
            event.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.getTimeInMillis());
            event.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.getTimeInMillis());
            startActivity(event);
        } catch (Exception e) {
            toast("Tidak dapat membuka Kalender: " + e.getMessage());
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
