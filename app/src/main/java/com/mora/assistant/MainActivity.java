package com.mora.assistant;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private TextView tvChatLog, tvStatus;
    private EditText etCommand;
    private ScrollView scrollLog;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ImageView ivLogo = findViewById(R.id.ivLogo);
        tvChatLog = findViewById(R.id.tvChatLog);
        tvStatus = findViewById(R.id.tvStatus);
        etCommand = findViewById(R.id.etCommand);
        scrollLog = findViewById(R.id.scrollLog);
        Button btnSend = findViewById(R.id.btnSend);
        Button btnAccessibility = findViewById(R.id.btnAccessibility);
        Button btnNotification = findViewById(R.id.btnNotification);

        ObjectAnimator pulse = ObjectAnimator.ofPropertyValuesHolder(
                ivLogo,
                PropertyValuesHolder.ofFloat("scaleX", 0.94f, 1.06f),
                PropertyValuesHolder.ofFloat("scaleY", 0.94f, 1.06f)
        );
        pulse.setDuration(1100);
        pulse.setRepeatCount(ObjectAnimator.INFINITE);
        pulse.setRepeatMode(ObjectAnimator.REVERSE);
        pulse.start();

        btnAccessibility.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        btnNotification.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

        appendLine("Mora: আসসালামু আলাইকুম! আমি মোরা—আপনার অ্যান্ড্রয়েড ১১ স্মার্ট অ্যাসিস্ট্যান্ট।");
        btnSend.setOnClickListener(v -> handleUserInput());
    }

    private void handleUserInput() {
        String input = etCommand.getText().toString().trim();
        if (input.isEmpty()) return;
        etCommand.setText("");
        appendLine("You: " + input);

        if (input.toLowerCase().contains("whatsapp")) {
            Intent launch = getPackageManager().getLaunchIntentForPackage("com.whatsapp");
            if (launch != null) {
                startActivity(launch);
                tvStatus.setText("WhatsApp খোলা হয়েছে — স্ক্রিন অ্যাকশন প্রস্তুত");
                appendLine("Mora: WhatsApp খোলা হয়েছে। Accessibility চালু থাকলে স্ক্রিনের কন্ট্যাক্ট/ইনপুট অ্যাকশন চেষ্টা করা যাবে।");
                mainHandler.postDelayed(() -> {
                    MoraAccessibilityService service = MoraAccessibilityService.getInstance();
                    if (service != null) {
                        service.clickTextOrDesc("search");
                    }
                }, 1800);
                return;
            }
        }

        tvStatus.setText("চিন্তা করছি (Timeout: 35s)...");
        new Thread(() -> callGemini(input)).start();
    }

    private void callGemini(String prompt) {
        HttpURLConnection conn = null;
        try {
            String apiKey = BuildConfig.GEMINI_API_KEY;
            if (apiKey == null || apiKey.trim().isEmpty()) {
                mainHandler.post(() -> {
                    tvStatus.setText("API Key সেট করা নেই");
                    appendLine("Error: GEMINI_API_KEY পাওয়া যায়নি।");
                });
                return;
            }

            URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(35000);
            conn.setReadTimeout(35000);
            conn.setDoOutput(true);

            JSONObject part = new JSONObject().put("text", prompt);
            JSONObject content = new JSONObject().put("parts", new JSONArray().put(part));
            JSONObject payload = new JSONObject().put("contents", new JSONArray().put(content));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(),
                    StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            if (code >= 200 && code < 300) {
                JSONObject resJson = new JSONObject(sb.toString());
                String reply = resJson.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .optString("text", "উত্তর পাওয়া যায়নি।");
                mainHandler.post(() -> {
                    tvStatus.setText("Mora Ready (Android 11)");
                    appendLine("Mora: " + reply.trim());
                });
            } else {
                mainHandler.post(() -> {
                    tvStatus.setText("API Error (" + code + ")");
                    appendLine("Error: HTTP " + code + " - " + sb.toString());
                });
            }
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "timeout";
            mainHandler.post(() -> {
                tvStatus.setText("Connection Timeout / Error");
                appendLine("Error: " + msg);
            });
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void appendLine(String text) {
        String current = tvChatLog.getText().toString();
        String updated = current.isEmpty() ? text : current + "\n" + text;
        tvChatLog.setText(updated);
        tvChatLog.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_slide_in));
        scrollLog.post(() -> scrollLog.fullScroll(ScrollView.FOCUS_DOWN));
    }
}
