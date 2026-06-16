package com.example;

import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.database.AppDatabase;
import com.example.database.VocabularyDao;
import com.example.database.WordDao;
import com.example.model.VocabularyEntity;
import com.example.model.WordEntity;
import com.example.util.AppToast;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 단어장 상세 화면 액티비티입니다.
 * 이제 이 화면은 사용자의 니즈를 근거로, 단어 목록이나 복잡한 탭이 전혀 없이
 * 바로 깔끔하고 직관적인 '카드 학습 모드'만을 독점적으로 표시합니다.
 * 실시간 학습 기록을 전격 기록하여 메인 학습 리포트 탭에 반영합니다.
 */
public class VocabularyDetailActivity extends AppCompatActivity {

    private int vocabularyId;
    private String vocabularyName;

    private AppDatabase db;
    private VocabularyDao vocabularyDao;
    private WordDao wordDao;
    private ExecutorService dbExecutor;
    private VocabularyEntity currentVocabulary;

    private List<WordEntity> activeWordList = new ArrayList<>();

    // 플래시 카드 학습 상태 및 UI 요소들
    private int currentStudyIndex = 0;
    private boolean isWordVisible = true;
    private boolean isMeaningVisible = false;

    private TextView tvStudyProgress;
    private ProgressBar pbStudy;
    private TextView tvStudyWord;
    private ImageView ivWordRevealIcon;
    private TextView tvWordRevealHint;
    private TextView tvStudyMeaning;
    private MaterialCardView cardWordDisplay;
    private MaterialCardView cardMeaningDisplay;
    private ImageView ivRevealIcon;
    private TextView tvRevealHint;

