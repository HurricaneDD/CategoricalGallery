package com.categorical.gallery.adapter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.categorical.gallery.R;
import com.categorical.gallery.model.Workspace;
import com.categorical.gallery.util.FileUtils;
import com.categorical.gallery.util.SortHelper;
import com.categorical.gallery.util.ThemeHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WorkspaceAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_WORKSPACE = 0;
    private static final int TYPE_ADD = 1;
    private static final int TYPE_DIVIDER = 2;

    private static final String PAYLOAD_SELECTION = "selection";

    private final Context context;
    private final List<Workspace> regularWorkspaces = new ArrayList<>();
    private Workspace defaultWorkspace = null;
    private final OnWorkspaceClickListener clickListener;
    private final OnAddClickListener addClickListener;
    private OnMultiSelectListener multiSelectListener;
    private int sortMode = SortHelper.SORT_CUSTOM;
    private ItemTouchHelper itemTouchHelper;

    private boolean multiSelectMode = false;
    private final Set<String> selectedNames = new HashSet<>();

    public interface OnWorkspaceClickListener {
        void onWorkspaceClick(Workspace workspace, int position);
    }

    public interface OnAddClickListener {
        void onAddClick();
    }

    public interface OnMultiSelectListener {
        void onMultiSelectModeEntered(int selectedCount);
        void onMultiSelectModeExited();
        void onSelectionChanged(int selectedCount);
    }

    public WorkspaceAdapter(Context context, List<Workspace> workspaces,
                            OnWorkspaceClickListener clickListener,
                            OnAddClickListener addClickListener) {
        this.context = context;
        this.clickListener = clickListener;
        this.addClickListener = addClickListener;
        if (workspaces != null) {
            setWorkspaces(workspaces);
        }
    }

    public void setWorkspaces(List<Workspace> workspaces) {
        regularWorkspaces.clear();
        defaultWorkspace = null;
        if (workspaces != null) {
            for (Workspace ws : workspaces) {
                if (FileUtils.isDefaultWorkspace(ws.getName())) {
                    defaultWorkspace = ws;
                } else {
                    regularWorkspaces.add(ws);
                }
            }
        }
        notifyDataSetChanged();
    }

    public void updateWorkspaceCovers(String workspaceName, List<String> covers) {
        for (int i = 0; i < regularWorkspaces.size(); i++) {
            if (regularWorkspaces.get(i).getName().equals(workspaceName)) {
                regularWorkspaces.get(i).setCoverPaths(covers);
                notifyItemChanged(i);
                return;
            }
        }
        if (defaultWorkspace != null && defaultWorkspace.getName().equals(workspaceName)) {
            defaultWorkspace.setCoverPaths(covers);
            notifyItemChanged(getDefaultPos());
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
        return position == getRegularCount();
    }

    private int getRegularCount() {
        return regularWorkspaces.size();
    }

    private int getDefaultPos() {
        return regularWorkspaces.size();
    }

    private int getDividerPos() {
        return regularWorkspaces.size() + (defaultWorkspace != null ? 1 : 0);
    }

    private int getAddPos() {
        return getDividerPos() + 1;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == getAddPos()) return TYPE_ADD;
        if (position == getDividerPos()) return TYPE_DIVIDER;
        if (defaultWorkspace != null && position == getDefaultPos()) return TYPE_WORKSPACE;
        return TYPE_WORKSPACE;
    }

    @Override
    public int getItemCount() {
        int count = regularWorkspaces.size();
        if (defaultWorkspace != null) count += 1;
        count += 2; // divider + add
        return count;
    }

    private Workspace getWorkspaceAt(int position) {
        if (defaultWorkspace != null && position == getDefaultPos()) {
            return defaultWorkspace;
        }
        if (position < regularWorkspaces.size()) {
            return regularWorkspaces.get(position);
        }
        return null;
    }

    public void attachToRecyclerView(RecyclerView recyclerView) {
        ItemTouchHelper.Callback callback = new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder holder) {
                int pos = holder.getAdapterPosition();
                if (pos < 0) return 0;
                if (getItemViewType(pos) != TYPE_WORKSPACE) return 0;
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
                if (from < 0 || to < 0) return false;
                if (getItemViewType(to) != TYPE_WORKSPACE) return false;
                if (from == getDefaultPos() || to == getDefaultPos()) return false;

                if (from < regularWorkspaces.size() && to < regularWorkspaces.size()) {
                    Workspace moved = regularWorkspaces.remove(from);
                    regularWorkspaces.add(to, moved);
                    notifyItemMoved(from, to);
                    return true;
                }
                return false;
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
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == TYPE_DIVIDER) {
            View view = inflater.inflate(R.layout.item_divider, parent, false);
            return new DividerViewHolder(view);
        }
        View view = inflater.inflate(R.layout.item_workspace, parent, false);
        return new WorkspaceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        int viewType = getItemViewType(position);
        if (viewType == TYPE_DIVIDER) return;
        if (viewType == TYPE_ADD) {
            bindAdd((WorkspaceViewHolder) holder);
        } else {
            bindWorkspace((WorkspaceViewHolder) holder, position);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
                                  @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            if (holder instanceof WorkspaceViewHolder) {
                updateSelectionState((WorkspaceViewHolder) holder, position);
            }
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    private void bindAdd(@NonNull WorkspaceViewHolder holder) {
        holder.layoutCovers.setVisibility(View.GONE);
        holder.layoutEmpty.setVisibility(View.GONE);
        holder.ivAdd.setVisibility(View.VISIBLE);
        holder.ivAdd.setBackgroundColor(ThemeHelper.getPrimaryColor(context));
        holder.tvName.setText(R.string.create_new_workspace);
        holder.ivSelection.setVisibility(View.GONE);
        holder.viewSelectionOverlay.setVisibility(View.GONE);
        holder.viewBorder.setVisibility(View.GONE);
        holder.itemView.setOnClickListener(v -> {
            if (addClickListener != null) addClickListener.onAddClick();
        });
        holder.itemView.setOnLongClickListener(null);
    }

    private void bindWorkspace(@NonNull WorkspaceViewHolder holder, int position) {
        Workspace workspace = getWorkspaceAt(position);
        if (workspace == null) return;

        boolean isDefault = FileUtils.isDefaultWorkspace(workspace.getName());
        holder.ivAdd.setVisibility(View.GONE);
        holder.tvName.setText(workspace.getName());

        if (isDefault) {
            int borderColor = ThemeHelper.getPrimaryColor(context);
            GradientDrawable border = new GradientDrawable();
            border.setColor(Color.TRANSPARENT);
            border.setStroke(dpToPx(3), borderColor);
            holder.viewBorder.setBackground(border);
            holder.viewBorder.setVisibility(View.VISIBLE);
        } else {
            holder.viewBorder.setVisibility(View.GONE);
        }

        if (!workspace.isHasAlbums()) {
            holder.layoutCovers.setVisibility(View.GONE);
            holder.layoutEmpty.setVisibility(View.VISIBLE);
            clearCovers(holder);
        } else {
            holder.layoutCovers.setVisibility(View.VISIBLE);
            holder.layoutEmpty.setVisibility(View.GONE);

            List<String> covers = workspace.getCoverPaths();
            ImageView[] imageViews = {
                    holder.ivCover1, holder.ivCover2, holder.ivCover3, holder.ivCover4
            };

            for (int i = 0; i < 4; i++) {
                if (covers != null && i < covers.size() && covers.get(i) != null) {
                    imageViews[i].setVisibility(View.VISIBLE);
                    imageViews[i].setBackgroundColor(0);
                    Glide.with(context)
                            .load(covers.get(i))
                            .centerCrop()
                            .into(imageViews[i]);
                } else {
                    imageViews[i].setVisibility(View.VISIBLE);
                    imageViews[i].setBackgroundColor(
                            ContextCompat.getColor(context, R.color.colorEmptyBg));
                    imageViews[i].setImageDrawable(null);
                    Glide.with(context).clear(imageViews[i]);
                }
            }
        }

        updateSelectionState(holder, position);

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION || getItemViewType(pos) != TYPE_WORKSPACE) {
                return;
            }
            Workspace ws = getWorkspaceAt(pos);
            if (ws == null) return;
            if (multiSelectMode) {
                toggleSelection(ws.getName());
            } else {
                if (clickListener != null) {
                    clickListener.onWorkspaceClick(ws, pos);
                }
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION || getItemViewType(pos) != TYPE_WORKSPACE) {
                return false;
            }
            Workspace ws = getWorkspaceAt(pos);
            if (ws == null) return false;
            if (multiSelectMode) {
                if (itemTouchHelper != null) {
                    itemTouchHelper.startDrag(holder);
                }
                return true;
            } else {
                enterMultiSelectMode(ws.getName());
                if (itemTouchHelper != null) {
                    itemTouchHelper.startDrag(holder);
                }
                return true;
            }
        });
    }

    private void updateSelectionState(@NonNull WorkspaceViewHolder holder, int position) {
        if (multiSelectMode) {
            holder.ivSelection.setVisibility(View.VISIBLE);
            Workspace ws = getWorkspaceAt(position);
            boolean selected = ws != null && selectedNames.contains(ws.getName());
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

    public void enterMultiSelectMode(String name) {
        multiSelectMode = true;
        selectedNames.clear();
        selectedNames.add(name);
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

    public List<String> getWorkspaceNames() {
        List<String> names = new ArrayList<>();
        for (Workspace ws : regularWorkspaces) {
            names.add(ws.getName());
        }
        if (defaultWorkspace != null) {
            names.add(defaultWorkspace.getName());
        }
        return names;
    }

    private int dpToPx(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private void clearCovers(WorkspaceViewHolder holder) {
        ImageView[] imageViews = {
                holder.ivCover1, holder.ivCover2, holder.ivCover3, holder.ivCover4
        };
        for (ImageView iv : imageViews) {
            Glide.with(context).clear(iv);
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof WorkspaceViewHolder) {
            clearCovers((WorkspaceViewHolder) holder);
        }
    }

    static class WorkspaceViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final LinearLayout layoutCovers;
        final LinearLayout layoutEmpty;
        final ImageView ivCover1;
        final ImageView ivCover2;
        final ImageView ivCover3;
        final ImageView ivCover4;
        final ImageView ivAdd;
        final View viewBorder;
        final View viewSelectionOverlay;
        final ImageView ivSelection;

        WorkspaceViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvWorkspaceName);
            layoutCovers = itemView.findViewById(R.id.layoutCovers);
            layoutEmpty = itemView.findViewById(R.id.layoutEmpty);
            ivCover1 = itemView.findViewById(R.id.ivCover1);
            ivCover2 = itemView.findViewById(R.id.ivCover2);
            ivCover3 = itemView.findViewById(R.id.ivCover3);
            ivCover4 = itemView.findViewById(R.id.ivCover4);
            ivAdd = itemView.findViewById(R.id.ivAdd);
            viewBorder = itemView.findViewById(R.id.viewBorder);
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
