package com.example;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.database.AppDatabase;
import com.example.database.WordDao;
import com.example.model.WordEntity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

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
    private WordDao wordDao;
    private ExecutorService dbExecutor;

    private List<WordEntity> activeWordList = new ArrayList<>();

    // 플래시 카드 학습 상태 및 UI 요소들
    private int currentStudyIndex = 0;
    private boolean isMeaningRevealed = false;

    private TextView tvStudyProgress;
    private ProgressBar pbStudy;
    private TextView tvStudyWord;
    private TextView tvStudyMeaning;
    private MaterialCardView cardMeaningDisplay;
    private View layoutRevealContainer;
    private ImageView ivRevealIcon;
    private TextView tvRevealHint;

    private MaterialButton btnStudyPrev;
    private MaterialButton btnStudyNext;
    private ImageView btnBookmark;
    private boolean isBookmarked = false;

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
        wordDao = db.wordDao();
        dbExecutor = Executors.newSingleThreadExecutor();

        // 툴바 연결
        TextView tvTitle = findViewById(R.id.tv_detail_title);
        tvTitle.setText(vocabularyName);

        findViewById(R.id.btn_detail_back).setOnClickListener(v -> finish());

        btnBookmark = findViewById(R.id.btn_detail_bookmark);
        btnBookmark.setOnClickListener(v -> {
            isBookmarked = !isBookmarked;
            if (isBookmarked) {
                btnBookmark.setImageResource(android.R.drawable.btn_star_big_on);
                Toast.makeText(VocabularyDetailActivity.this, "단어장이 즐겨찾기에 등록되었습니다.", Toast.LENGTH_SHORT).show();
            } else {
                btnBookmark.setImageResource(android.R.drawable.btn_star_big_off);
                Toast.makeText(VocabularyDetailActivity.this, "즐겨찾기 해제되었습니다.", Toast.LENGTH_SHORT).show();
            }
        });

        // 카드 컴포넌트들 맵핑
        tvStudyProgress = findViewById(R.id.tv_study_progress);
        pbStudy = findViewById(R.id.pb_study);
        tvStudyWord = findViewById(R.id.tv_study_word);
        tvStudyMeaning = findViewById(R.id.tv_study_meaning);
        cardMeaningDisplay = findViewById(R.id.card_meaning_display);
        layoutRevealContainer = findViewById(R.id.layout_reveal_container);
        ivRevealIcon = findViewById(R.id.iv_reveal_icon);
        tvRevealHint = findViewById(R.id.tv_reveal_hint);

        btnStudyPrev = findViewById(R.id.btn_study_prev);
        btnStudyNext = findViewById(R.id.btn_study_next);

        // 탭하여 뜻 보기 설정
        cardMeaningDisplay.setOnClickListener(v -> revealMeaning());

        // 이전/다음
        btnStudyPrev.setOnClickListener(v -> navigateStudyCard(-1));
        btnStudyNext.setOnClickListener(v -> navigateStudyCard(1));

        // 단어 로드 실행
        loadVocabularyWords();
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

        isMeaningRevealed = false;
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

        // 앞 단어 매핑
        tvStudyWord.setText(word.getWord());

        // 뜻 영역 복구
        isMeaningRevealed = false;
        tvStudyMeaning.setText(word.getMeaning());
        tvStudyMeaning.setVisibility(View.GONE);

        ivRevealIcon.setVisibility(View.VISIBLE);
        tvRevealHint.setVisibility(View.VISIBLE);
        cardMeaningDisplay.setCardBackgroundColor(android.graphics.Color.parseColor("#eeedf3"));

        // SharedPreferences에 최신 단어장 및 진행 진척도 저장
        SharedPreferences prefs = getSharedPreferences("StudyPrefs", MODE_PRIVATE);
        prefs.edit()
                .putInt("last_studied_vocab_id", vocabularyId)
                .putInt("vocab_progress_" + vocabularyId, currentStudyIndex + 1)
                .putInt("vocab_total_" + vocabularyId, activeWordList.size())
                .putString("vocab_name_" + vocabularyId, vocabularyName)
                .apply();
    }

    private void revealMeaning() {
        if (isMeaningRevealed) return;
        isMeaningRevealed = true;

        tvStudyMeaning.setVisibility(View.VISIBLE);
        ivRevealIcon.setVisibility(View.GONE);
        tvRevealHint.setVisibility(View.GONE);
        cardMeaningDisplay.setCardBackgroundColor(android.graphics.Color.parseColor("#ffffff"));
    }

    private void navigateStudyCard(int action) {
        if (activeWordList == null || activeWordList.isEmpty()) return;

        int targetIndex = currentStudyIndex + action;
        if (targetIndex < 0) {
            Toast.makeText(this, "첫 번째 단어장 카드입니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (targetIndex >= activeWordList.size()) {
            Toast.makeText(this, "축하합니다! 해당 단어장의 모든 어휘 학습을 모두 완주하셨습니다!", Toast.LENGTH_LONG).show();
            return;
        }

        currentStudyIndex = targetIndex;
        bindWordToFlashcard();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbExecutor != null && !dbExecutor.isShutdown()) {
            dbExecutor.shutdown();
        }
    }
}
