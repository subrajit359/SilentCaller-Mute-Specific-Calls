package com.silentcaller.adapter;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.silentcaller.R;
import com.silentcaller.model.SilentNumber;

import java.util.Date;

/**
 * RecyclerView adapter for displaying silent numbers.
 * Uses ListAdapter with DiffUtil for efficient list updates.
 */
public class SilentNumberAdapter extends ListAdapter<SilentNumber, SilentNumberAdapter.ViewHolder> {

    private OnItemActionListener listener;

    public interface OnItemActionListener {
        void onRemoveClick(SilentNumber silentNumber);
    }

    // DiffUtil callback — only re-renders items that actually changed
    private static final DiffUtil.ItemCallback<SilentNumber> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<SilentNumber>() {
                @Override
                public boolean areItemsTheSame(@NonNull SilentNumber a, @NonNull SilentNumber b) {
                    return a.getId() == b.getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull SilentNumber a, @NonNull SilentNumber b) {
                    return a.getPhoneNumber().equals(b.getPhoneNumber())
                            && nullSafeEquals(a.getLabel(), b.getLabel())
                            && a.getAddedAt() == b.getAddedAt();
                }

                private boolean nullSafeEquals(String a, String b) {
                    if (a == null && b == null) return true;
                    if (a == null || b == null) return false;
                    return a.equals(b);
                }
            };

    public SilentNumberAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnItemActionListener(OnItemActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_silent_number, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SilentNumber item = getItem(position);
        holder.bind(item, listener);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvPhoneNumber;
        private final TextView tvLabel;
        private final TextView tvAddedAt;
        private final Button btnRemove;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPhoneNumber = itemView.findViewById(R.id.tv_phone_number);
            tvLabel = itemView.findViewById(R.id.tv_label);
            tvAddedAt = itemView.findViewById(R.id.tv_added_at);
            btnRemove = itemView.findViewById(R.id.btn_remove);
        }

        public void bind(SilentNumber item, OnItemActionListener listener) {
            tvPhoneNumber.setText(item.getPhoneNumber());

            // Show label if available, otherwise hide it
            if (item.getLabel() != null && !item.getLabel().trim().isEmpty()) {
                tvLabel.setText(item.getLabel());
                tvLabel.setVisibility(View.VISIBLE);
            } else {
                tvLabel.setVisibility(View.GONE);
            }

            // Format the date added
            String dateStr = DateFormat.format("MMM dd, yyyy", new Date(item.getAddedAt())).toString();
            tvAddedAt.setText(itemView.getContext().getString(R.string.added_on, dateStr));

            btnRemove.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRemoveClick(item);
                }
            });
        }
    }
}
