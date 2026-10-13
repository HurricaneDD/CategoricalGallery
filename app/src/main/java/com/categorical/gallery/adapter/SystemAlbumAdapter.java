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

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying system photo albums in a RecyclerView.
 * Supports single-selection mode: tapping an item selects it and gives
 * visual feedback (border highlight + check mark overlay).
 * Supports real-time search filtering by album name.
 */
public class SystemAlbumAdapter extends RecyclerView.Adapter<SystemAlbumAdapter.AlbumViewHolder> {

    /**
     * Callback invoked when an album is selected by the user.
     */
    public interface OnAlbumSelectedListener {
        void onAlbumSelected(SystemAlbum album);
    }

    private final Context context;
    private final List<SystemAlbum> allAlbums = new ArrayList<>();
    private final List<SystemAlbum> displayedAlbums = new ArrayList<>();
    private final OnAlbumSelectedListener listener;

    private SystemAlbum selectedAlbum = null;

    public SystemAlbumAdapter(Context context, List<SystemAlbum> albums, OnAlbumSelectedListener listener) {
        this.context = context;
        if (albums != null) {
            this.allAlbums.addAll(albums);
            this.displayedAlbums.addAll(albums);
        }
        this.listener = listener;
    }

    public void setAlbums(List<SystemAlbum> albums) {
        this.allAlbums.clear();
        if (albums != null) {
            this.allAlbums.addAll(albums);
        }
        filter("");
    }

    public int filter(String query) {
        displayedAlbums.clear();
        String q = query == null ? "" : query.trim().toLowerCase();
        for (SystemAlbum album : allAlbums) {
            if (q.isEmpty() || (album.getName() != null && album.getName().toLowerCase().contains(q))) {
                displayedAlbums.add(album);
            }
        }
        notifyDataSetChanged();
        return displayedAlbums.size();
    }

    public void setSelectedAlbum(SystemAlbum album) {
        this.selectedAlbum = album;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AlbumViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_system_album, parent, false);
        return new AlbumViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlbumViewHolder holder, int position) {
        SystemAlbum album = displayedAlbums.get(position);

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

        boolean selected = isSameAlbum(album, selectedAlbum);
        holder.itemView.setSelected(selected);
        holder.imageCheck.setVisibility(selected ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;

            SystemAlbum clickedAlbum = displayedAlbums.get(pos);
            if (isSameAlbum(clickedAlbum, selectedAlbum)) {
                // Toggle off selection
                selectedAlbum = null;
                notifyItemChanged(pos);
                if (listener != null) listener.onAlbumSelected(null);
            } else {
                selectedAlbum = clickedAlbum;
                notifyDataSetChanged();
                if (listener != null) listener.onAlbumSelected(selectedAlbum);
            }
        });
    }

    private boolean isSameAlbum(SystemAlbum a, SystemAlbum b) {
        if (a == null || b == null) return false;
        if (a.getId() != 0 && b.getId() != 0) {
            return a.getId() == b.getId();
        }
        return a.getName() != null && a.getName().equals(b.getName());
    }

    @Override
    public int getItemCount() {
        return displayedAlbums.size();
    }

    /**
     * @return the currently selected album, or null if none selected.
     */
    public SystemAlbum getSelectedAlbum() {
        return selectedAlbum;
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
