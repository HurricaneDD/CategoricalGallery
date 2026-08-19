package com.categorical.gallery;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.categorical.gallery.model.Album;
import com.categorical.gallery.model.Photo;
import com.categorical.gallery.model.Workspace;
import com.categorical.gallery.util.FileUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * 图片选择页面：用于多图同屏展示模式中选择第二张图片。
 * <p>
 * 三级浏览：工作区列表 -> 相册列表 -> 照片网格。
 * 默认进入 Intent 传入的工作区/相册的照片列表。
 * 顶部路径面包屑可点击回退到上一级。
 * 单选一张照片后底部"确定"按钮可用，确定后把选中照片路径返回给调用方。
 */
public class PhotoSelectActivity extends AppCompatActivity {

    public static final String EXTRA_DEFAULT_WORKSPACE = "defaultWorkspaceName";
    public static final String EXTRA_DEFAULT_ALBUM = "defaultAlbumName";
    public static final String EXTRA_SELECTED_PHOTO_PATH = "selectedPhotoPath";

    private static final int LEVEL_WORKSPACE = 0;
    private static final int LEVEL_ALBUM = 1;
    private static final int LEVEL_PHOTO = 2;

    private int currentLevel = LEVEL_WORKSPACE;
    private String currentWorkspaceName;
    private String currentAlbumName;
    private String selectedPhotoPath;

    private TextView tvBreadcrumb;
    private RecyclerView recyclerView;
    private MaterialButton btnConfirm;

