package com.categorical.gallery.adapter;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.categorical.gallery.R;
import com.categorical.gallery.model.SystemAlbum;

import java.util.List;

/**
 * Adapter for displaying system photo albums in a RecyclerView.
 * Supports single-selection mode: tapping an item selects it and gives
 * visual feedback (border highlight + check mark overlay).
 */
public class SystemAlbumAdapter extends RecyclerView.Adapter<SystemAlbumAdapter.AlbumViewHolder> {

    /**
     * Callback invoked when an album is selected by the user.
     */
    public interface OnAlbumSelectedListener {
        void onAlbumSelected(SystemAlbum album);
    }

    private final Context context;
    private final List<SystemAlbum> albums;
    private final OnAlbumSelectedListener listener;

    /** Index of the currently selected album, -1 if none. */
    private int selectedPosition = -1;

    public SystemAlbumAdapter(Context context, List<SystemAlbum> albums, OnAlbumSelectedListener listener) {
        this.context = context;
        this.albums = albums;
        this.listener = listener;
    }

    @NonNull
    @Override
    public AlbumViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_system_album, parent, false);
        return new AlbumViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlbumViewHolder holder, int position) {
        SystemAlbum album = albums.get(position);

        holder.textName.setText(album.getName());
        holder.textCount.setText(context.getString(R.string.photo_count_format, album.getPhotoCount()));

        String coverUri = album.getCoverUri();
        if (coverUri != null && !coverUri.isEmpty()) {
            Glide.with(context)
                    .load(Uri.parse(coverUri))
                    .centerCrop()
                    .placeholder(R.color.colorEmptyBg)
                    .error(R.color.colorEmptyBg)
                    .into(holder.imageCover);
        } else {
            holder.imageCover.setImageResource(0);
            holder.imageCover.setBackgroundColor(
                    context.getResources().getColor(R.color.colorEmptyBg));
        }

        boolean selected = position == selectedPosition;
        holder.itemView.setSelected(selected);
        holder.imageCheck.setVisibility(selected ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;

            int previous = selectedPosition;
            if (previous == pos) {
                // Toggle off the current selection
                selectedPosition = -1;
                notifyItemChanged(pos);
                if (listener != null) listener.onAlbumSelected(null);
            } else {
                selectedPosition = pos;
                if (previous >= 0) notifyItemChanged(previous);
                notifyItemChanged(pos);
                if (listener != null) listener.onAlbumSelected(albums.get(pos));
            }
        });
    }

    @Override
    public int getItemCount() {
        return albums == null ? 0 : albums.size();
    }

    /**
     * @return the currently selected album, or null if none selected.
     */
    public SystemAlbum getSelectedAlbum() {
        if (selectedPosition >= 0 && selectedPosition < getItemCount()) {
            return albums.get(selectedPosition);
        }
        return null;
    }

    static class AlbumViewHolder extends RecyclerView.ViewHolder {
        final ImageView imageCover;
        final ImageView imageCheck;
        final TextView textName;
        final TextView textCount;

        AlbumViewHolder(@NonNull View itemView) {
            super(itemView);
            imageCover = itemView.findViewById(R.id.imageCover);
            imageCheck = itemView.findViewById(R.id.imageCheck);
            textName = itemView.findViewById(R.id.textName);
            textCount = itemView.findViewById(R.id.textCount);
        }
    }
}