    private MaterialButton btnStudyPrev;
    private MaterialButton btnStudyNext;
    private ImageView btnBookmark;
    private AlertDialog studyCompleteDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vocabulary_detail);

        // 값 수집
        vocabularyId = getIntent().getIntExtra("VOCAB_ID", -1);
        vocabularyName = getIntent().getStringExtra("VOCAB_NAME");
        if (vocabularyName == null) {
            vocabularyName = "단어장";
        }

        db = AppDatabase.getInstance(this);
        vocabularyDao = db.vocabularyDao();
        wordDao = db.wordDao();
        dbExecutor = Executors.newSingleThreadExecutor();

        // 툴바 연결
        TextView tvTitle = findViewById(R.id.tv_detail_title);
        tvTitle.setText(vocabularyName);

        findViewById(R.id.btn_detail_back).setOnClickListener(v -> finish());

        btnBookmark = findViewById(R.id.btn_detail_bookmark);
        btnBookmark.setEnabled(false);
        btnBookmark.setOnClickListener(v -> toggleFavorite());

        // 카드 컴포넌트들 맵핑
        tvStudyProgress = findViewById(R.id.tv_study_progress);
        pbStudy = findViewById(R.id.pb_study);
        cardWordDisplay = findViewById(R.id.card_word_display);
        tvStudyWord = findViewById(R.id.tv_study_word);
        ivWordRevealIcon = findViewById(R.id.iv_word_reveal_icon);
        tvWordRevealHint = findViewById(R.id.tv_word_reveal_hint);
        tvStudyMeaning = findViewById(R.id.tv_study_meaning);
        cardMeaningDisplay = findViewById(R.id.card_meaning_display);
        ivRevealIcon = findViewById(R.id.iv_reveal_icon);
        tvRevealHint = findViewById(R.id.tv_reveal_hint);

        btnStudyPrev = findViewById(R.id.btn_study_prev);
        btnStudyNext = findViewById(R.id.btn_study_next);

        cardWordDisplay.setOnClickListener(v -> toggleWordVisibility());
        cardMeaningDisplay.setOnClickListener(v -> toggleMeaningVisibility());

        // 이전/다음
        btnStudyPrev.setOnClickListener(v -> navigateStudyCard(-1));
        btnStudyNext.setOnClickListener(v -> navigateStudyCard(1));

        // 단어장 상태와 단어 목록 로드
        loadFavoriteState();
        loadVocabularyWords();
    }

    private void loadFavoriteState() {
        if (vocabularyId == -1) return;

        dbExecutor.execute(() -> {
            final VocabularyEntity vocabulary = vocabularyDao.getVocabularyById(vocabularyId);
            runOnUiThread(() -> {
                currentVocabulary = vocabulary;
                btnBookmark.setEnabled(currentVocabulary != null);
                updateFavoriteIcon();
            });
        });
    }

    private void updateFavoriteIcon() {
        boolean isFavorite = currentVocabulary != null && currentVocabulary.isFavorite();
        btnBookmark.setImageResource(
                isFavorite ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline
        );
        btnBookmark.setContentDescription(getString(
                isFavorite ? R.string.remove_from_favorites : R.string.add_to_favorites
        ));
    }

    private void toggleFavorite() {
        if (currentVocabulary == null) return;

        boolean newFavoriteState = !currentVocabulary.isFavorite();
        currentVocabulary.setFavorite(newFavoriteState);
        updateFavoriteIcon();

        dbExecutor.execute(() ->
                vocabularyDao.updateFavorite(vocabularyId, newFavoriteState)
        );
    }

    private void loadVocabularyWords() {
        if (vocabularyId == -1) return;

        dbExecutor.execute(() -> {
            final List<WordEntity> words = wordDao.getWordsByVocabularyId(vocabularyId);
            runOnUiThread(() -> {
                activeWordList = words;
                initStudyMode();
            });
        });
    }

    private void initStudyMode() {
        if (activeWordList == null || activeWordList.isEmpty()) {
            Toast.makeText(this, "단어장에 수록된 단어가 없습니다. 편집 화면에서 단어를 추가해 보세요!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // 특정 단어장에 이전에 공부하던 저장된 위치가 있다면 불러오기
        SharedPreferences prefs = getSharedPreferences("StudyPrefs", MODE_PRIVATE);
        int lastSavedProgress = prefs.getInt("vocab_progress_" + vocabularyId, 1);
        
        currentStudyIndex = lastSavedProgress - 1;
        if (currentStudyIndex < 0 || currentStudyIndex >= activeWordList.size()) {
            currentStudyIndex = 0;
        }

        resetCardVisibilityState();
        bindWordToFlashcard();
    }

    private void bindWordToFlashcard() {
        if (activeWordList == null || activeWordList.isEmpty() || currentStudyIndex >= activeWordList.size()) {
            return;
        }

        WordEntity word = activeWordList.get(currentStudyIndex);

        // 진행률 텍스팅
        tvStudyProgress.setText((currentStudyIndex + 1) + " / " + activeWordList.size());
        
        int percent = (int) (((float) (currentStudyIndex + 1) / activeWordList.size()) * 100);
        pbStudy.setProgress(percent);

        // 현재 단어와 뜻 데이터 매핑
        tvStudyWord.setText(word.getWord());
        tvStudyMeaning.setText(word.getMeaning());
        updateWordCardVisibility();
        updateMeaningCardVisibility();

        // SharedPreferences에 최신 단어장 및 진행 진척도 저장
        SharedPreferences prefs = getSharedPreferences("StudyPrefs", MODE_PRIVATE);
        prefs.edit()
                .putInt("last_studied_vocab_id", vocabularyId)
                .putInt("vocab_progress_" + vocabularyId, currentStudyIndex + 1)
                .putInt("vocab_total_" + vocabularyId, activeWordList.size())
                .putString("vocab_name_" + vocabularyId, vocabularyName)
                .apply();
    }

    private void toggleWordVisibility() {
        isWordVisible = !isWordVisible;
        updateWordCardVisibility();
    }

    private void toggleMeaningVisibility() {
        isMeaningVisible = !isMeaningVisible;
        updateMeaningCardVisibility();
    }

    private void resetCardVisibilityState() {
        isWordVisible = true;
        isMeaningVisible = false;
    }

    private void updateWordCardVisibility() {
        tvStudyWord.setVisibility(isWordVisible ? View.VISIBLE : View.GONE);
        ivWordRevealIcon.setVisibility(isWordVisible ? View.GONE : View.VISIBLE);
        tvWordRevealHint.setVisibility(isWordVisible ? View.GONE : View.VISIBLE);
        cardWordDisplay.setCardBackgroundColor(ContextCompat.getColor(
                this,
                isWordVisible ? R.color.app_surface : R.color.app_surface_subtle
        ));
        cardWordDisplay.setContentDescription(getString(
                isWordVisible ? R.string.study_hide_word : R.string.study_show_word
        ));
    }

    private void updateMeaningCardVisibility() {
        tvStudyMeaning.setVisibility(isMeaningVisible ? View.VISIBLE : View.GONE);
        ivRevealIcon.setVisibility(isMeaningVisible ? View.GONE : View.VISIBLE);
        tvRevealHint.setVisibility(isMeaningVisible ? View.GONE : View.VISIBLE);
        cardMeaningDisplay.setCardBackgroundColor(ContextCompat.getColor(
                this,
                isMeaningVisible ? R.color.app_surface : R.color.app_surface_subtle
        ));
        cardMeaningDisplay.setContentDescription(getString(
                isMeaningVisible ? R.string.study_hide_meaning : R.string.study_show_meaning
        ));
    }

    private void navigateStudyCard(int action) {
        if (activeWordList == null || activeWordList.isEmpty()) return;

        int targetIndex = currentStudyIndex + action;
        if (targetIndex < 0) {
            AppToast.show(this, "첫 번째 단어예요.");
            return;
        }

        if (targetIndex >= activeWordList.size()) {
            showStudyCompleteDialog();
            return;
        }

        currentStudyIndex = targetIndex;
        resetCardVisibilityState();
        bindWordToFlashcard();
    }

    private void showStudyCompleteDialog() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (studyCompleteDialog != null && studyCompleteDialog.isShowing()) {
            return;
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_study_complete, null);
        studyCompleteDialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        MaterialButton btnRestart = dialogView.findViewById(R.id.btn_study_complete_restart);
        MaterialButton btnList = dialogView.findViewById(R.id.btn_study_complete_list);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btn_study_complete_confirm);

        btnRestart.setOnClickListener(v -> {
            restartStudyFromBeginning();
            studyCompleteDialog.dismiss();
        });
        btnList.setOnClickListener(v -> {
            studyCompleteDialog.dismiss();
            finish();
        });
        btnConfirm.setOnClickListener(v -> studyCompleteDialog.dismiss());

        studyCompleteDialog.setOnDismissListener(dialog -> studyCompleteDialog = null);
        studyCompleteDialog.setCancelable(false);
        studyCompleteDialog.setCanceledOnTouchOutside(false);
        studyCompleteDialog.show();

        Window window = studyCompleteDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
        }
    }

    private void restartStudyFromBeginning() {
        currentStudyIndex = 0;
        resetCardVisibilityState();
        bindWordToFlashcard();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (studyCompleteDialog != null && studyCompleteDialog.isShowing()) {
            studyCompleteDialog.dismiss();
        }
        studyCompleteDialog = null;
        if (dbExecutor != null && !dbExecutor.isShutdown()) {
            dbExecutor.shutdown();
        }
    }
}
