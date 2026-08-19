package com.categorical.gallery.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.categorical.gallery.R;
import com.categorical.gallery.model.Album;
import com.categorical.gallery.model.Photo;
import com.categorical.gallery.util.FileUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying albums in a 3-column grid.
 * The last item is a "create new album" entry.
 */
public class AlbumAdapter extends RecyclerView.Adapter<AlbumAdapter.AlbumViewHolder> {

    private static final int TYPE_ALBUM = 0;
    private static final int TYPE_ADD = 1;

    private final Context context;
    private final List<Album> albums = new ArrayList<>();
    private final OnAlbumClickListener listener;

    public interface OnAlbumClickListener {
        void onAlbumClick(Album album, int position);
        void onAddClick();
    }

    public AlbumAdapter(Context context, OnAlbumClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setAlbums(List<Album> list) {
        albums.clear();
        if (list != null) {
            albums.addAll(list);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return position < albums.size() ? TYPE_ALBUM : TYPE_ADD;
    }

    @Override
    public int getItemCount() {
        return albums.size() + 1;
    }

    @NonNull
    @Override
    public AlbumViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_album, parent, false);
        return new AlbumViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlbumViewHolder holder, int position) {
        if (getItemViewType(position) == TYPE_ADD) {
            bindAdd(holder);
        } else {
            bindAlbum(holder, position);
        }
    }

    private void bindAdd(AlbumViewHolder holder) {
        holder.coverContainer.setBackgroundColor(
                ContextCompat.getColor(context, R.color.colorPrimary));
        holder.cover.setImageResource(R.drawable.ic_add);
        holder.cover.setContentDescription(context.getString(R.string.create_new_album));
        holder.empty.setVisibility(View.GONE);
        holder.name.setText(R.string.create_new_album);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onAddClick();
        });
    }

    private void bindAlbum(AlbumViewHolder holder, int position) {
        Album album = albums.get(position);
        holder.name.setText(album.getName());
        holder.coverContainer.setBackgroundColor(
                ContextCompat.getColor(context, R.color.colorEmptyBg));

        Photo cover = FileUtils.getEarliestPhoto(album.getWorkspaceName(), album.getName());
        if (cover != null) {
            holder.empty.setVisibility(View.GONE);
            holder.cover.setContentDescription(album.getName());
            Glide.with(context)
                    .load(cover.getFile())
                    .centerCrop()
                    .into(holder.cover);
        } else {
            holder.empty.setVisibility(View.VISIBLE);
            holder.cover.setImageDrawable(null);
            holder.cover.setContentDescription(album.getName());
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && pos < albums.size()) {
                    listener.onAlbumClick(albums.get(pos), pos);
                }
            }
        });
    }

    static class AlbumViewHolder extends RecyclerView.ViewHolder {
        FrameLayout coverContainer;
        ImageView cover;
        TextView empty;
        TextView name;

        AlbumViewHolder(@NonNull View itemView) {
            super(itemView);
            coverContainer = itemView.findViewById(R.id.cover_container);
            cover = itemView.findViewById(R.id.iv_cover);
            empty = itemView.findViewById(R.id.tv_empty);
            name = itemView.findViewById(R.id.tv_album_name);
        }
    }
}
