package com.example.service;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.util.SettingsManager;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Gemini 3.5 Flash API 활용을 위한 네트워크 연동 헬퍼 클래스입니다.
 * 비즈니스 단어 자동생성, PDF 기출 단어 추출 기능 등 모든 스레드 처리를 안전하게 분리하고 백그라운드 연동을 보장합니다.
 */
public class GeminiHelper {

    private static final String TAG = "GeminiHelper";
    private static final String MODEL_NAME = "gemini-2.5-flash";
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
            + MODEL_NAME + ":generateContent";
    private static final int LOG_CHUNK_SIZE = 3000;
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 1500L;
    static final String SERVER_BUSY_MESSAGE = "AI 서버가 현재 혼잡합니다. 잠시 후 다시 시도해주세요.";

    private final OkHttpClient client;
    private final Moshi moshi;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final Context appContext;

    public interface GeminiCallback {
        void onSuccess(List<WordResult> results);
        void onError(String errorMessage);
    }

    static class GeminiUnavailableException extends IOException {
        GeminiUnavailableException() {
            super(SERVER_BUSY_MESSAGE);
        }
    }

    public static class WordResult {
        public String word;
        public String meaning;

        public WordResult(String word, String meaning) {
            this.word = word;
            this.meaning = meaning;
        }
    }

    public GeminiHelper() {
        this(null);
    }

