package com.example;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adapter.VocabularyAdapter;
import com.example.database.AppDatabase;
import com.example.database.VocabularyDao;
import com.example.database.WordDao;
import com.example.model.VocabularyEntity;
import com.example.model.WordEntity;
import com.example.service.GeminiHelper;
import com.example.service.PdfWordExtractor;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 어플리케이션의 메인 대시보드 액티비티입니다.
 * 하단바의 고유 세가지 탭을 한 화면 안에서 뷰 전환 기법으로 완벽히 통제 및 연동합니다.
 * 1. 단어장 탭: 내 단어장 폴더 목록, 실시간 검색, 단어장 추가/편집 기법 연계
 * 2. 학습 탭: SharedPreferences 연동 실시간 최근 학습 진척도 원형 프로그레스 표시 및 이어학습 기능
 * 3. 추가/수정 탭: PDF 연동 AI 영단어 자동 추출 및 Gemini를 활용한 뜻 자동 완성, 단어 행 동적 입출력, 저장 탑재
 */
public class MainActivity extends AppCompatActivity implements VocabularyAdapter.OnVocabularyClickListener {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_PDF_PICK = 422;

    private AppDatabase db;
    private VocabularyDao vocabularyDao;
    private WordDao wordDao;
    private ExecutorService dbExecutor;
    private GeminiHelper geminiHelper;
    private PdfWordExtractor pdfWordExtractor;

    // Bottom Navigation
    private BottomNavigationView bottomNavigation;

    // Tab 1 UI
    private View tabContainerVocab;
    private RecyclerView rvVocabularies;
    private VocabularyAdapter adapter;
    private List<VocabularyEntity> currentVocabularyList = new ArrayList<>();
    private View layoutEmptyState;
    private EditText etSearch;

    // Tab 2 UI (학습 레포트)
    private View tabContainerLearn;
    private View cardLearnEmpty;
    private View layoutLearnActive;
    private TextView tvLearnVocabName;
    private ProgressBar pbLearnCircle;
    private TextView tvLearnPercent;
    private TextView tvLearnProgressRatio;
    private View btnResumeStudy;
    private int currentLastStudiedVocabId = -1;

    // Tab 3 UI (단어 입력 / 수정)
    private View tabContainerAdd;
    private TextView tvAddPageTitle;
    private TextInputEditText etVocabName;
    private TextInputEditText etVocabDesc;
    private LinearLayout containerWordRows;
    private MaterialButton btnAiComplete;
    private MaterialButton btnPdfExtract;
    private boolean isTabNavigationConfirmed = false;
    private AlertDialog simpleMessageDialog;
    private int editingVocabularyId = 0; // 0이면 신규 추가, >0이면 특정 단어장 편집 수정

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 자원 할당
        db = AppDatabase.getInstance(this);
        vocabularyDao = db.vocabularyDao();
        wordDao = db.wordDao();
        dbExecutor = Executors.newSingleThreadExecutor();
        geminiHelper = new GeminiHelper();
        pdfWordExtractor = new PdfWordExtractor(this, geminiHelper);

        // 메인 멀티 탭 구조 그릇 연결
        tabContainerVocab = findViewById(R.id.tab_container_vocab);
        tabContainerLearn = findViewById(R.id.tab_container_learn);
        tabContainerAdd = findViewById(R.id.tab_container_add);

        // --- TAB 1 (단어장 목록) 초기화 ---
        rvVocabularies = findViewById(R.id.rv_vocabularies);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        etSearch = findViewById(R.id.et_search);

        rvVocabularies.setLayoutManager(new LinearLayoutManager(this));
        adapter = new VocabularyAdapter(this);
        rvVocabularies.setAdapter(adapter);

