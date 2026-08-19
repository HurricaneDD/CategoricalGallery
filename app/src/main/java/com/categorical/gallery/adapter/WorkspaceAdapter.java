package com.categorical.gallery.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.categorical.gallery.R;
import com.categorical.gallery.model.Workspace;
import com.categorical.gallery.util.FileUtils;

import java.util.List;

public class WorkspaceAdapter extends RecyclerView.Adapter<WorkspaceAdapter.ViewHolder> {

    private final Context context;
    private List<Workspace> workspaces;
    private final OnWorkspaceClickListener clickListener;
    private final OnWorkspaceLongClickListener longClickListener;

    public interface OnWorkspaceClickListener {
        void onWorkspaceClick(Workspace workspace, int position);
    }

    public interface OnWorkspaceLongClickListener {
        void onWorkspaceLongClick(Workspace workspace, int position);
    }

    public WorkspaceAdapter(Context context, List<Workspace> workspaces,
                            OnWorkspaceClickListener clickListener,
                            OnWorkspaceLongClickListener longClickListener) {
        this.context = context;
        this.workspaces = workspaces;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    public void setWorkspaces(List<Workspace> workspaces) {
        this.workspaces = workspaces;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_workspace, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Workspace workspace = workspaces.get(position);
        holder.tvName.setText(workspace.getName());

        boolean hasAlbums = FileUtils.hasAlbums(workspace.getName());
        if (!hasAlbums) {
            // No albums: show empty state
            holder.layoutCovers.setVisibility(View.GONE);
            holder.layoutEmpty.setVisibility(View.VISIBLE);
            clearCovers(holder);
        } else {
            holder.layoutCovers.setVisibility(View.VISIBLE);
            holder.layoutEmpty.setVisibility(View.GONE);

            List<String> covers = FileUtils.getWorkspaceCovers(workspace.getName());
            ImageView[] imageViews = {
                    holder.ivCover1, holder.ivCover2, holder.ivCover3, holder.ivCover4
            };

            for (int i = 0; i < 4; i++) {
                if (i < covers.size() && covers.get(i) != null) {
                    imageViews[i].setVisibility(View.VISIBLE);
                    Glide.with(context)
                            .load(covers.get(i))
                            .centerCrop()
                            .into(imageViews[i]);
                } else {
                    imageViews[i].setVisibility(View.INVISIBLE);
                    Glide.with(context).clear(imageViews[i]);
                }
            }
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    clickListener.onWorkspaceClick(workspace, pos);
                }
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    longClickListener.onWorkspaceLongClick(workspace, pos);
                    return true;
                }
            }
            return false;
        });
    }

    private void clearCovers(ViewHolder holder) {
        ImageView[] imageViews = {
                holder.ivCover1, holder.ivCover2, holder.ivCover3, holder.ivCover4
        };
        for (ImageView iv : imageViews) {
            Glide.with(context).clear(iv);
        }
    }

    @Override
    public int getItemCount() {
        return workspaces == null ? 0 : workspaces.size();
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        clearCovers(holder);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final LinearLayout layoutCovers;
        final LinearLayout layoutEmpty;
        final ImageView ivCover1;
        final ImageView ivCover2;
        final ImageView ivCover3;
        final ImageView ivCover4;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvWorkspaceName);
            layoutCovers = itemView.findViewById(R.id.layoutCovers);
            layoutEmpty = itemView.findViewById(R.id.layoutEmpty);
            ivCover1 = itemView.findViewById(R.id.ivCover1);
            ivCover2 = itemView.findViewById(R.id.ivCover2);
            ivCover3 = itemView.findViewById(R.id.ivCover3);
            ivCover4 = itemView.findViewById(R.id.ivCover4);
        }
    }
}
