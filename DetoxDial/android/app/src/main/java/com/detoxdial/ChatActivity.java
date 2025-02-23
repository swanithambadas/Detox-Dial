// android/app/src/main/java/com/detoxdial/ChatActivity.java
package com.detoxdial;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ChatActivity extends AppCompatActivity {
    private static final String TAG = "ChatActivity";
    private static final String BACKEND_URL = "http://10.0.2.2:5000";
    private EditText messageInput;
    private Button sendButton;
    private TextView chatHistory;
    private String personality;
    private boolean isSending = false; // Prevent multiple simultaneous sends

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Initialize views
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);
        chatHistory = findViewById(R.id.chatHistory);

        // Get personality type from intent or SharedPreferences
        personality = getIntent().getStringExtra("personality");
        if (personality == null) {
            SharedPreferences prefs = getSharedPreferences("MBTI", MODE_PRIVATE);
            personality = prefs.getString("finalPersonality", "NEUTRAL");
        }
        Log.d(TAG, "Personality type: " + personality);

        // Initial greeting
        appendMessage("Assistant", "Hello! I'm your Detox Dial assistant. Let's chat.");

        sendButton.setOnClickListener(v -> {
            if (!isSending) {
                sendMessage();
            }
        });
    }

    private void sendMessage() {
        String message = messageInput.getText().toString().trim();
        if (message.isEmpty()) return;

        // Show user message
        appendMessage("You", message);
        messageInput.setText("");

        // Disable send button while processing
        isSending = true;
        sendButton.setEnabled(false);

        // Create JSON request body
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("personality", personality);
            jsonBody.put("user_message", message);
            String jsonStr = jsonBody.toString();
            Log.d(TAG, "Sending request body: " + jsonStr);

            // Send to backend
            OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

            RequestBody body = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                jsonStr
            );

            Request request = new Request.Builder()
                .url(BACKEND_URL + "/chat")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Chat request failed", e);
                    runOnUiThread(() -> {
                        appendMessage("System", "Error communicating with server: " + e.getMessage());
                        enableSendButton();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    Log.d(TAG, "Response code: " + response.code());
                    Log.d(TAG, "Response body: " + responseBody);

                    runOnUiThread(() -> {
                        if (!response.isSuccessful()) {
                            appendMessage("System", "Server error: " + response.code());
                        } else {
                            try {
                                JSONObject json = new JSONObject(responseBody);
                                String assistantMessage = json.getString("response");
                                appendMessage("Assistant", assistantMessage);
                            } catch (JSONException e) {
                                Log.e(TAG, "Error parsing response", e);
                                appendMessage("System", "Error parsing server response");
                            }
                        }
                        enableSendButton();
                    });
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON", e);
            enableSendButton();
        }
    }

    private void enableSendButton() {
        isSending = false;
        sendButton.setEnabled(true);
    }

    private void appendMessage(String sender, String message) {
        String currentChat = chatHistory.getText().toString();
        String newMessage = sender + ": " + message + "\n\n";
        chatHistory.setText(currentChat + newMessage);
        
        // Scroll to bottom
        final ScrollView scrollView = (ScrollView) chatHistory.getParent();
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }
}