    public GeminiHelper(Context context) {
        this.appContext = context == null ? null : context.getApplicationContext();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        this.moshi = new Moshi.Builder().build();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static boolean isApiConfigured(Context context) {
        return SettingsManager.isGeminiApiConfigured(context);
    }

    /**
     * 입력된 미완성 단어 목록의 뜻을 AI로 자동 완성하여 반환하는 메소드
     */
    public void fillMeanings(final List<String> words, final GeminiCallback callback) {
        executor.execute(() -> {
            try {
                if (words == null || words.isEmpty()) {
                    postError(callback, "입력된 단어가 없습니다.");
                    return;
                }

                // API Key 체크
                if (!isApiConfigured(appContext)) {
                    postError(callback, "Gemini API Key가 설정되지 않았습니다.");
                    return;
                }

                // 프롬프트 구성
                StringBuilder wordListStr = new StringBuilder();
                for (String w : words) {
                    if (w != null && !w.trim().isEmpty()) {
                        wordListStr.append("- ").append(w).append("\n");
                    }
                }

                String prompt = "You are an expert English-Korean vocabulary learning assistant.\n" +
                        "For the following list of English words, generate appropriate and precise Korean vocabulary meanings or brief parts of speech (keep it short and clean like: '사과 (명사)', '협력하다 (동사)').\n" +
                        "Here are the English words:\n" +
                        wordListStr.toString() +
                        "\n" +
                        "You MUST return the output ONLY as a raw, valid JSON array of objects, where each object has fields \"word\" and \"meaning\". Do not wrap in markdown or any other format than pure JSON array text.\n" +
                        "Example output:\n" +
                        "[{\"word\": \"Apple\", \"meaning\": \"사과\"}]";

                String responseText = performGeminiRequest(prompt, null);
                List<WordResult> results = parseJsonArray(responseText);
                if (results != null && !results.isEmpty()) {
                    postSuccess(callback, results);
                } else {
                    postError(callback, "AI 결과 데이터 분석에 실패했습니다.");
                }

            } catch (Exception e) {
                Log.e(TAG, "Exception during fillMeanings", e);
                postError(
                        callback,
                        e instanceof GeminiUnavailableException
                                ? SERVER_BUSY_MESSAGE
                                : "오류가 발생했습니다: " + e.getMessage()
                );
            }
        });
    }

    /**
     * PDF 렌더링된 첫 페이지 이미지(Base64)로부터 중요 학습 단어와 뜻을 AI가 추출해주는 프리미엄 기능
     */
    public void extractWordsFromPdfImage(final String base64Image, final GeminiCallback callback) {
        executor.execute(() -> {
            try {
                if (!isApiConfigured(appContext)) {
                    postError(callback, "Gemini API Key가 설정되지 않았습니다.");
                    return;
                }

                List<WordResult> results = extractWordsFromPdfImageBlocking(base64Image);
                if (results != null && !results.isEmpty()) {
                    postSuccess(callback, results);
                } else {
                    postError(callback, "PDF 이미지에서 어휘를 추출할 수 없거나 형식이 어긋납니다.");
                }

            } catch (Exception e) {
                Log.e(TAG, "Exception during extractWordsFromPdfImage", e);
                postError(
                        callback,
                        e instanceof GeminiUnavailableException
                                ? SERVER_BUSY_MESSAGE
                                : "PDF AI 분석 실행 도중 장애가 발생했습니다: " + e.getMessage()
                );
            }
        });
    }

    List<WordResult> extractWordsFromPdfImageBlocking(String base64Image) throws IOException {
        if (!isApiConfigured(appContext)) {
            throw new IOException("Gemini API Key가 설정되지 않았습니다.");
        }
        if (base64Image == null || base64Image.isEmpty()) {
            throw new IOException("PDF 페이지 이미지 생성에 실패했습니다.");
        }

        String prompt = "This is one page of a PDF learning document.\n" +
                "Read the page carefully and extract up to 10 important English vocabulary words with clear Korean meanings.\n" +
                "Return ONLY a valid JSON array of objects with fields \"word\" and \"meaning\". Do not use markdown.";

        String responseText = performGeminiRequest(prompt, base64Image);
        List<WordResult> results = parseJsonArray(responseText);
        return results != null ? results : new ArrayList<>();
    }

    private String performGeminiRequest(String textPrompt, String base64Image) throws IOException {
        MediaType jsonMedia = MediaType.get("application/json; charset=utf-8");

        // Request Body 구성
        Map<String, Object> requestMap = new HashMap<>();
        List<Map<String, Object>> contentsList = new ArrayList<>();
        Map<String, Object> contentMap = new HashMap<>();
        List<Map<String, Object>> partsList = new ArrayList<>();

        // Part 1: text prompt
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", textPrompt);
        partsList.add(textPart);

        // Part 2: image details (multimodal) if exists
        if (base64Image != null && !base64Image.isEmpty()) {
            Map<String, Object> imagePart = new HashMap<>();
            Map<String, Object> inlineData = new HashMap<>();
            inlineData.put("mimeType", "image/jpeg");
            inlineData.put("data", base64Image);
            imagePart.put("inlineData", inlineData);
            partsList.add(imagePart);
        }

        contentMap.put("parts", partsList);
        contentsList.add(contentMap);
        requestMap.put("contents", contentsList);

        // Generation Config for JSON raw output
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        requestMap.put("generationConfig", generationConfig);

        String requestJson = moshi.adapter(Map.class).toJson(requestMap);

        String apiKey = SettingsManager.getResolvedGeminiApiKey(appContext);
        if (apiKey.isEmpty()) {
            throw new IOException("Gemini API Key가 설정되지 않았습니다.");
        }
        String fullUrl = API_URL + "?key=" + apiKey;
        String redactedUrl = API_URL + "?key=<redacted>";

        Request request = new Request.Builder()
                .url(fullUrl)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(requestJson, jsonMedia))
                .build();

        Log.d(TAG, "Gemini request URL: " + redactedUrl);
        Log.d(TAG, "Gemini request Content-Type: " + jsonMedia);
        Log.d(TAG, "Gemini API key configured: " + isApiConfigured(appContext));
        logLong(Log.DEBUG, "Gemini request JSON: " + createSafeRequestLog(textPrompt, base64Image));

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try (Response response = client.newCall(request).execute()) {
                Log.d(TAG, "Gemini request attempt: " + (attempt + 1) + "/" + (MAX_RETRIES + 1));
                Log.d(TAG, "Gemini response code: " + response.code());
                Log.d(TAG, "Gemini response successful: " + response.isSuccessful());

                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    logLong(Log.ERROR, "Gemini errorBody: " + errorBody);

                    if (response.code() == 503) {
                        if (attempt < MAX_RETRIES) {
                            Log.w(
                                    TAG,
                                    "Gemini server unavailable. Retrying in "
                                            + RETRY_DELAY_MS + "ms."
                            );
                            waitBeforeRetry();
                            continue;
                        }
                        throw new GeminiUnavailableException();
                    }

                    throw new IOException("Gemini API 오류 " + response.code() + ": " + errorBody);
                }
                if (response.body() == null) {
                    throw new IOException("Gemini API 응답 본문이 비어 있습니다.");
                }
                String rawResponse = response.body().string();
                logLong(Log.DEBUG, "Gemini raw response: " + rawResponse);

                // Response의 JSON 파싱하여 text 필드 추출
                return extractTextContentFromGeminiResponse(rawResponse);
            } catch (IOException e) {
                Log.e(
                        TAG,
                        "Gemini request failure. url=" + redactedUrl + ", message=" + e.getMessage(),
                        e
                );
                throw e;
            }
        }

        throw new GeminiUnavailableException();
    }

    private void waitBeforeRetry() throws IOException {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Gemini API 재시도 대기가 중단되었습니다.", e);
        }
    }

    private String createSafeRequestLog(String textPrompt, String base64Image) {
        Map<String, Object> safeRequestMap = new HashMap<>();
        List<Map<String, Object>> safeContents = new ArrayList<>();
        Map<String, Object> safeContent = new HashMap<>();
        List<Map<String, Object>> safeParts = new ArrayList<>();

        Map<String, Object> safeTextPart = new HashMap<>();
        safeTextPart.put("text", textPrompt);
        safeParts.add(safeTextPart);

        if (base64Image != null && !base64Image.isEmpty()) {
            Map<String, Object> safeImagePart = new HashMap<>();
            Map<String, Object> safeInlineData = new HashMap<>();
            safeInlineData.put("mimeType", "image/jpeg");
            safeInlineData.put("data", "<base64 omitted, length=" + base64Image.length() + ">");
            safeImagePart.put("inlineData", safeInlineData);
            safeParts.add(safeImagePart);
        }

        safeContent.put("parts", safeParts);
        safeContents.add(safeContent);
        safeRequestMap.put("contents", safeContents);

        Map<String, Object> safeGenerationConfig = new HashMap<>();
        safeGenerationConfig.put("responseMimeType", "application/json");
        safeRequestMap.put("generationConfig", safeGenerationConfig);

        return moshi.adapter(Map.class).toJson(safeRequestMap);
    }

    private void logLong(int priority, String message) {
        if (message == null || message.isEmpty()) {
            Log.println(priority, TAG, "");
            return;
        }

        for (int start = 0; start < message.length(); start += LOG_CHUNK_SIZE) {
            int end = Math.min(message.length(), start + LOG_CHUNK_SIZE);
            Log.println(priority, TAG, message.substring(start, end));
        }
    }

    private String extractTextContentFromGeminiResponse(String responseJson) throws IOException {
        // Moshi로 간단한 트래버싱 처리
        Map<String, Object> map = moshi.adapter(Map.class).fromJson(responseJson);
        if (map != null && map.containsKey("candidates")) {
            List<?> candidates = (List<?>) map.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Map<?, ?> candidate = (Map<?, ?>) candidates.get(0);
                if (candidate != null && candidate.containsKey("content")) {
                    Map<?, ?> content = (Map<?, ?>) candidate.get("content");
                    if (content != null && content.containsKey("parts")) {
                        List<?> parts = (List<?>) content.get("parts");
                        if (parts != null && !parts.isEmpty()) {
                            Map<?, ?> part = (Map<?, ?>) parts.get(0);
                            if (part != null && part.containsKey("text")) {
                                return (String) part.get("text");
                            }
                        }
                    }
                }
            }
        }
        throw new IOException("Gemini 응답 구조 분석 실패.");
    }

    private List<WordResult> parseJsonArray(String jsonText) {
        if (jsonText == null) return null;
        jsonText = jsonText.trim();
        // ```json 이나 ``` 블록 제거
        if (jsonText.startsWith("```json")) {
            jsonText = jsonText.substring(7);
        } else if (jsonText.startsWith("```")) {
            jsonText = jsonText.substring(3);
        }
        if (jsonText.endsWith("```")) {
            jsonText = jsonText.substring(0, jsonText.length() - 3);
        }
        jsonText = jsonText.trim();

        try {
            Type listType = Types.newParameterizedType(List.class, Map.class);
            JsonAdapter<List<Map<String, String>>> adapter = moshi.adapter(listType);
            List<Map<String, String>> rawList = adapter.fromJson(jsonText);

            List<WordResult> results = new ArrayList<>();
            if (rawList != null) {
                for (Map<String, String> item : rawList) {
                    String word = item.get("word");
                    String meaning = item.get("meaning");
                    if (word != null && meaning != null) {
                        results.add(new WordResult(word, meaning));
                    }
                }
            }
            return results;
        } catch (Exception e) {
            Log.e(TAG, "Moshi parsing failed for text: " + jsonText, e);
            return null;
        }
    }

    private void postSuccess(final GeminiCallback callback, final List<WordResult> results) {
        mainHandler.post(() -> callback.onSuccess(results));
    }

    private void postError(final GeminiCallback callback, final String message) {
        mainHandler.post(() -> callback.onError(message));
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