        // 실시간 이름 검색 연동
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchVocabularyFolders(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // --- TAB 2 (학습 레포트) 초기화 ---
        cardLearnEmpty = findViewById(R.id.card_learn_empty);
        layoutLearnActive = findViewById(R.id.layout_learn_active);
        tvLearnVocabName = findViewById(R.id.tv_learn_vocab_name);
        pbLearnCircle = findViewById(R.id.pb_learn_circle);
        tvLearnPercent = findViewById(R.id.tv_learn_percent);
        tvLearnProgressRatio = findViewById(R.id.tv_learn_progress_ratio);
        btnResumeStudy = findViewById(R.id.btn_resume_study);

        // 이어 학습 클릭 핸들러
        btnResumeStudy.setOnClickListener(v -> {
            if (currentLastStudiedVocabId != -1) {
                Intent intent = new Intent(this, VocabularyDetailActivity.class);
                intent.putExtra("VOCAB_ID", currentLastStudiedVocabId);
                intent.putExtra("VOCAB_NAME", tvLearnVocabName.getText().toString());
                startActivity(intent);
            }
        });

        // --- TAB 3 (단어장 추가 및 편집) 초기화 ---
        tvAddPageTitle = findViewById(R.id.tv_add_page_title);
        etVocabName = findViewById(R.id.et_vocab_name);
        etVocabDesc = findViewById(R.id.et_vocab_desc);
        containerWordRows = findViewById(R.id.container_word_rows);

        // 단어 추가 행 생성 리스너
        findViewById(R.id.btn_add_row).setOnClickListener(v -> addNewWordRow("", ""));

        // 단어장 저장 버튼 연결
        findViewById(R.id.btn_save_vocabulary).setOnClickListener(v -> saveVocabularyData());

        // AI 스마트 채우기 뜻 매핑 버튼 연결
        btnAiComplete = findViewById(R.id.btn_ai_complete);
        btnAiComplete.setOnClickListener(v -> executeAiSmartFill());

        // PDF 영단어 추출 및 업로드 연동 클릭
        btnPdfExtract = findViewById(R.id.btn_pdf_extract);
        btnPdfExtract.setOnClickListener(v -> triggerPdfSelection());

        findViewById(R.id.btn_add_help).setOnClickListener(v -> showHelpDialog());

        // 첫 기동 샘플 단어 점검 및 주입
        checkAndPrepopulateSampleData();

        // 하단바 클릭 탭 분기 핸들링
        bottomNavigation = findViewById(R.id.bottom_navigation);
        bottomNavigation.setOnItemSelectedListener(item -> {
            return navigateToTabAfterConfirm(item.getItemId());
        });

        // 기본 활성 탭
        bottomNavigation.setSelectedItemId(R.id.nav_vocab);
    }

