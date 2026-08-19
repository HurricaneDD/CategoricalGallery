package com.categorical.gallery.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.categorical.gallery.R;
import com.categorical.gallery.model.Photo;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying photos in a 3-column grid.
 * The last item is an "import images" entry.
 */
public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {

    private static final int TYPE_PHOTO = 0;
    private static final int TYPE_ADD = 1;

    private final Context context;
    private final List<Photo> photos = new ArrayList<>();
    private final OnPhotoClickListener listener;

    public interface OnPhotoClickListener {
        void onPhotoClick(int position);
        void onAddClick();
        void onPhotoLongClick(int position);
    }

    public PhotoAdapter(Context context, OnPhotoClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setPhotos(List<Photo> list) {
        photos.clear();
        if (list != null) {
            photos.addAll(list);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return position < photos.size() ? TYPE_PHOTO : TYPE_ADD;
    }

    @Override
    public int getItemCount() {
        return photos.size() + 1;
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_photo, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        if (getItemViewType(position) == TYPE_ADD) {
            bindAdd(holder);
        } else {
            bindPhoto(holder, position);
        }
    }

    private void bindAdd(PhotoViewHolder holder) {
        holder.container.setBackgroundColor(
                ContextCompat.getColor(context, R.color.colorPrimary));
        holder.photo.setImageResource(R.drawable.ic_add);
        holder.pin.setVisibility(View.GONE);
        holder.itemView.setOnLongClickListener(null);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onAddClick();
        });
    }

    private void bindPhoto(PhotoViewHolder holder, int position) {
        Photo photo = photos.get(position);
        holder.container.setBackgroundColor(
                ContextCompat.getColor(context, R.color.colorEmptyBg));
        holder.photo.setContentDescription(photo.getName());
        Glide.with(context)
                .load(photo.getFile())
                .centerCrop()
                .into(holder.photo);
        holder.pin.setVisibility(photo.isPinned() ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && pos < photos.size()) {
                    listener.onPhotoClick(pos);
                }
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && pos < photos.size()) {
                    listener.onPhotoLongClick(pos);
                }
                return true;
            }
            return false;
        });
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        FrameLayout container;
        ImageView photo;
        ImageView pin;

        PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            container = itemView.findViewById(R.id.photo_container);
            photo = itemView.findViewById(R.id.iv_photo);
            pin = itemView.findViewById(R.id.iv_pin);
        }
    }
}
