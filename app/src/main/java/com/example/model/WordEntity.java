package com.example.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 단어 데이터를 저장하는 Room 데이터베이스 엔티티 클래스입니다.
 * 각 단어는 고유 ID, 소속된 단어장 ID(외래키), 영단어, 한글 뜻 필드를 가집니다.
 *
 * 외래키(ForeignKey) 설정을 통해 소속 단어장 삭제 시(ON DELETE CASCADE)
 * 해당 단어장에 포함된 모든 단어들이 데이터베이스에서 자동으로 같이 삭제되도록 하였습니다.
 */
@Entity(
    tableName = "words",
    foreignKeys = @ForeignKey(
        entity = VocabularyEntity.class,
        parentColumns = "id",
        childColumns = "vocabularyId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("vocabularyId")}
)
public class WordEntity {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "vocabularyId")
    private int vocabularyId;

    @ColumnInfo(name = "word")
    private String word;

    @ColumnInfo(name = "meaning")
    private String meaning;

    // 생성자
    public WordEntity(int vocabularyId, String word, String meaning) {
        this.vocabularyId = vocabularyId;
        this.word = word;
        this.meaning = meaning;
    }

    // 기본 생성자
    public WordEntity() {
    }

    // Getter & Setter 메소드들
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getVocabularyId() {
        return vocabularyId;
    }

    public void setVocabularyId(int vocabularyId) {
        this.vocabularyId = vocabularyId;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public String getMeaning() {
        return meaning;
    }

    public void setMeaning(String meaning) {
        this.meaning = meaning;
    }
}