    private void showHelpDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_help, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        MaterialButton confirmButton = dialogView.findViewById(R.id.btn_help_confirm);
        confirmButton.setOnClickListener(v -> dialog.dismiss());

        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
        }
    }

    private void showAiErrorDialog(String title, String message) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_ai_error, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        TextView titleView = dialogView.findViewById(R.id.tv_ai_error_title);
        TextView messageView = dialogView.findViewById(R.id.tv_ai_error_message);
        MaterialButton confirmButton = dialogView.findViewById(R.id.btn_ai_error_confirm);

        titleView.setText(title);
        messageView.setText(message);
        confirmButton.setOnClickListener(v -> dialog.dismiss());

        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
        }
    }

    private void showShortMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showSimpleMessageDialog(String title, String message) {
        if (simpleMessageDialog != null && simpleMessageDialog.isShowing()) {
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_simple_message, null);
        simpleMessageDialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        TextView titleView = dialogView.findViewById(R.id.tv_simple_message_title);
        TextView messageView = dialogView.findViewById(R.id.tv_simple_message_body);
        MaterialButton confirmButton = dialogView.findViewById(R.id.btn_simple_message_confirm);

        titleView.setText(title);
        messageView.setText(message);
        confirmButton.setOnClickListener(v -> simpleMessageDialog.dismiss());

        simpleMessageDialog.setOnDismissListener(dialog -> simpleMessageDialog = null);
        simpleMessageDialog.setCancelable(false);
        simpleMessageDialog.setCanceledOnTouchOutside(false);
        simpleMessageDialog.show();

        Window window = simpleMessageDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
        }
    }
    private String getUserFriendlyAiErrorTitle(String errorMessage) {
        String normalized = normalizeAiErrorMessage(errorMessage);
        if (isQuotaExceededError(normalized)) {
            return "AI 요청 한도를 초과했어요";
        }
        if (isServerUnavailableError(normalized)) {
            return "AI 서버가 혼잡해요";
        }
        if (isTimeoutError(normalized)) {
            return "요청 시간이 초과됐어요";
        }
        if (isNetworkError(normalized)) {
            return "네트워크 연결을 확인해 주세요";
        }
        if (isJsonParseError(normalized)) {
            return "AI 응답을 처리하지 못했어요";
        }
        return "AI 요청에 실패했어요";
    }

    private String getUserFriendlyAiErrorMessage(String errorMessage) {
        String normalized = normalizeAiErrorMessage(errorMessage);
        if (isQuotaExceededError(normalized)) {
            return "오늘 사용할 수 있는 AI 요청량을 모두 사용했어요. 잠시 후 다시 시도하거나 내일 다시 이용해 주세요.";
        }
        if (isServerUnavailableError(normalized)) {
            return "현재 AI 서버 응답이 원활하지 않아요. 잠시 후 다시 시도해 주세요.";
        }
        if (isTimeoutError(normalized)) {
            return "AI 응답을 기다리는 시간이 길어졌어요. 네트워크 상태를 확인한 뒤 다시 시도해 주세요.";
        }
        if (isNetworkError(normalized)) {
            return "인터넷 연결이 불안정하거나 서버에 연결할 수 없어요. 연결 상태를 확인한 뒤 다시 시도해 주세요.";
        }
        if (isJsonParseError(normalized)) {
            return "AI가 예상과 다른 형식으로 응답했어요. 다시 시도해 주세요.";
        }
        return "일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요.";
    }

    private String normalizeAiErrorMessage(String errorMessage) {
        return errorMessage == null ? "" : errorMessage.toLowerCase();
    }

    private boolean isQuotaExceededError(String errorMessage) {
        return errorMessage.contains("429")
                || errorMessage.contains("resource_exhausted")
                || errorMessage.contains("quota");
    }

    private boolean isServerUnavailableError(String errorMessage) {
        return errorMessage.contains("503")
                || errorMessage.contains("unavailable")
                || errorMessage.contains("server busy")
                || errorMessage.contains("서버")
                || errorMessage.contains("혼잡");
    }

    private boolean isTimeoutError(String errorMessage) {
        return errorMessage.contains("timeout")
                || errorMessage.contains("timed out")
                || errorMessage.contains("시간이 초과")
                || errorMessage.contains("시간 초과");
    }

    private boolean isNetworkError(String errorMessage) {
        return errorMessage.contains("network")
                || errorMessage.contains("failed to connect")
                || errorMessage.contains("unable to resolve host")
                || errorMessage.contains("connection")
                || errorMessage.contains("socket")
                || errorMessage.contains("인터넷")
                || errorMessage.contains("네트워크");
    }

    private boolean isJsonParseError(String errorMessage) {
        return errorMessage.contains("json")
                || errorMessage.contains("parse")
                || errorMessage.contains("parsing")
                || errorMessage.contains("분석에 실패")
                || errorMessage.contains("형식")
                || errorMessage.contains("응답 구조");
    }

    private void setAiLoadingState(boolean isLoading) {
        if (btnAiComplete != null) {
            btnAiComplete.setEnabled(!isLoading);
            btnAiComplete.setAlpha(isLoading ? 0.55f : 1f);
        }
        if (btnPdfExtract != null) {
            btnPdfExtract.setEnabled(!isLoading);
            btnPdfExtract.setAlpha(isLoading ? 0.55f : 1f);
        }
    }

    private boolean navigateToTabAfterConfirm(int targetTabId) {
        int currentTabId = bottomNavigation.getSelectedItemId();
        if (isTabNavigationConfirmed) {
            switchTab(targetTabId);
            return true;
        }

        if (currentTabId == R.id.nav_add
                && targetTabId != R.id.nav_add
                && hasUnsavedAddEditChanges()) {
            showLeaveAddEditConfirmDialog(() -> {
                exitEditMode();
                clearAddEditForm();
                isTabNavigationConfirmed = true;
                bottomNavigation.setSelectedItemId(targetTabId);
                isTabNavigationConfirmed = false;
            });
            return false;
        }

        switchTab(targetTabId);
        return true;
    }

    private boolean hasUnsavedVocabularyInput() {
        if (!isEditTextEmpty(etVocabName) || !isEditTextEmpty(etVocabDesc)) {
            return true;
        }

        for (int i = 0; i < containerWordRows.getChildCount(); i++) {
            View row = containerWordRows.getChildAt(i);
            EditText etWord = row.findViewById(R.id.et_row_word);
            EditText etMeaning = row.findViewById(R.id.et_row_meaning);
            if (!isEditTextEmpty(etWord) || !isEditTextEmpty(etMeaning)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasUnsavedEditInput() {
        return editingVocabularyId > 0;
    }

    private boolean hasUnsavedAddEditChanges() {
        return hasUnsavedEditInput() || hasUnsavedVocabularyInput();
    }

    private void showLeaveAddEditConfirmDialog(Runnable onConfirm) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_unsaved_changes, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        TextView titleView = dialogView.findViewById(R.id.tv_unsaved_changes_title);
        TextView messageView = dialogView.findViewById(R.id.tv_unsaved_changes_message);
        MaterialButton cancelButton = dialogView.findViewById(R.id.btn_unsaved_changes_cancel);
        MaterialButton confirmButton = dialogView.findViewById(R.id.btn_unsaved_changes_confirm);

        if (hasUnsavedEditInput()) {
            titleView.setText("편집을 종료할까요?");
            messageView.setText("저장하지 않고 이동하면 수정 중인 내용이 사라집니다. 이동할까요?");
        } else {
            titleView.setText("입력 내용이 사라질 수 있어요");
            messageView.setText("저장하지 않고 이동하면 현재 입력한 단어장 내용이 사라집니다. 이동할까요?");
        }

        cancelButton.setOnClickListener(v -> dialog.dismiss());
        confirmButton.setOnClickListener(v -> {
            dialog.dismiss();
            if (onConfirm != null) {
                onConfirm.run();
            }
        });

        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
        }
    }

    private void clearAddEditForm() {
        tvAddPageTitle.setText("새 단어장 추가");
        etVocabName.setText("");
        etVocabDesc.setText("");
        containerWordRows.removeAllViews();
        addNewWordRow("", "");
        addNewWordRow("", "");
        addNewWordRow("", "");
        resetAddEditScrollPosition();
    }

    private void exitEditMode() {
        editingVocabularyId = 0;
    }

    private void ensureAddEditFormReady() {
        if (containerWordRows.getChildCount() == 0) {
            clearAddEditForm();
        }
    }

    /**
     * 지정된 탭 아이템 ID에 맞추어 화면 레이아웃들의 시각적 가시성 전환
     */
    private void switchTab(int itemId) {
        if (itemId == R.id.nav_vocab) {
            tabContainerVocab.setVisibility(View.VISIBLE);
            tabContainerLearn.setVisibility(View.GONE);
            tabContainerAdd.setVisibility(View.GONE);
            // 메인 툴바 텍스트 복구
            TextView tvTitle = findViewById(R.id.tv_main_title);
            tvTitle.setText("AI 스마트 단어장");
            loadVocabularyFolders();
        } else if (itemId == R.id.nav_learn) {
            tabContainerVocab.setVisibility(View.GONE);
            tabContainerLearn.setVisibility(View.VISIBLE);
            tabContainerAdd.setVisibility(View.GONE);
            // 메인 툴바 타이틀
            TextView tvTitle = findViewById(R.id.tv_main_title);
            tvTitle.setText("나의 실시간 학습");
            loadLearningReport();
        } else if (itemId == R.id.nav_add) {
            tabContainerVocab.setVisibility(View.GONE);
            tabContainerLearn.setVisibility(View.GONE);
            tabContainerAdd.setVisibility(View.VISIBLE);
            // 메인 툴바 타이틀
            TextView tvTitle = findViewById(R.id.tv_main_title);
            tvTitle.setText("단어장 편집실");

            ensureAddEditFormReady();
            resetAddEditScrollPosition();
        } else if (itemId == R.id.nav_settings) {
            Toast.makeText(this, "설정 창은 다음 추가 편의 기능 업데이트 예정입니다.", Toast.LENGTH_SHORT).show();
            // 탭 변경 없이 이전 탭 유지되도록 재설정 방지
        }
    }

    private void resetAddEditScrollPosition() {
        tabContainerAdd.post(() -> tabContainerAdd.scrollTo(0, 0));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 상세 학습하고 돌아왔을 때, 혹시 기록이 있는지 파악하여 학습 리포트 최신화 대비
        if (bottomNavigation.getSelectedItemId() == R.id.nav_learn) {
            loadLearningReport();
        } else if (bottomNavigation.getSelectedItemId() == R.id.nav_vocab) {
            loadVocabularyFolders();
        }
    }

    // --- TAB 1: 내 단어장 데이터 로딩 및 검색 실무 ---

    private void loadVocabularyFolders() {
        dbExecutor.execute(() -> {
            final List<VocabularyEntity> list = vocabularyDao.getAllVocabularies();
            runOnUiThread(() -> updateUI(list));
        });
    }

    private void searchVocabularyFolders(String query) {
        if (query == null || query.trim().isEmpty()) {
            loadVocabularyFolders();
            return;
        }
        dbExecutor.execute(() -> {
            final List<VocabularyEntity> list = vocabularyDao.searchVocabularies("%" + query + "%");
            runOnUiThread(() -> updateUI(list));
        });
    }

    private void updateUI(List<VocabularyEntity> list) {
        if (list == null || list.isEmpty()) {
            currentVocabularyList = new ArrayList<>();
            layoutEmptyState.setVisibility(View.VISIBLE);
            rvVocabularies.setVisibility(View.GONE);
        } else {
            currentVocabularyList = sortVocabularyList(list);
            layoutEmptyState.setVisibility(View.GONE);
            rvVocabularies.setVisibility(View.VISIBLE);
            adapter.setVocabularyList(currentVocabularyList);
        }
    }

    private List<VocabularyEntity> sortVocabularyList(List<VocabularyEntity> source) {
        List<VocabularyEntity> sortedList = new ArrayList<>(source);
        sortedList.sort(
                Comparator.comparing(VocabularyEntity::isFavorite)
                        .reversed()
                        .thenComparing(
                                VocabularyEntity::getId,
                                Comparator.reverseOrder()
                        )
        );
        return sortedList;
    }

    private void toggleFavorite(VocabularyEntity vocabulary) {
        boolean newFavoriteState = !vocabulary.isFavorite();
        vocabulary.setFavorite(newFavoriteState);

        currentVocabularyList = sortVocabularyList(currentVocabularyList);
        adapter.setVocabularyList(currentVocabularyList);

        dbExecutor.execute(() ->
                vocabularyDao.updateFavorite(vocabulary.getId(), newFavoriteState)
        );
    }

    // --- TAB 2: 학습 진행 통계 리포트 로드 실무 (사용자 구체적 편의 개선) ---

    private void loadLearningReport() {
        SharedPreferences prefs = getSharedPreferences("StudyPrefs", MODE_PRIVATE);
        final int lastStudiedId = prefs.getInt("last_studied_vocab_id", -1);

        if (lastStudiedId == -1) {
            cardLearnEmpty.setVisibility(View.VISIBLE);
            layoutLearnActive.setVisibility(View.GONE);
        } else {
            currentLastStudiedVocabId = lastStudiedId;
            final String savedName = prefs.getString("vocab_name_" + lastStudiedId, "학습 단어장");
            final int progress = prefs.getInt("vocab_progress_" + lastStudiedId, 0);
            final int total = prefs.getInt("vocab_total_" + lastStudiedId, 0);

            // UI 매핑
            tvLearnVocabName.setText(savedName);
            tvLearnProgressRatio.setText("학습 마일스톤: " + progress + " / " + total + " 단어 완료");

            if (total > 0) {
                int percentage = (int) (((float) progress / total) * 100);
                pbLearnCircle.setProgress(percentage);
                tvLearnPercent.setText(percentage + "%");
            } else {
                pbLearnCircle.setProgress(0);
                tvLearnPercent.setText("0%");
            }

            cardLearnEmpty.setVisibility(View.GONE);
            layoutLearnActive.setVisibility(View.VISIBLE);
        }
    }

    // --- TAB 3: 단어 입력 및 AI 스마트 툴 구현 실무 ---

    /**
     * 단어 혹은 뜻 입력을 위한 가로 텍스트행 추가 생성 메커니즘
     */
    private void addNewWordRow(String wordText, String meaningText) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_word_input_row, containerWordRows, false);
        final EditText etWord = row.findViewById(R.id.et_row_word);
        final EditText etMeaning = row.findViewById(R.id.et_row_meaning);
        final View btnDelete = row.findViewById(R.id.btn_row_delete);

        etWord.setText(wordText);
        etMeaning.setText(meaningText);

        btnDelete.setOnClickListener(v -> containerWordRows.removeView(row));

        containerWordRows.addView(row);
    }

    /**
     * 단어 입력 정보를 바탕으로 뜻을 AI(Gemini 3.5 Flash)를 통해 자동 제안 한화면에 세팅
     */
    private void executeAiSmartFill() {
        if (!hasEmptyMeanings()) {
            Toast.makeText(this, "비어 있는 뜻이 없어요.", Toast.LENGTH_SHORT).show();
            return;
        }

        final List<String> wordsToFill = collectWordsWithEmptyMeaning();

        if (wordsToFill.isEmpty()) {
            Toast.makeText(this, "뜻을 매칭하고 완성할 영단어를 최소 1개 이상 왼쪽 폼에 입력해 주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage("스마트 AI가 입력한 핵심 영단어 뜻풀이와 파생 의미를 분석하여 추천 기재 중입니다...");
        dialog.setCancelable(false);
        setAiLoadingState(true);
        dialog.show();

        geminiHelper.fillMeanings(wordsToFill, new GeminiHelper.GeminiCallback() {
            @Override
            public void onSuccess(List<GeminiHelper.WordResult> results) {
                dialog.dismiss();
                setAiLoadingState(false);
                if (results == null || results.isEmpty()) {
                    Toast.makeText(MainActivity.this, "AI 분석 뜻을 생성하지 못했습니다.", Toast.LENGTH_SHORT).show();
                    return;
                }

                int matchCount = applyMeaningsOnlyToEmptyFields(results);
                Toast.makeText(MainActivity.this, "총 " + matchCount + "개의 영어 어휘 뜻풀이가 AI로 자동 삽입 보완 완료되었습니다!", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String errorMessage) {
                dialog.dismiss();
                setAiLoadingState(false);
                Log.e(TAG, "Gemini fill meanings failed: " + errorMessage);
                showAiErrorDialog(
                        getUserFriendlyAiErrorTitle(errorMessage),
                        getUserFriendlyAiErrorMessage(errorMessage)
                );
            }
        });
    }

    private boolean hasEmptyMeanings() {
        for (int i = 0; i < containerWordRows.getChildCount(); i++) {
            View row = containerWordRows.getChildAt(i);
            EditText etMeaning = row.findViewById(R.id.et_row_meaning);
            if (isEditTextEmpty(etMeaning)) {
                return true;
            }
        }
        return false;
    }

    private List<String> collectWordsWithEmptyMeaning() {
        List<String> wordsToFill = new ArrayList<>();
        for (int i = 0; i < containerWordRows.getChildCount(); i++) {
            View row = containerWordRows.getChildAt(i);
            EditText etWord = row.findViewById(R.id.et_row_word);
            EditText etMeaning = row.findViewById(R.id.et_row_meaning);

            String word = etWord.getText().toString().trim();
            if (!word.isEmpty() && isEditTextEmpty(etMeaning)) {
                wordsToFill.add(word);
            }
        }
        return wordsToFill;
    }

    private int applyMeaningsOnlyToEmptyFields(List<GeminiHelper.WordResult> results) {
        int matchCount = 0;
        for (int i = 0; i < containerWordRows.getChildCount(); i++) {
            View row = containerWordRows.getChildAt(i);
            EditText etWord = row.findViewById(R.id.et_row_word);
            EditText etMeaning = row.findViewById(R.id.et_row_meaning);

            String currentWord = etWord.getText().toString().trim();
            if (currentWord.isEmpty() || !isEditTextEmpty(etMeaning)) {
                continue;
            }

            for (GeminiHelper.WordResult result : results) {
                if (result.word == null || result.meaning == null || result.meaning.trim().isEmpty()) {
                    continue;
                }
                if (currentWord.equalsIgnoreCase(result.word.trim())) {
                    etMeaning.setText(result.meaning.trim());
                    matchCount++;
                    break;
                }
            }
        }
        return matchCount;
    }

    private boolean isEditTextEmpty(EditText editText) {
        return editText == null || editText.getText().toString().trim().isEmpty();
    }

    /**
     * PDF 파선택을 유도하는 인텐트 트리거
     */
    private void triggerPdfSelection() {
        setAiLoadingState(true);
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        startActivityForResult(intent, REQUEST_CODE_PDF_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PDF_PICK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            processPdfFile(data.getData());
        } else if (requestCode == REQUEST_CODE_PDF_PICK) {
            setAiLoadingState(false);
        }
    }

    /**
     * 선택한 PDF의 페이지를 순서대로 분석하여 추출된 단어를 입력 행에 추가합니다.
     */
    private void processPdfFile(Uri uri) {
        if (!GeminiHelper.isApiConfigured()) {
            setAiLoadingState(false);
            Log.e(TAG, "Gemini API key is not configured.");
            showAiErrorDialog(
                    getUserFriendlyAiErrorTitle("Gemini API Key가 설정되지 않았습니다."),
                    getUserFriendlyAiErrorMessage("Gemini API Key가 설정되지 않았습니다.")
            );
            return;
        }

        final ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage("가져온 PDF 학습 문서를 분석하기 위해 준비 중입니다...");
        progress.setCancelable(false);
        setAiLoadingState(true);
        progress.show();

        pdfWordExtractor.extract(uri, new PdfWordExtractor.Callback() {
            @Override
            public void onProgress(int currentPage, int totalPages) {
                progress.setMessage(
                        "PDF " + currentPage + " / " + totalPages + " 페이지에서 단어를 추출하는 중입니다..."
                );
            }

            @Override
            public void onSuccess(
                    List<GeminiHelper.WordResult> results,
                    int processedPages,
                    boolean pageLimitApplied
            ) {
                progress.dismiss();
                setAiLoadingState(false);
                if (results == null || results.isEmpty()) {
                    Toast.makeText(
                            MainActivity.this,
                            "PDF에서 중요 단어를 추출하지 못했습니다.",
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                boolean hasContent = false;
                for (int i = 0; i < containerWordRows.getChildCount(); i++) {
                    View row = containerWordRows.getChildAt(i);
                    EditText etWord = row.findViewById(R.id.et_row_word);
                    if (!etWord.getText().toString().trim().isEmpty()) {
                        hasContent = true;
                        break;
                    }
                }
                if (!hasContent) {
                    containerWordRows.removeAllViews();
                }

                for (GeminiHelper.WordResult result : results) {
                    addNewWordRow(result.word, result.meaning);
                }

                String limitMessage = pageLimitApplied
                        ? " 최대 " + PdfWordExtractor.MAX_PAGES + "페이지만 처리했습니다."
                        : "";
                Toast.makeText(
                        MainActivity.this,
                        processedPages + "페이지 분석 완료, 중복 제외 " + results.size()
                                + "개 단어를 추가했습니다." + limitMessage,
                        Toast.LENGTH_LONG
                ).show();
            }

            @Override
            public void onError(String errorMessage) {
                progress.dismiss();
                setAiLoadingState(false);
                Log.e(TAG, "Gemini PDF extraction failed: " + errorMessage);
                showAiErrorDialog(
                        getUserFriendlyAiErrorTitle(errorMessage),
                        getUserFriendlyAiErrorMessage(errorMessage)
                );
            }
        });
    }

    /**
     * 입력실에 구성 완료한 데이터를 최종 DB에 갱신/추가 세진처리
     */
    private boolean validateVocabularyTitle() {
        String title = etVocabName.getText().toString().trim();
        if (title.isEmpty()) {
            etVocabName.setError("단어장 제목을 입력해 주세요.");
            etVocabName.requestFocus();
            return false;
        }
        etVocabName.setError(null);
        return true;
    }

    private boolean hasAtLeastOneWord() {
        for (int i = 0; i < containerWordRows.getChildCount(); i++) {
            View row = containerWordRows.getChildAt(i);
            EditText etWord = row.findViewById(R.id.et_row_word);
            if (!isEditTextEmpty(etWord)) {
                return true;
            }
        }
        return false;
    }
    private void saveVocabularyData() {
        if (!validateVocabularyTitle()) {
            return;
        }

        if (!hasAtLeastOneWord()) {
            showSimpleMessageDialog("단어를 추가해 주세요", "저장할 단어가 하나 이상 필요해요.");
            return;
        }

        final String name = etVocabName.getText().toString().trim();
        final String desc = etVocabDesc.getText().toString().trim();
        final boolean isEditMode = editingVocabularyId > 0;
        final int targetVocabularyId = editingVocabularyId;

        final List<WordEntity> targetWords = new ArrayList<>();
        for (int i = 0; i < containerWordRows.getChildCount(); i++) {
            View row = containerWordRows.getChildAt(i);
            EditText etW = row.findViewById(R.id.et_row_word);
            EditText etM = row.findViewById(R.id.et_row_meaning);

            String w = etW.getText().toString().trim();
            String m = etM.getText().toString().trim();

            if (!w.isEmpty()) {
                targetWords.add(new WordEntity(0, w, m));
            }
        }

        if (targetWords.isEmpty()) {
            showSimpleMessageDialog("단어를 추가해 주세요", "저장할 단어가 하나 이상 필요해요.");
            return;
        }

        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage("단어장 세부 정보를 저장하고 디비에 반영 중입니다...");
        dialog.setCancelable(false);
        dialog.show();

        dbExecutor.execute(() -> {
            try {
                if (isEditMode) {
                    VocabularyEntity folder = vocabularyDao.getVocabularyById(targetVocabularyId);
                    if (folder == null) {
                        throw new IllegalStateException("Vocabulary not found: " + targetVocabularyId);
                    }

                    folder.setName(name);
                    folder.setDescription(desc);
                    vocabularyDao.update(folder);
                    wordDao.deleteWordsByVocabularyId(targetVocabularyId);

                    for (WordEntity wrd : targetWords) {
                        wrd.setVocabularyId(targetVocabularyId);
                        wordDao.insert(wrd);
                    }
                } else {
                    VocabularyEntity folder = new VocabularyEntity(name, desc);
                    long newId = vocabularyDao.insert(folder);

                    for (WordEntity wrd : targetWords) {
                        wrd.setVocabularyId((int) newId);
                        wordDao.insert(wrd);
                    }
                }

                runOnUiThread(() -> {
                    dialog.dismiss();
                    showShortMessage(isEditMode ? "단어장이 수정되었어요." : "단어장이 저장되었어요.");
                    exitEditMode();
                    clearAddEditForm();
                    bottomNavigation.setSelectedItemId(R.id.nav_vocab);
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to save vocabulary. isEditMode=" + isEditMode, e);
                runOnUiThread(() -> {
                    dialog.dismiss();
                    showSimpleMessageDialog(
                            isEditMode ? "수정하지 못했어요" : "저장하지 못했어요",
                            "일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요."
                    );
                });
            }
        });
    }

    private void showDeleteConfirmDialog(final VocabularyEntity vocabulary) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("단어장 영구 삭제")
                .setMessage("정말 [" + vocabulary.getName() + "] 단어장을 영구적으로 삭제하시겠습니까?\n내부에 소속된 전 수록단어 및 학습 기록도 함께 삭제처리됩니다.")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("삭제", (dialog, which) -> {
                    dbExecutor.execute(() -> {
                        vocabularyDao.delete(vocabulary);
                        
                        // 학습 통 기록에 해당 단어장이 지워졌다면 SharedPreferences 클리어 대비
                        SharedPreferences prefs = getSharedPreferences("StudyPrefs", MODE_PRIVATE);
                        if (prefs.getInt("last_studied_vocab_id", -1) == vocabulary.getId()) {
                            prefs.edit().remove("last_studied_vocab_id").apply();
                        }
                        
                        loadVocabularyFolders();
                    });
                    Toast.makeText(MainActivity.this, "단어장이 완전 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("취소", (dialog, which) -> dialog.dismiss())
                .show();
    }

    // --- Vocabulary Adapter 클릭 수신 위임 구조 구현 ---

    @Override
    public void onItemClick(VocabularyEntity vocabulary) {
        // 단어장 카드 클릭 시: 상세 플래시카드 학습 페이지로 가동 출발!
        Intent intent = new Intent(this, VocabularyDetailActivity.class);
        intent.putExtra("VOCAB_ID", vocabulary.getId());
        intent.putExtra("VOCAB_NAME", vocabulary.getName());
        startActivity(intent);
    }

    @Override
    public void onFavoriteClick(VocabularyEntity vocabulary) {
        toggleFavorite(vocabulary);
    }

    @Override
    public void onEditClick(VocabularyEntity vocabulary) {
        // 수정 버튼을 눌렀을 경우: 팝업창이 뜨는 것이 전혀 아니며, 단어 입력(Tab 3) 페이지로 이동!
        editingVocabularyId = vocabulary.getId();
        tvAddPageTitle.setText("단어장 편집 및 수정");
        etVocabName.setText(vocabulary.getName());
        etVocabDesc.setText(vocabulary.getDescription());
        containerWordRows.removeAllViews();

        final ProgressDialog waiting = new ProgressDialog(this);
        waiting.setMessage("저장되어 있는 어휘 목록을 불러오는 중입니다...");
        waiting.show();

        dbExecutor.execute(() -> {
            final List<WordEntity> words = wordDao.getWordsByVocabularyId(vocabulary.getId());
            runOnUiThread(() -> {
                waiting.dismiss();
                if (words != null && !words.isEmpty()) {
                    for (WordEntity w : words) {
                        addNewWordRow(w.getWord(), w.getMeaning());
                    }
                } else {
                    addNewWordRow("", "");
                    addNewWordRow("", "");
                    addNewWordRow("", "");
                }
                
                // 단어 입력 페이지에 해당하는 nav_add 탭 선택 전환
                bottomNavigation.setSelectedItemId(R.id.nav_add);
            });
        });
    }

    @Override
    public void onDeleteClick(VocabularyEntity vocabulary) {
        showDeleteConfirmDialog(vocabulary);
    }

    private void checkAndPrepopulateSampleData() {
        dbExecutor.execute(() -> {
            List<VocabularyEntity> currentList = vocabularyDao.getAllVocabularies();
            if (currentList == null || currentList.isEmpty()) {
                // 1. 비즈니스 단어셋
                VocabularyEntity v1 = new VocabularyEntity("비즈니스 영어 실전", "실제 미팅과 이메일 작성 시 가장 빈번히 사용되는 실무 영어");
                long id1 = vocabularyDao.insert(v1);
                wordDao.insert(new WordEntity((int)id1, "Collaborate", "협력하다 (동사)"));
                wordDao.insert(new WordEntity((int)id1, "Agenda", "회의 의제, 안건 (명사)"));
                wordDao.insert(new WordEntity((int)id1, "Deliverable", "최종 성과물 (명사)"));
                wordDao.insert(new WordEntity((int)id1, "Outcome", "결과, 성과 (명사)"));

                // 2. 여행 기초 회화
                VocabularyEntity v2 = new VocabularyEntity("여행 기초 회화 단어집", "여행 상황 속에서 살아남기 위한 필수 어휘 덤프");
                long id2 = vocabularyDao.insert(v2);
                wordDao.insert(new WordEntity((int)id2, "Destination", "목적지 (명사)"));
                wordDao.insert(new WordEntity((int)id2, "Boarding", "탑승 (명사)"));
                wordDao.insert(new WordEntity((int)id2, "Reservation", "예약 (명사)"));
                wordDao.insert(new WordEntity((int)id2, "Departure", "출발 (명사)"));

                loadVocabularyFolders();
            } else {
                loadVocabularyFolders();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbExecutor != null && !dbExecutor.isShutdown()) {
            dbExecutor.shutdown();
        }
        if (pdfWordExtractor != null) {
            pdfWordExtractor.shutdown();
        }
        if (geminiHelper != null) {
            geminiHelper.shutdown();
        }
    }
}
