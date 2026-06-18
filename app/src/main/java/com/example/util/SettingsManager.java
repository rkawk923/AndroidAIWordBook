package com.example.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.BuildConfig;

/** 앱 설정의 SharedPreferences 접근을 한 곳에서 관리합니다. */
public final class SettingsManager {

    private static final String PREFS_NAME = "WordCatSettings";
    private static final String KEY_GEMINI_API_KEY = "gemini_api_key";
    private static final String KEY_SHOW_WORD_ON_START = "show_word_on_start";
    private static final String KEY_SHOW_MEANING_ON_START = "show_meaning_on_start";
    private static final String PLACEHOLDER_API_KEY = "MY_GEMINI_API_KEY";

    private SettingsManager() {
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void saveUserGeminiApiKey(Context context, String apiKey) {
        preferences(context).edit()
                .putString(KEY_GEMINI_API_KEY, normalize(apiKey))
                .apply();
    }

    public static String getUserGeminiApiKey(Context context) {
        return normalize(preferences(context).getString(KEY_GEMINI_API_KEY, ""));
    }

    public static boolean hasUserGeminiApiKey(Context context) {
        return !getUserGeminiApiKey(context).isEmpty();
    }

    public static void deleteUserGeminiApiKey(Context context) {
        preferences(context).edit().remove(KEY_GEMINI_API_KEY).apply();
    }

    public static String getResolvedGeminiApiKey(Context context) {
        if (context != null) {
            String userApiKey = getUserGeminiApiKey(context);
            if (!userApiKey.isEmpty()) {
                return userApiKey;
            }
        }
        return getBuildConfigGeminiApiKey();
    }

    public static boolean isGeminiApiConfigured(Context context) {
        return !getResolvedGeminiApiKey(context).isEmpty();
    }

    public static boolean isShowWordOnStart(Context context) {
        return preferences(context).getBoolean(KEY_SHOW_WORD_ON_START, true);
    }

    public static void setShowWordOnStart(Context context, boolean show) {
        preferences(context).edit().putBoolean(KEY_SHOW_WORD_ON_START, show).apply();
    }

    public static boolean isShowMeaningOnStart(Context context) {
        return preferences(context).getBoolean(KEY_SHOW_MEANING_ON_START, false);
    }

    public static void setShowMeaningOnStart(Context context, boolean show) {
        preferences(context).edit().putBoolean(KEY_SHOW_MEANING_ON_START, show).apply();
    }

    private static String getBuildConfigGeminiApiKey() {
        String apiKey = normalize(BuildConfig.GEMINI_API_KEY);
        return PLACEHOLDER_API_KEY.equals(apiKey) ? "" : apiKey;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
