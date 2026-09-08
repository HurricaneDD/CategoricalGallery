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
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.categorical.gallery.R;
import com.categorical.gallery.model.Album;
import com.categorical.gallery.util.SortHelper;
import com.categorical.gallery.util.ThemeHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AlbumAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_ALBUM = 0;
    public static final int TYPE_ADD = 1;
    public static final int TYPE_IMPORT = 2;
    public static final int TYPE_DIVIDER = 3;

    private static final String PAYLOAD_SELECTION = "selection";

    private final Context context;
    private final List<Album> albums = new ArrayList<>();
    private final OnAlbumClickListener listener;
    private final OnImportClickListener importListener;
    private OnMultiSelectListener multiSelectListener;
    private int sortMode = SortHelper.SORT_CUSTOM;
    private ItemTouchHelper itemTouchHelper;

    private boolean multiSelectMode = false;
    private final Set<String> selectedNames = new HashSet<>();

    public interface OnAlbumClickListener {
        void onAlbumClick(Album album, int position);
        void onAddClick();
    }

    public interface OnImportClickListener {
        void onImportClick();
    }

    public interface OnMultiSelectListener {
        void onMultiSelectModeEntered(int selectedCount);
        void onMultiSelectModeExited();
        void onSelectionChanged(int selectedCount);
    }

    public AlbumAdapter(Context context, OnAlbumClickListener listener,
                        OnImportClickListener importListener) {
        this.context = context;
        this.listener = listener;
        this.importListener = importListener;
    }

    public void setAlbums(List<Album> list) {
        albums.clear();
        if (list != null) {
            albums.addAll(list);
        }
        notifyDataSetChanged();
    }

    public void updateAlbumCover(String albumName, String coverPath) {
        for (int i = 0; i < albums.size(); i++) {
            if (albums.get(i).getName().equals(albumName)) {
                albums.get(i).setCoverPath(coverPath);
                notifyItemChanged(i);
                return;
            }
        }
    }

    public void setSortMode(int mode) {
        this.sortMode = mode;
    }

    public boolean isCustomSort() {
        return sortMode == SortHelper.SORT_CUSTOM;
    }

    public boolean isMultiSelectMode() {
        return multiSelectMode;
    }

    public void setOnMultiSelectListener(OnMultiSelectListener listener) {
        this.multiSelectListener = listener;
    }

    public boolean isDividerPosition(int position) {
        return position == albums.size();
    }

    @Override
    public int getItemViewType(int position) {
        int albumCount = albums.size();
        if (position < albumCount) return TYPE_ALBUM;
        if (position == albumCount) return TYPE_DIVIDER;
        if (position == albumCount + 1) return TYPE_ADD;
        return TYPE_IMPORT;
    }

    @Override
    public int getItemCount() {
        return albums.size() + 3;
    }

    public void attachToRecyclerView(RecyclerView recyclerView) {
        ItemTouchHelper.Callback callback = new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder holder) {
                int pos = holder.getAdapterPosition();
                if (pos < 0 || pos >= albums.size()) return 0;
                int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN |
                        ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
                return makeMovementFlags(dragFlags, 0);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) {
                int from = vh.getAdapterPosition();
                int to = target.getAdapterPosition();
                if (from < 0 || to < 0 || from >= albums.size() || to >= albums.size()) return false;
                Album moved = albums.remove(from);
                albums.add(to, moved);
                notifyItemMoved(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return false;
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder holder, int actionState) {
                super.onSelectedChanged(holder, actionState);
                if (holder != null && actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    holder.itemView.setAlpha(0.8f);
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder holder) {
                super.clearView(rv, holder);
                holder.itemView.setAlpha(1.0f);
            }
        };
        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(recyclerView);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_DIVIDER) {
            View view = inflater.inflate(R.layout.item_divider, parent, false);
            return new DividerViewHolder(view);
        }
        View view = inflater.inflate(R.layout.item_album, parent, false);
        return new AlbumViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        int viewType = getItemViewType(position);
        if (viewType == TYPE_DIVIDER) return;
        if (viewType == TYPE_ADD) {
            bindAdd((AlbumViewHolder) holder);
        } else if (viewType == TYPE_IMPORT) {
            bindImport((AlbumViewHolder) holder);
        } else {
            bindAlbum((AlbumViewHolder) holder, position);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
                                  @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            if (holder instanceof AlbumViewHolder) {
                updateSelectionState((AlbumViewHolder) holder, position);
            }
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    private void bindAdd(@NonNull AlbumViewHolder holder) {
        holder.coverContainer.setBackgroundColor(ThemeHelper.getPrimaryColor(context));
        holder.cover.setImageResource(R.drawable.ic_add);
        holder.empty.setVisibility(View.GONE);
        holder.name.setText(R.string.create_new_album);
        holder.ivSelection.setVisibility(View.GONE);
        holder.viewSelectionOverlay.setVisibility(View.GONE);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onAddClick();
        });
        holder.itemView.setOnLongClickListener(null);
    }

    private void bindImport(@NonNull AlbumViewHolder holder) {
        holder.coverContainer.setBackgroundColor(ThemeHelper.getPrimaryColor(context));
        holder.cover.setImageResource(R.drawable.ic_import);
        holder.empty.setVisibility(View.GONE);
        holder.name.setText(R.string.import_system_album);
        holder.ivSelection.setVisibility(View.GONE);
        holder.viewSelectionOverlay.setVisibility(View.GONE);
        holder.itemView.setOnClickListener(v -> {
            if (importListener != null) importListener.onImportClick();
        });
        holder.itemView.setOnLongClickListener(null);
    }

    private void bindAlbum(@NonNull AlbumViewHolder holder, int position) {
        Album album = albums.get(position);
        holder.name.setText(album.getName());
        holder.coverContainer.setBackgroundColor(
                ContextCompat.getColor(context, R.color.colorEmptyBg));

        String coverPath = album.getCoverPath();
        if (coverPath != null) {
            holder.empty.setVisibility(View.GONE);
            holder.cover.setContentDescription(album.getName());
            Glide.with(context)
                    .load(coverPath)
                    .centerCrop()
                    .into(holder.cover);
        } else {
            holder.empty.setVisibility(View.VISIBLE);
            holder.cover.setImageDrawable(null);
            holder.cover.setContentDescription(album.getName());
        }

        updateSelectionState(holder, position);

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && pos < albums.size()) {
                if (multiSelectMode) {
                    toggleSelection(albums.get(pos).getName());
                } else {
                    if (listener != null) {
                        listener.onAlbumClick(albums.get(pos), pos);
                    }
                }
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION || pos >= albums.size()) return false;
            if (multiSelectMode) {
                if (itemTouchHelper != null) {
                    itemTouchHelper.startDrag(holder);
                }
                return true;
            } else {
                enterMultiSelectMode(albums.get(pos).getName());
                if (itemTouchHelper != null) {
                    itemTouchHelper.startDrag(holder);
                }
                return true;
            }
        });
    }

    private void updateSelectionState(@NonNull AlbumViewHolder holder, int position) {
        if (multiSelectMode && position < albums.size()) {
            holder.ivSelection.setVisibility(View.VISIBLE);
            boolean selected = selectedNames.contains(albums.get(position).getName());
            if (selected) {
                holder.ivSelection.setImageResource(R.drawable.ic_multi_checked);
                holder.viewSelectionOverlay.setVisibility(View.VISIBLE);
            } else {
                holder.ivSelection.setImageResource(R.drawable.ic_multi_unchecked);
                holder.viewSelectionOverlay.setVisibility(View.GONE);
            }
        } else {
            holder.ivSelection.setVisibility(View.GONE);
            holder.viewSelectionOverlay.setVisibility(View.GONE);
        }
    }

    public void enterMultiSelectMode(String albumName) {
        multiSelectMode = true;
        selectedNames.clear();
        selectedNames.add(albumName);
        notifyItemRangeChanged(0, getItemCount(), PAYLOAD_SELECTION);
        if (multiSelectListener != null) {
            multiSelectListener.onMultiSelectModeEntered(selectedNames.size());
        }
    }

    public void exitMultiSelectMode() {
        multiSelectMode = false;
        selectedNames.clear();
        notifyItemRangeChanged(0, getItemCount(), PAYLOAD_SELECTION);
        if (multiSelectListener != null) {
            multiSelectListener.onMultiSelectModeExited();
        }
    }

    private void toggleSelection(String name) {
        if (selectedNames.contains(name)) {
            selectedNames.remove(name);
            if (selectedNames.isEmpty()) {
                exitMultiSelectMode();
                return;
            }
        } else {
            selectedNames.add(name);
        }
        notifyItemRangeChanged(0, getItemCount(), PAYLOAD_SELECTION);
        if (multiSelectListener != null) {
            multiSelectListener.onSelectionChanged(selectedNames.size());
        }
    }

    public Set<String> getSelectedNames() {
        return new HashSet<>(selectedNames);
    }

    public List<String> getAlbumNames() {
        List<String> names = new ArrayList<>();
        for (Album album : albums) {
            names.add(album.getName());
        }
        return names;
    }

    static class AlbumViewHolder extends RecyclerView.ViewHolder {
        FrameLayout coverContainer;
        ImageView cover;
        TextView empty;
        TextView name;
        View viewSelectionOverlay;
        ImageView ivSelection;

        AlbumViewHolder(@NonNull View itemView) {
            super(itemView);
            coverContainer = itemView.findViewById(R.id.cover_container);
            cover = itemView.findViewById(R.id.iv_cover);
            empty = itemView.findViewById(R.id.tv_empty);
            name = itemView.findViewById(R.id.tv_album_name);
            viewSelectionOverlay = itemView.findViewById(R.id.viewSelectionOverlay);
            ivSelection = itemView.findViewById(R.id.ivSelection);
        }
    }

    static class DividerViewHolder extends RecyclerView.ViewHolder {
        DividerViewHolder(View itemView) {
            super(itemView);
        }
    }
}
