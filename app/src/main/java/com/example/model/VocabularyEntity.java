package com.example.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 단어장 폴더 정보를 저장하는 Room 데이터베이스 엔티티 클래스입니다.
 * 각 단어장은 고유 ID, 단어장 이름, 그리고 설명을 가집니다.
 */
@Entity(tableName = "vocabulary")
public class VocabularyEntity {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "description")
    private String description;

    // Room과 코드 사용을 위한 생성자
    public VocabularyEntity(String name, String description) {
        this.name = name;
        this.description = description;
    }

    // 기본 생성자
    public VocabularyEntity() {
    }

    // Getter & Setter 메소드들
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
