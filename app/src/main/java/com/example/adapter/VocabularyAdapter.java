package com.example.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.model.VocabularyEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 단어장 목록 RecyclerView를 위한 어댑터 클래스입니다.
 * 뷰 홀더 바인딩과 각 버튼(수정, 삭제) 클릭 이벤트를 콜백 인터페이스를 통해 전달합니다.
 */
public class VocabularyAdapter extends RecyclerView.Adapter<VocabularyAdapter.VocabularyViewHolder> {

    private List<VocabularyEntity> vocabularyList = new ArrayList<>();
    private final OnVocabularyClickListener listener;

    /**
     * 메인 화면 액티비티에서 특정 이벤트 콜백을 수신받기 위한 인터페이스 정의입니다.
     */
    public interface OnVocabularyClickListener {
        void onItemClick(VocabularyEntity vocabulary); // 단어장 클릭 시 상세 보기로
        void onFavoriteClick(VocabularyEntity vocabulary); // 단어장 즐겨찾기 전환
        void onEditClick(VocabularyEntity vocabulary); // 단어장 수정 버튼 클릭
        void onDeleteClick(VocabularyEntity vocabulary); // 단어장 삭제 버튼 클릭
    }

    public VocabularyAdapter(OnVocabularyClickListener listener) {
        this.listener = listener;
    }

    /**
     * 단어장 데이터 목록을 갱신하는 메소드입니다.
     */
    public void setVocabularyList(List<VocabularyEntity> newList) {
        this.vocabularyList = newList;
        notifyDataSetChanged(); // 변경 사실을 리플래시하여 다시 그림
    }

    @NonNull
    @Override
    public VocabularyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_vocabulary, parent, false);
        return new VocabularyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VocabularyViewHolder holder, int position) {
        VocabularyEntity currentItem = vocabularyList.get(position);
        holder.bind(currentItem, listener);
    }

    @Override
    public int getItemCount() {
        return vocabularyList != null ? vocabularyList.size() : 0;
    }

    /**
     * 각각의 아이템 목록 요소를 관리하고 데이터를 뷰에 맵핑하는 뷰홀더 내부(Inner) 클래스입니다.
     */
    public static class VocabularyViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvName;
        private final TextView tvDesc;
        private final ImageButton btnFavorite;
        private final View btnEdit;
        private final View btnDelete;

        public VocabularyViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_vocab_name);
            tvDesc = itemView.findViewById(R.id.tv_vocab_desc);
            btnFavorite = itemView.findViewById(R.id.iv_favorite);
            btnEdit = itemView.findViewById(R.id.btn_edit);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }

        public void bind(final VocabularyEntity item, final OnVocabularyClickListener listener) {
            tvName.setText(item.getName());
            tvDesc.setText(item.getDescription());
            bindFavoriteState(item);

            // 카드 영역 전체 클릭 시 콜백
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });

            btnFavorite.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onFavoriteClick(item);
                }
            });

            // 수정 버튼 클릭 시
            btnEdit.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEditClick(item);
                }
            });

            // 삭제 버튼 클릭 시
            btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteClick(item);
                }
            });
        }

        private void bindFavoriteState(VocabularyEntity item) {
            btnFavorite.setImageResource(
                    item.isFavorite()
                            ? R.drawable.ic_heart_filled
                            : R.drawable.ic_heart_outline
            );
            btnFavorite.setContentDescription(itemView.getContext().getString(
                    item.isFavorite()
                            ? R.string.remove_from_favorites
                            : R.string.add_to_favorites
            ));
        }
    }
}
