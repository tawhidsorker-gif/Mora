package com.mora.assistant;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY;
    private static final String SYSTEM_PROMPT =
        "You are Mora, a smart personal assistant for an Android 11 phone. " +
        "Reply ONLY in valid JSON format (no markdown, no extra text). " +
        "For phone actions use: {\"type\":\"command\",\"action\":\"open_app|check_notifications|send_message|change_setting|shutdown\",\"target\":\"<name>\",\"message\":\"<text>\",\"reply\":\"<short bangla>\"}. " +
        "For normal chat or knowledge use: {\"type\":\"chat\",\"reply\":\"<helpful bangla answer>\"}.";

    private TextView chatOutput;
    private EditText inputBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        Button btnAcc = new Button(this);
        btnAcc.setText("Enable Accessibility");
        btnAcc.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(btnAcc);

        Button btnNotif = new Button(this);
        btnNotif.setText("Enable Notification Access");
        btnNotif.setOnClickListener(v -> startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
        root.addView(btnNotif);

        ScrollView scroll = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        scroll.setLayoutParams(scrollParams);

        chatOutput = new TextView(this);
        chatOutput.setText("Mora Ready (Android 11)\n\n");
        chatOutput.setTextSize(15f);
        scroll.addView(chatOutput);
        root.addView(scroll);

        inputBox = new EditText(this);
        inputBox.setHint("Ask Mora or give a command...");
        root.addView(inputBox);

        Button btnSend = new Button(this);
        btnSend.setText("Send");
        btnSend.setOnClickListener(v -> {
            String msg = inputBox.getText().toString().trim();
            if (!msg.isEmpty()) {
                inputBox.setText("");
                appendLine("You: " + msg);
                new Thread(() -> handlePrompt(msg)).start();
            }
        });
        root.addView(btnSend);

        setContentView(root);
    }

    private void appendLine(String line) {
        runOnUiThread(() -> chatOutput.append(line + "\n"));
    }

    private void handlePrompt(String prompt) {
        try {
            if (GEMINI_API_KEY == null || GEMINI_API_KEY.isEmpty()) {
                appendLine("Error: GEMINI_API_KEY is not configured.");
                return;
            }
            String rawJson = callGemini(prompt).replace("```json", "").replace("```", "").trim();
            JSONObject obj = new JSONObject(rawJson);
            String type = obj.optString("type", "chat");
            String reply = obj.optString("reply", "");

            if ("command".equals(type)) {
                String action = obj.optString("action", "");
                String target = obj.optString("target", "");
                appendLine("Mora: " + reply);
                if ("open_app".equals(action)) {
                    boolean opened = openAppByName(target);
                    appendLine(opened ? "Opened: " + target : "App not found: " + target);
                } else if ("check_notifications".equals(action)) {
                    List<String> notifs = MoraNotificationService.getRecentNotifications();
                    appendLine("Notifications (" + notifs.size() + "):");
                    for (String n : notifs) {
                        appendLine(" - " + n);
                    }
                } else if ("change_setting".equals(action)) {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                }
            } else {
                appendLine("Mora: " + reply);
            }
        } catch (Exception e) {
            appendLine("Error: " + e.getMessage());
        }
    }

    private boolean openAppByName(String appName) {
        PackageManager pm = getPackageManager();
        String query = appName.toLowerCase(Locale.ROOT).trim();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo info : apps) {
            String label = pm.getApplicationLabel(info).toString().toLowerCase(Locale.ROOT);
            String pkg = info.packageName.toLowerCase(Locale.ROOT);
            if (label.contains(query) || pkg.contains(query)) {
                Intent launch = pm.getLaunchIntentForPackage(info.packageName);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(launch);
                    return true;
                }
            }
        }
        return false;
    }

    private String callGemini(String userPrompt) throws Exception {
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + GEMINI_API_KEY;
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setDoOutput(true);
        conn.setConnectTimeout(25000);
        conn.setReadTimeout(25000);

        JSONObject part = new JSONObject().put("text", SYSTEM_PROMPT + "\n\nUser: " + userPrompt);
        JSONObject content = new JSONObject().put("parts", new JSONArray().put(part));
        JSONObject payload = new JSONObject().put("contents", new JSONArray().put(content));

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();

        JSONObject res = new JSONObject(sb.toString());
        return res.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text");
    }
}
