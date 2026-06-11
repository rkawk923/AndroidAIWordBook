package com.example.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.model.VocabularyEntity;

import java.util.List;

/**
 * Vocabulary 테이블에 특화된 SQL 쿼리와 데이터베이스 작업을 처리하는 데이터 액세스 객체(DAO)입니다.
 */
@Dao
public interface VocabularyDao {

    // 즐겨찾기 단어장을 먼저 표시하고, 각 그룹 안에서는 기존 최신 등록 순서를 유지합니다.
    @Query("SELECT * FROM vocabulary ORDER BY is_favorite DESC, id DESC")
    List<VocabularyEntity> getAllVocabularies();

    // ID로 특정 단어장을 조회하는 쿼리
    @Query("SELECT * FROM vocabulary WHERE id = :id LIMIT 1")
    VocabularyEntity getVocabularyById(int id);

    // 검색어가 단어장 이름에 포함되어 있는지 필터링하는 쿼리
    @Query("SELECT * FROM vocabulary WHERE name LIKE :searchQuery ORDER BY is_favorite DESC, id DESC")
    List<VocabularyEntity> searchVocabularies(String searchQuery);

    @Query("UPDATE vocabulary SET is_favorite = :isFavorite WHERE id = :vocabularyId")
    void updateFavorite(int vocabularyId, boolean isFavorite);

    // 단어장을 새로 추가하는 메소드 (자동 생성된 id가 반환됩니다)
    @Insert
    long insert(VocabularyEntity vocabulary);

    // 단어장 정보를 수정하는 메소드 (이름, 설명 등 변경)
    @Update
    void update(VocabularyEntity vocabulary);

    // 단어장을 완전히 삭제하는 메소드 (외래키 Cascade 설정으로 해당 단어장에 들은 단어도 같이 삭제됩니다)
    @Delete
    void delete(VocabularyEntity vocabulary);
}
