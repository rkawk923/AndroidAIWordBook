package com.example.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.model.WordEntity;

import java.util.List;

/**
 * Words 테이블에 특화된 SQL 쿼리와 데이터베이스 작업을 처리하는 데이터 액세스 객체(DAO)입니다.
 */
@Dao
public interface WordDao {

    // 특정 단어장에 소속된 단어 목록만을 조건으로 가져오는 쿼리
    @Query("SELECT * FROM words WHERE vocabularyId = :vocabularyId ORDER BY id ASC")
    List<WordEntity> getWordsByVocabularyId(int vocabularyId);

    // 특정 단어장에 소속된 단어 전체를 일괄 삭제하는 쿼리
    @Query("DELETE FROM words WHERE vocabularyId = :vocabularyId")
    void deleteWordsByVocabularyId(int vocabularyId);

    // 단어를 새로 추가하는 메소드
    @Insert
    long insert(WordEntity word);

    // 단어 정보(스펠링, 뜻)를 수정하는 메소드
    @Update
    void update(WordEntity word);

    // 단어를 삭제하는 메소드
    @Delete
    void delete(WordEntity word);
}
