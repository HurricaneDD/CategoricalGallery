package com.categorical.gallery;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.widget.CompoundButtonCompat;

import com.categorical.gallery.util.ThemeHelper;

public class SettingsActivity extends AppCompatActivity {

    private View[] colorViews;
    private int selectedColorIndex = -1;

    private final int[] colorIds = {
            R.id.colorBlue,
            R.id.colorPurple,
            R.id.colorGreen,
            R.id.colorRed,
            R.id.colorBW
    };

    private final int[] colorValues = {
            ThemeHelper.COLOR_BLUE,
            ThemeHelper.COLOR_PURPLE,
            ThemeHelper.COLOR_GREEN,
            ThemeHelper.COLOR_RED,
            ThemeHelper.COLOR_BLACKWHITE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        ThemeHelper.applyToolbar(this, toolbar);
        toolbar.setTitle("设置");
        toolbar.setNavigationOnClickListener(v -> finish());

        colorViews = new View[colorIds.length];
        for (int i = 0; i < colorIds.length; i++) {
            View view = findViewById(colorIds[i]);
            colorViews[i] = view;
            int color = ThemeHelper.getPrimaryColor(this);
            int themeColor = getThemeColorValue(i);
            setColorViewBackground(view, themeColor);
            final int index = i;
            view.setOnClickListener(v -> {
                ThemeHelper.setThemeColor(this, colorValues[index]);
                recreate();
            });
        }

        updateColorSelection();

        TextView tvThemeTitle = findViewById(R.id.tvThemeTitle);
        TextView tvDarkModeTitle = findViewById(R.id.tvDarkModeTitle);
        RadioGroup rgDarkMode = findViewById(R.id.rgDarkMode);
        RadioButton rbFollowSystem = findViewById(R.id.rbFollowSystem);
        RadioButton rbLight = findViewById(R.id.rbLight);
        RadioButton rbDark = findViewById(R.id.rbDark);

        int textPrimary = ContextCompat.getColor(this, R.color.colorTextPrimary);
        if (tvThemeTitle != null) tvThemeTitle.setTextColor(textPrimary);
        if (tvDarkModeTitle != null) tvDarkModeTitle.setTextColor(textPrimary);
        rbFollowSystem.setTextColor(textPrimary);
        rbLight.setTextColor(textPrimary);
        rbDark.setTextColor(textPrimary);

        int primaryColor = ThemeHelper.getPrimaryColor(this);
        ColorStateList buttonTint = new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{-android.R.attr.state_checked}
                },
                new int[]{
                        primaryColor,
                        ContextCompat.getColor(this, R.color.colorTextSecondary)
                }
        );
        CompoundButtonCompat.setButtonTintList(rbFollowSystem, buttonTint);
        CompoundButtonCompat.setButtonTintList(rbLight, buttonTint);
        CompoundButtonCompat.setButtonTintList(rbDark, buttonTint);

        int darkMode = ThemeHelper.getDarkMode(this);
        switch (darkMode) {
            case ThemeHelper.MODE_LIGHT:
                rbLight.setChecked(true);
                break;
            case ThemeHelper.MODE_DARK:
                rbDark.setChecked(true);
                break;
            default:
                rbFollowSystem.setChecked(true);
                break;
        }

        rgDarkMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbFollowSystem) {
                ThemeHelper.setDarkMode(this, ThemeHelper.MODE_FOLLOW_SYSTEM);
            } else if (checkedId == R.id.rbLight) {
                ThemeHelper.setDarkMode(this, ThemeHelper.MODE_LIGHT);
            } else if (checkedId == R.id.rbDark) {
                ThemeHelper.setDarkMode(this, ThemeHelper.MODE_DARK);
            }
            recreate();
        });
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int getThemeColorValue(int index) {
        return ContextCompat.getColor(this, getThemeColorRes(index));
    }

    private int getThemeColorRes(int index) {
        switch (index) {
            case 1: return R.color.themePurplePrimary;
            case 2: return R.color.themeGreenPrimary;
            case 3: return R.color.themeRedPrimary;
            case 4: return R.color.themeBWPrimary;
            default: return R.color.themeBluePrimary;
        }
    }

    private void setColorViewBackground(View view, int color) {
        GradientDrawable drawable = (GradientDrawable) view.getBackground().mutate();
        drawable.setColor(color);
    }

    private void updateColorSelection() {
        int currentColor = ThemeHelper.getThemeColor(this);
        for (int i = 0; i < colorViews.length; i++) {
            GradientDrawable drawable = (GradientDrawable) colorViews[i].getBackground().mutate();
            if (colorValues[i] == currentColor) {
                selectedColorIndex = i;
                colorViews[i].setAlpha(1.0f);
                colorViews[i].setScaleX(1.0f);
                colorViews[i].setScaleY(1.0f);
                drawable.setStroke(dpToPx(4), ContextCompat.getColor(this, R.color.colorTextPrimary));
            } else {
                colorViews[i].setAlpha(0.6f);
                colorViews[i].setScaleX(1.0f);
                colorViews[i].setScaleY(1.0f);
                drawable.setStroke(dpToPx(1), ContextCompat.getColor(this, R.color.colorDivider));
            }
        }
    }
}
