package com.categorical.gallery;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.categorical.gallery.model.Photo;
import com.categorical.gallery.util.FileUtils;
import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.material.appbar.MaterialToolbar;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 多图同屏展示页面。
 * <p>
 * 上下两个 PhotoView 各占 50% 高度，均支持独立的双指缩放（PhotoView 自带）。
 * 使用一个 ViewPager2，每个 page 包含上下两个 PhotoView：
 * - 上方 PhotoView 显示当前 position 对应的相册照片，左右滑动可切换相册中的其他照片；
 * - 下方 PhotoView 显示从 Intent 传入的第二张图片（固定不变）。
 * 由于每个 page 的下方图片相同，滑动时下方面板视觉上保持不变。
 */
public class MultiImageActivity extends AppCompatActivity {

    public static final String EXTRA_PHOTO1_PATH = "photo1Path";
    public static final String EXTRA_PHOTO2_PATH = "photo2Path";
    public static final String EXTRA_WORKSPACE_NAME = "workspaceName";
    public static final String EXTRA_ALBUM_NAME = "albumName";
    public static final String EXTRA_POSITION = "position";

    private List<Photo> photos = new ArrayList<>();
    private String photo2Path;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_image);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.multi_image_title);
        toolbar.setNavigationOnClickListener(v -> finish());

        ViewPager2 viewPager = findViewById(R.id.vp_multi_image);

        String workspaceName = getIntent().getStringExtra(EXTRA_WORKSPACE_NAME);
        String albumName = getIntent().getStringExtra(EXTRA_ALBUM_NAME);
        photo2Path = getIntent().getStringExtra(EXTRA_PHOTO2_PATH);
        int position = getIntent().getIntExtra(EXTRA_POSITION, 0);

        // 优先使用相册照片列表作为上方图片的数据源
        if (workspaceName != null && albumName != null) {
            List<Photo> list = FileUtils.getPhotos(this, workspaceName, albumName);
            if (list != null) {
                photos = list;
            }
        }

        // 回退：若没有相册信息，则用 photo1Path 构造单页
        if (photos.isEmpty()) {
            String photo1Path = getIntent().getStringExtra(EXTRA_PHOTO1_PATH);
            if (photo1Path != null && !photo1Path.isEmpty()) {
                photos = new ArrayList<>();
                photos.add(new Photo(new File(photo1Path)));
            }
        }

        if (photos.isEmpty()) {
            finish();
            return;
        }

        viewPager.setAdapter(new PageAdapter(photos, photo2Path));
        viewPager.setOffscreenPageLimit(1);

        if (position < 0) {
            position = 0;
        }
        if (position >= photos.size()) {
            position = photos.size() - 1;
        }
        viewPager.setCurrentItem(position, false);
    }

    // ==================== ViewPager2 Adapter ====================

    private static class PageAdapter extends RecyclerView.Adapter<PageAdapter.PageVH> {

        private final List<Photo> photos;
        private final String photo2Path;

        PageAdapter(List<Photo> photos, String photo2Path) {
            this.photos = photos;
            this.photo2Path = photo2Path;
        }

        @NonNull
        @Override
        public PageVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_multi_image_page, parent, false);
            return new PageVH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PageVH holder, int position) {
            Photo photo = photos.get(position);

            // 上方图片：当前 position 的相册照片
            holder.photoTop.setScale(1f, false);
            Glide.with(holder.itemView.getContext())
                    .load(photo.getFile())
                    .fitCenter()
                    .into(holder.photoTop);

            // 下方图片：固定的第二张图片
            if (photo2Path != null && !photo2Path.isEmpty()) {
                holder.divider.setVisibility(View.VISIBLE);
                holder.photoBottom.setVisibility(View.VISIBLE);
                holder.photoBottom.setScale(1f, false);
                Glide.with(holder.itemView.getContext())
                        .load(photo2Path)
                        .fitCenter()
                        .into(holder.photoBottom);
            } else {
                holder.divider.setVisibility(View.GONE);
                holder.photoBottom.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return photos.size();
        }

        static class PageVH extends RecyclerView.ViewHolder {
            final PhotoView photoTop;
            final PhotoView photoBottom;
            final View divider;

            PageVH(@NonNull View itemView) {
                super(itemView);
                photoTop = itemView.findViewById(R.id.photo_top);
                photoBottom = itemView.findViewById(R.id.photo_bottom);
                divider = itemView.findViewById(R.id.divider);
            }
        }
    }
}
