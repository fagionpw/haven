package org.havenapp.main.alerts;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import com.google.gson.JsonObject;
import org.havenapp.main.PreferenceManager;
import org.havenapp.main.model.EventTrigger;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class TelegramAlertChannel implements AlertChannel {
    private static final String TAG = "TelegramAlertChannel";
    private Context context;
    private PreferenceManager prefs;

    public TelegramAlertChannel(Context context) {
        this.context = context;
        this.prefs = new PreferenceManager(context);
    }

    @Override
    public boolean isEnabled() {
        return prefs.getTelegramEnabled() &&
                !TextUtils.isEmpty(prefs.getTelegramBotToken()) &&
                !TextUtils.isEmpty(prefs.getTelegramChatId());
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void sendAlert(String message, String mediaPath, int eventType) throws Exception {
        String botToken = prefs.getTelegramBotToken();
        String chatId = prefs.getTelegramChatId();

        if (TextUtils.isEmpty(botToken) || TextUtils.isEmpty(chatId)) {
            throw new Exception("Telegram Bot Token or Chat ID not configured");
        }

        if (!TextUtils.isEmpty(mediaPath) && new File(mediaPath).exists()) {
            String method;
            String partName;
            
            if (eventType == EventTrigger.CAMERA) {
                method = "sendPhoto";
                partName = "photo";
            } else if (eventType == EventTrigger.MICROPHONE) {
                method = "sendAudio";
                partName = "audio";
            } else if (eventType == EventTrigger.CAMERA_VIDEO) {
                method = "sendVideo";
                partName = "video";
            } else {
                method = "sendDocument";
                partName = "document";
            }
            
            sendTelegramFile(botToken, chatId, message, mediaPath, method, partName);
        } else {
            sendTelegramMessage(botToken, chatId, message);
        }
    }

    private void sendTelegramMessage(String botToken, String chatId, String text) throws Exception {
        String urlString = "https://api.telegram.org/bot" + botToken + "/sendMessage";
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);

        JsonObject json = new JsonObject();
        json.addProperty("chat_id", chatId);
        json.addProperty("text", text);
        String jsonInputString = json.toString();

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new Exception("Failed to send Telegram message, response code: " + responseCode);
        }
        
        Log.d(TAG, "Telegram message sent successfully");
    }

    private void sendTelegramFile(String botToken, String chatId, String caption, String filePath, String method, String partName) throws Exception {
        String urlString = "https://api.telegram.org/bot" + botToken + "/" + method;
        String boundary = "---" + System.currentTimeMillis() + "---";
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            // Chat ID part
            os.write(("--" + boundary + "\r\n").getBytes());
            os.write(("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n").getBytes());
            os.write((chatId + "\r\n").getBytes());

            // Caption part
            if (!TextUtils.isEmpty(caption)) {
                os.write(("--" + boundary + "\r\n").getBytes());
                os.write(("Content-Disposition: form-data; name=\"caption\"\r\n\r\n").getBytes());
                os.write((caption + "\r\n").getBytes());
            }

            // File part
            File file = new File(filePath);
            os.write(("--" + boundary + "\r\n").getBytes());
            os.write(("Content-Disposition: form-data; name=\"" + partName + "\"; filename=\"" + file.getName() + "\"\r\n").getBytes());
            os.write(("Content-Type: application/octet-stream\r\n\r\n").getBytes());

            try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
                byte[] buffer = new byte[4096];
                int n;
                while ((n = is.read(buffer)) != -1) {
                    os.write(buffer, 0, n);
                }
            }
            os.write("\r\n".getBytes());

            // End boundary
            os.write(("--" + boundary + "--\r\n").getBytes());
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new Exception("Failed to send Telegram file (" + method + "), response code: " + responseCode);
        }

        Log.d(TAG, "Telegram file sent successfully: " + method);
    }

    @Override
    public String getChannelName() {
        return "Telegram";
    }

    @Override
    public void configure(String... params) {
        if (params.length > 0) {
            prefs.setTelegramBotToken(params[0]);
        }
        if (params.length > 1) {
            prefs.setTelegramChatId(params[1]);
        }
        if (params.length > 2) {
            prefs.setTelegramEnabled(Boolean.parseBoolean(params[2]));
        }
    }

    @Override
    public boolean requiresConfiguration() {
        return TextUtils.isEmpty(prefs.getTelegramBotToken()) ||
                TextUtils.isEmpty(prefs.getTelegramChatId());
    }
}
