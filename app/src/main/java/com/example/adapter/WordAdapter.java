package com.example.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.model.WordEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 각각의 특정 영단어/뜻 세트 목록을 RecyclerView에 표기해 주는 어댑터 클래스입니다.
 */
public class WordAdapter extends RecyclerView.Adapter<WordAdapter.WordViewHolder> {

    private List<WordEntity> wordList = new ArrayList<>();
    private final OnWordClickListener listener;

    /**
     * 영단어 수정 및 삭제 이벤트를 처리하기 위한 인터페이스입니다.
     */
    public interface OnWordClickListener {
        void onEditClick(WordEntity word);
        void onDeleteClick(WordEntity word);
    }

    public WordAdapter(OnWordClickListener listener) {
        this.listener = listener;
    }

    /**
     * 어댑터 내부 단어 리스트 데이터를 안전하게 갱신하는 메소드입니다.
     */
    public void setWordList(List<WordEntity> newList) {
        this.wordList = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public WordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_word, parent, false);
        return new WordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WordViewHolder holder, int position) {
        WordEntity currentWord = wordList.get(position);
        holder.bind(currentWord, listener);
    }

    @Override
    public int getItemCount() {
        return wordList != null ? wordList.size() : 0;
    }

    /**
     * 영단어 데이터 행 레이아웃을 바인딩해주는 뷰홀더 Inner 클래스입니다.
     */
    public static class WordViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvWord;
        private final TextView tvMeaning;
        private final ImageButton btnEdit;
        private final ImageButton btnDelete;

        public WordViewHolder(@NonNull View itemView) {
            super(itemView);
            tvWord = itemView.findViewById(R.id.tv_word);
            tvMeaning = itemView.findViewById(R.id.tv_meaning);
            btnEdit = itemView.findViewById(R.id.btn_edit_word);
            btnDelete = itemView.findViewById(R.id.btn_delete_word);
        }

        public void bind(final WordEntity item, final OnWordClickListener listener) {
            tvWord.setText(item.getWord());
            tvMeaning.setText(item.getMeaning());

            // 수정 아이콘 클릭 시 콜백
            btnEdit.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEditClick(item);
                }
            });

            // 삭제 아이콘 클릭 시 콜백
            btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteClick(item);
                }
            });
        }
    }
}