    /** 统一存放当前层级的条目（Workspace / Album / Photo）。 */
    private final List<Object> items = new ArrayList<>();
    private SelectAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_select);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvBreadcrumb = findViewById(R.id.tv_breadcrumb);
        recyclerView = findViewById(R.id.rv_select);
        btnConfirm = findViewById(R.id.btn_confirm);

        adapter = new SelectAdapter();
        recyclerView.setAdapter(adapter);

        // 点击面包屑回退到上一级
        tvBreadcrumb.setOnClickListener(v -> navigateUp());

        // 确定按钮：返回选中照片路径
        btnConfirm.setOnClickListener(v -> {
            if (selectedPhotoPath != null) {
                Intent data = new Intent();
                data.putExtra(EXTRA_SELECTED_PHOTO_PATH, selectedPhotoPath);
                setResult(RESULT_OK, data);
                finish();
            }
        });
        updateConfirmButton();

        // 默认进入用户当前所在相册的照片列表
        String defaultWs = getIntent().getStringExtra(EXTRA_DEFAULT_WORKSPACE);
        String defaultAlbum = getIntent().getStringExtra(EXTRA_DEFAULT_ALBUM);
        if (defaultWs != null && !defaultWs.isEmpty()
                && defaultAlbum != null && !defaultAlbum.isEmpty()) {
            currentWorkspaceName = defaultWs;
            currentAlbumName = defaultAlbum;
            currentLevel = LEVEL_PHOTO;
        } else if (defaultWs != null && !defaultWs.isEmpty()) {
            currentWorkspaceName = defaultWs;
            currentLevel = LEVEL_ALBUM;
        } else {
            currentLevel = LEVEL_WORKSPACE;
        }
        loadCurrentLevel();
    }

    /** 根据当前层级加载对应数据并切换布局管理器。 */
    private void loadCurrentLevel() {
        items.clear();
        switch (currentLevel) {
            case LEVEL_WORKSPACE:
                items.addAll(FileUtils.getWorkspaces());
                recyclerView.setLayoutManager(new LinearLayoutManager(this));
                break;
            case LEVEL_ALBUM:
                items.addAll(FileUtils.getAlbums(currentWorkspaceName));
                recyclerView.setLayoutManager(new LinearLayoutManager(this));
                break;
            case LEVEL_PHOTO:
            default:
                items.addAll(FileUtils.getPhotos(this, currentWorkspaceName, currentAlbumName));
                recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
                break;
        }
        adapter.setCurrentLevel(currentLevel);
        adapter.notifyDataSetChanged();
        updateBreadcrumb();
    }

    /** 更新顶部路径面包屑文本。 */
    private void updateBreadcrumb() {
        String text;
        switch (currentLevel) {
            case LEVEL_ALBUM:
                text = currentWorkspaceName;
                break;
            case LEVEL_PHOTO:
                text = currentWorkspaceName + " > " + currentAlbumName;
                break;
            case LEVEL_WORKSPACE:
            default:
                text = getString(R.string.photo_select_root);
                break;
        }
        tvBreadcrumb.setText(text);
    }

    /** 回退到上一级。 */
    private void navigateUp() {
        if (currentLevel == LEVEL_PHOTO) {
            currentLevel = LEVEL_ALBUM;
            currentAlbumName = null;
            selectedPhotoPath = null;
            updateConfirmButton();
            loadCurrentLevel();
        } else if (currentLevel == LEVEL_ALBUM) {
            currentLevel = LEVEL_WORKSPACE;
            currentWorkspaceName = null;
            loadCurrentLevel();
        }
        // 在工作区层级不再回退
    }

    /** 条目点击事件：根据层级进入下一级或选中照片。 */
    private void onItemClick(int position) {
        if (position < 0 || position >= items.size()) {
            return;
        }
        Object item = items.get(position);
        switch (currentLevel) {
            case LEVEL_WORKSPACE: {
                Workspace ws = (Workspace) item;
                currentWorkspaceName = ws.getName();
                currentLevel = LEVEL_ALBUM;
                loadCurrentLevel();
                break;
            }
            case LEVEL_ALBUM: {
                Album album = (Album) item;
                currentAlbumName = album.getName();
                currentLevel = LEVEL_PHOTO;
                selectedPhotoPath = null;
                updateConfirmButton();
                loadCurrentLevel();
                break;
            }
            case LEVEL_PHOTO:
            default: {
                Photo photo = (Photo) item;
                selectedPhotoPath = photo.getAbsolutePath();
                adapter.setSelectedPath(selectedPhotoPath);
                updateConfirmButton();
                break;
            }
        }
    }

    /** 更新确定按钮的可用状态。 */
    private void updateConfirmButton() {
        btnConfirm.setEnabled(selectedPhotoPath != null);
    }

    // ==================== RecyclerView Adapter ====================

    /**
     * 同时支持文件夹条目（工作区/相册）与照片条目的适配器。
     */
    private class SelectAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_FOLDER = 0;
        private static final int TYPE_PHOTO = 1;

        private int currentLevel = LEVEL_WORKSPACE;
        private String selectedPath;

        void setCurrentLevel(int level) {
            this.currentLevel = level;
        }

        void setSelectedPath(String path) {
            this.selectedPath = path;
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return currentLevel == LEVEL_PHOTO ? TYPE_PHOTO : TYPE_FOLDER;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_PHOTO) {
                return new PhotoVH(inflater.inflate(R.layout.item_select_photo, parent, false));
            }
            return new FolderVH(inflater.inflate(R.layout.item_select_folder, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object item = items.get(position);
            if (holder instanceof FolderVH) {
                bindFolder((FolderVH) holder, item);
            } else if (holder instanceof PhotoVH) {
                bindPhoto((PhotoVH) holder, (Photo) item);
            }
            holder.itemView.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    onItemClick(pos);
                }
            });
        }

        private void bindFolder(FolderVH holder, Object item) {
            String name;
            if (item instanceof Workspace) {
                name = ((Workspace) item).getName();
            } else if (item instanceof Album) {
                name = ((Album) item).getName();
            } else {
                name = "";
            }
            holder.tvName.setText(name);
            holder.ivIcon.setImageResource(R.drawable.ic_folder);
            holder.ivArrow.setVisibility(View.VISIBLE);
        }

        private void bindPhoto(PhotoVH holder, Photo photo) {
            holder.ivPhoto.setContentDescription(photo.getName());
            Glide.with(holder.itemView.getContext())
                    .load(photo.getFile())
                    .centerCrop()
                    .into(holder.ivPhoto);

            boolean selected = photo.getAbsolutePath().equals(selectedPath);
            holder.ivCheck.setVisibility(selected ? View.VISIBLE : View.GONE);
            holder.container.setBackgroundResource(
                    selected ? R.drawable.bg_select_photo_selected
                            : R.color.colorEmptyBg);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class FolderVH extends RecyclerView.ViewHolder {
            final ImageView ivIcon;
            final TextView tvName;
            final ImageView ivArrow;

            FolderVH(@NonNull View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.iv_folder_icon);
                tvName = itemView.findViewById(R.id.tv_folder_name);
                ivArrow = itemView.findViewById(R.id.iv_arrow);
            }
        }

        class PhotoVH extends RecyclerView.ViewHolder {
            final FrameLayout container;
            final ImageView ivPhoto;
            final ImageView ivCheck;

            PhotoVH(@NonNull View itemView) {
                super(itemView);
                container = itemView.findViewById(R.id.select_photo_container);
                ivPhoto = itemView.findViewById(R.id.iv_select_photo);
                ivCheck = itemView.findViewById(R.id.iv_select_check);
            }
        }
    }
}
