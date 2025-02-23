// android/app/src/main/java/com/detoxdial/HomeActivity.java
package com.detoxdial;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public class HomeActivity extends AppCompatActivity {
    private TextView screenTimeText;
    private PieChart usageChart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        TextView personalityText = findViewById(R.id.personalityText);
        screenTimeText = findViewById(R.id.screenTimeText);
        usageChart = findViewById(R.id.usageChart);
        Button startChatButton = findViewById(R.id.startChatButton);

        SharedPreferences prefs = getSharedPreferences("MBTI", MODE_PRIVATE);
        String finalPersonality = prefs.getString("finalPersonality", "Unknown");
        personalityText.setText("Personality: " + finalPersonality);

        if (!hasUsageStatsPermission()) {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        }
        loadUsageStats();

        startChatButton.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, ChatActivity.class);
            intent.putExtra("personality", finalPersonality);
            startActivity(intent);
        });
    }

    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void loadUsageStats() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        Calendar calendar = Calendar.getInstance();
        long endTime = calendar.getTimeInMillis();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        long startTime = calendar.getTimeInMillis();

        List<UsageStats> usageStatsList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime);
        if (usageStatsList == null || usageStatsList.isEmpty()) {
            screenTimeText.setText("Screen time data not available.");
            return;
        }

        long totalTime = 0;
        SortedMap<String, Long> appUsage = new TreeMap<>();
        for (UsageStats usage : usageStatsList) {
            long timeUsed = usage.getTotalTimeInForeground();
            if (timeUsed > 0) {
                totalTime += timeUsed;
                String pkg = usage.getPackageName();
                appUsage.put(pkg, appUsage.getOrDefault(pkg, 0L) + timeUsed);
            }
        }
        long minutes = totalTime / (1000 * 60);
        screenTimeText.setText("Today's Screen Time: " + minutes + " minutes");

        List<PieEntry> entries = new ArrayList<>();
        int count = 0;
        for (String pkg : appUsage.keySet()) {
            if (count++ >= 5) break;
            long time = appUsage.get(pkg);
            entries.add(new PieEntry(time / (1000 * 60), pkg));
        }
        PieDataSet dataSet = new PieDataSet(entries, "App Usage (minutes)");
        PieData pieData = new PieData(dataSet);
        usageChart.setData(pieData);
        usageChart.invalidate();
    }
}
