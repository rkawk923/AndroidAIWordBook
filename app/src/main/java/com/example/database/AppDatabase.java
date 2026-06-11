package com.example.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.model.VocabularyEntity;
import com.example.model.WordEntity;

/**
 * 어플리케이션의 메인 로컬 Room 데이터베이스 클래스입니다.
 * 버전 정보 및 보관할 엔티티 스키마들을 등록합니다.
 * 싱글톤 패턴(Singleton)으로 리소스를 효율적으로 재활용할 수 있게 설계하였습니다.
 */
@Database(entities = {VocabularyEntity.class, WordEntity.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "ALTER TABLE vocabulary "
                            + "ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0"
            );
        }
    };

    // 각각의 DAO 인터페이스의 추상 메소드 구현체는 Room에 의해 자동 빌드됩니다.
    public abstract VocabularyDao vocabularyDao();
    public abstract WordDao wordDao();

    /**
     * 동시성 접근(Concurrency Thread-Safe)을 방지하는 싱글톤 객체 획득 메소드입니다.
     */
    public static AppDatabase getInstance(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "vocabulary_book_database"
                    )
                    .addMigrations(MIGRATION_1_2)
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
