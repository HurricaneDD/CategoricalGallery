package com.categorical.gallery;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

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

        RadioGroup rgDarkMode = findViewById(R.id.rgDarkMode);
        RadioButton rbFollowSystem = findViewById(R.id.rbFollowSystem);
        RadioButton rbLight = findViewById(R.id.rbLight);
        RadioButton rbDark = findViewById(R.id.rbDark);

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
        });
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
        android.graphics.drawable.GradientDrawable drawable =
                (android.graphics.drawable.GradientDrawable) view.getBackground();
        drawable.setColor(color);
    }

    private void updateColorSelection() {
        int currentColor = ThemeHelper.getThemeColor(this);
        for (int i = 0; i < colorViews.length; i++) {
            if (colorValues[i] == currentColor) {
                selectedColorIndex = i;
                colorViews[i].setAlpha(1.0f);
                colorViews[i].setScaleX(1.2f);
                colorViews[i].setScaleY(1.2f);
            } else {
                colorViews[i].setAlpha(0.5f);
                colorViews[i].setScaleX(1.0f);
                colorViews[i].setScaleY(1.0f);
            }
        }
    }
}
