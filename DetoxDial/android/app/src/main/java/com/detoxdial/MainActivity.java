package com.detoxdial;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.widget.Toast;
import android.content.Intent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String BACKEND_URL = "http://10.0.2.2:5000";  // Points to localhost when running in emulator
    private static final String TAG = "MainActivity";
    private ProgressBar progressBar;
    private TextView loadingText;
    private int currentQuestionIndex = 0;
    private List<String> userAnswers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "Testing connection to: " + BACKEND_URL);

        // Initialize UI elements
        progressBar = findViewById(R.id.progressBar);
        loadingText = findViewById(R.id.loadingText);

        // Test backend connection first
        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

        Request pingRequest = new Request.Builder()
            .url(BACKEND_URL + "/ping")
            .build();

        Log.d(TAG, "Sending ping request...");
        client.newCall(pingRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Backend ping failed", e);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, 
                        "Cannot connect to server: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show();
                    loadingText.setText("Connection failed. Please check your network.");
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                Log.d(TAG, "Ping response: " + responseBody);
                
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Backend returned error: " + response.code() + ", body: " + responseBody);
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, 
                            "Server error: " + response.code(), 
                            Toast.LENGTH_LONG).show();
                        loadingText.setText("Server error. Please try again.");
                    });
                    return;
                }
                // If ping successful, proceed with MBTI test
                loadMBTITest();
            }
        });
    }

    private void loadMBTITest() {
        Log.d(TAG, "Loading MBTI test...");
        SharedPreferences prefs = getSharedPreferences("MBTI", MODE_PRIVATE);
        
        // Reset test status for testing (remove this in production)
        prefs.edit().clear().apply();
        
        boolean hasCompletedTest = prefs.getBoolean("hasCompletedTest", false);
        Log.d(TAG, "Has completed test: " + hasCompletedTest);

        if (!hasCompletedTest) {
            runOnUiThread(() -> {
                loadingText.setText("Loading MBTI questions...");
            });

            OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

            Request request = new Request.Builder()
                .url(BACKEND_URL + "/mbti/questions")
                .build();

            Log.d(TAG, "Fetching MBTI questions...");
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Failed to fetch questions", e);
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, 
                            "Failed to load questions: " + e.getMessage(), 
                            Toast.LENGTH_LONG).show();
                        loadingText.setText("Failed to load questions. Please try again.");
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "Server error: " + response.code());
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this,
                                "Server error: " + response.code(),
                                Toast.LENGTH_LONG).show();
                            loadingText.setText("Server error. Please try again.");
                        });
                        return;
                    }

                    String responseBody = response.body().string();
                    Log.d(TAG, "Questions response: " + responseBody);
                    
                    try {
                        JSONObject json = new JSONObject(responseBody);
                        JSONArray questionsArray = json.getJSONArray("questions");
                        List<String> mbtiQuestions = new ArrayList<>();
                        for (int i = 0; i < questionsArray.length(); i++) {
                            mbtiQuestions.add(questionsArray.getString(i));
                        }
                        
                        Log.d(TAG, "Loaded " + mbtiQuestions.size() + " questions");
                        
                        runOnUiThread(() -> {
                            // Show questions UI
                            setContentView(R.layout.activity_questions);
                            setupQuestionsUI(mbtiQuestions);
                        });
                    } catch (JSONException e) {
                        Log.e(TAG, "JSON parsing error", e);
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this,
                                "Error parsing questions",
                                Toast.LENGTH_LONG).show();
                            loadingText.setText("Error loading questions. Please try again.");
                        });
                    }
                }
            });
        } else {
            // Test already completed, go to home screen
            Log.d(TAG, "MBTI test already completed, going to home screen");
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        }
    }

    private void setupQuestionsUI(List<String> questions) {
        Log.d(TAG, "Setting up questions UI");
        
        TextView questionText = findViewById(R.id.questionText);
        Button optionAButton = findViewById(R.id.optionAButton);
        Button optionBButton = findViewById(R.id.optionBButton);
        TextView progressText = findViewById(R.id.progressText);
        ProgressBar questionProgressBar = findViewById(R.id.questionProgressBar);

        // Show first question
        showQuestion(questions, questionText, optionAButton, optionBButton, progressText, questionProgressBar);

        // Set up button click listeners
        optionAButton.setOnClickListener(v -> {
            userAnswers.add("A");
            nextQuestion(questions, questionText, optionAButton, optionBButton, progressText, questionProgressBar);
        });

        optionBButton.setOnClickListener(v -> {
            userAnswers.add("B");
            nextQuestion(questions, questionText, optionAButton, optionBButton, progressText, questionProgressBar);
        });
    }

    private void showQuestion(List<String> questions, TextView questionText, Button optionA, Button optionB, 
                            TextView progressText, ProgressBar progressBar) {
        String question = questions.get(currentQuestionIndex);
        String[] options = parseQuestionOptions(question);
        
        questionText.setText(question);
        optionA.setText(options[0]);
        optionB.setText(options[1]);
        
        progressText.setText(String.format("Question %d of %d", currentQuestionIndex + 1, questions.size()));
        progressBar.setProgress(currentQuestionIndex + 1);
    }

    private void nextQuestion(List<String> questions, TextView questionText, Button optionA, Button optionB, 
                            TextView progressText, ProgressBar progressBar) {
        currentQuestionIndex++;
        
        if (currentQuestionIndex < questions.size()) {
            showQuestion(questions, questionText, optionA, optionB, progressText, progressBar);
        } else {
            // Test completed
            finishTest();
        }
    }

    private String[] parseQuestionOptions(String question) {
        // Parse "X or Y" format questions
        String[] parts = question.split(" or ");
        if (parts.length == 2) {
            return parts;
        }
        // Default options if question format is different
        return new String[]{"Yes", "No"};
    }

    private void finishTest() {
        Log.d(TAG, "Test completed. Answers: " + userAnswers);
        
        // Calculate MBTI type based on answers
        int introvertScore = 0;
        int intuitiveScore = 0;
        int thinkingScore = 0;
        int judgingScore = 0;
        
        // Questions 1,5,9,13 determine E/I
        introvertScore += userAnswers.get(0).equalsIgnoreCase("No") ? 1 : 0;
        introvertScore += userAnswers.get(4).equalsIgnoreCase("No") ? 1 : 0;
        introvertScore += userAnswers.get(8).equalsIgnoreCase("No") ? 1 : 0;
        introvertScore += userAnswers.get(12).equalsIgnoreCase("No") ? 1 : 0;
        
        // Questions 2,6,10,14 determine S/N
        intuitiveScore += userAnswers.get(1).equalsIgnoreCase("No") ? 1 : 0;
        intuitiveScore += userAnswers.get(5).equalsIgnoreCase("No") ? 1 : 0;
        intuitiveScore += userAnswers.get(9).equalsIgnoreCase("No") ? 1 : 0;
        intuitiveScore += userAnswers.get(13).equalsIgnoreCase("No") ? 1 : 0;
        
        // Questions 3,7,11,15 determine T/F
        thinkingScore += userAnswers.get(2).equalsIgnoreCase("Yes") ? 1 : 0;
        thinkingScore += userAnswers.get(6).equalsIgnoreCase("Yes") ? 1 : 0;
        thinkingScore += userAnswers.get(10).equalsIgnoreCase("Yes") ? 1 : 0;
        thinkingScore += userAnswers.get(14).equalsIgnoreCase("Yes") ? 1 : 0;
        
        // Questions 4,8,12,16 determine J/P
        judgingScore += userAnswers.get(3).equalsIgnoreCase("Yes") ? 1 : 0;
        judgingScore += userAnswers.get(7).equalsIgnoreCase("Yes") ? 1 : 0;
        judgingScore += userAnswers.get(11).equalsIgnoreCase("Yes") ? 1 : 0;
        judgingScore += userAnswers.get(15).equalsIgnoreCase("Yes") ? 1 : 0;
        
        // Determine each letter based on majority
        String personalityType = "";
        personalityType += introvertScore >= 2 ? "I" : "E";
        personalityType += intuitiveScore >= 2 ? "N" : "S";
        personalityType += thinkingScore >= 2 ? "T" : "F";
        personalityType += judgingScore >= 2 ? "J" : "P";
        
        Log.d(TAG, "Calculated personality type: " + personalityType);
        
        // Save completion status and personality type
        SharedPreferences prefs = getSharedPreferences("MBTI", MODE_PRIVATE);
        prefs.edit()
            .putBoolean("hasCompletedTest", true)
            .putString("finalPersonality", personalityType)
            .apply();
        
        // Start HomeActivity
        Intent intent = new Intent(this, HomeActivity.class);
        intent.putExtra("personality", personalityType);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /**
     * Example method to parse the OpenAI JSON response for MBTI questions.
     * Adjust to match your actual JSON response structure.
     */
    private List<String> parseQuestions(String responseBody) {
        List<String> questionList = new ArrayList<>();
        try {
            // Example for a Chat Completions response:
            // {
            //   "id": "...",
            //   "choices": [
            //     {
            //       "message": {
            //         "role": "assistant",
            //         "content": "1) ...\n2) ..."
            //       }
            //     }
            //   ]
            // }
            JSONObject root = new JSONObject(responseBody);
            JSONArray choices = root.getJSONArray("choices");
            if (choices.length() > 0) {
                JSONObject firstChoice = choices.getJSONObject(0);
                JSONObject messageObj = firstChoice.getJSONObject("message");
                String content = messageObj.getString("content");

                // Simple split by new lines:
                for (String line : content.split("\n")) {
                    // Filter out blank lines or numbering. Adjust as needed.
                    String cleaned = line.replaceAll("^\\d+\\)|\\d+\\.|^\\s+|-\\s+", "").trim();
                    if (!cleaned.isEmpty()) {
                        questionList.add(cleaned);
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return questionList;
    }
}