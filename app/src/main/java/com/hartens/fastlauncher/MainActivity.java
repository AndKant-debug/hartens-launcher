package com.hartens.fastlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Минималистичный лаунчер: без рекламы, рекомендаций и фоновых сервисов.
 * Верхний ряд — избранное (Wink, VK Видео, YouTube, VPN-клиент закрепляются при первом запуске),
 * ниже — сетка всех приложений. Долгое нажатие OK на приложении — меню.
 */
public class MainActivity extends Activity {

    private static class App {
        String pkg;
        String label;
        Drawable icon;
        Intent intent;
    }

    // Подстроки названий, которые закрепляются автоматически при первом запуске
    private static final String[] AUTO_PIN = {
            "youtube", "smarttube", "zetflix", "lampa", "wink",
            "vk видео", "vk video", "вк видео", "twitch", "happ"
    };
    // Пакеты VPN-клиентов для кнопки «VPN»
    private static final String[] VPN_PACKAGES = {
            "com.happproxy", "su.happ.proxyutility", "su.happ.proxyutility.tv",
            "com.v2ray.ang", "com.v2raytun.android", "app.hiddify.com"
    };

    private static final String PREFS = "launcher";
    private static final String KEY_FAVS = "favs";
    private static final String KEY_HIDDEN = "hidden";
    private static final String KEY_INIT = "init3";

    private static final String[] THEME_NAMES = {"Графит", "Океан", "Закат", "Аврора", "Свой фон"};
    private static final int[][] THEME_COLORS = {
            {0xFF0F1115, 0xFF1B2230},
            {0xFF06141F, 0xFF0B3C5D},
            {0xFF1A0B2E, 0xFF4A1A5E, 0xFF8A2F4F},
            {0xFF04140F, 0xFF0E5A4B, 0xFF1B2A6B}
    };
    private LinearLayout rootView;

    private SharedPreferences prefs;
    private final List<App> allApps = new ArrayList<>();
    private final List<App> gridApps = new ArrayList<>();
    private LinearLayout favRow;
    private GridView grid;
    private GridAdapter adapter;
    private boolean showHidden = false;
    private float density;
    private volatile boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        density = getResources().getDisplayMetrics().density;
        getWindow().getDecorView().setBackgroundColor(Color.parseColor("#0F1115"));
        buildUi();
        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadApps(); // подхватываем установленные/удалённые приложения
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (grid != null) grid.setSelection(0);
        if (favRow != null && favRow.getChildCount() > 0) favRow.getChildAt(0).requestFocus();
    }

    @Override
    public void onBackPressed() {
        // Мы домашний экран — назад ничего не делает
    }

    // ---------- UI ----------

    private int dp(int v) {
        return Math.round(v * density);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(48), dp(24), dp(48), dp(16));
        rootView = root;

        // верхняя панель: часы + кнопки
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTextColor(Color.WHITE);
        time.setTextSize(34);
        top.addView(time);

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d MMMM");
        date.setFormat12Hour("EEEE, d MMMM");
        date.setTextColor(Color.parseColor("#9AA4B2"));
        date.setTextSize(16);
        date.setPadding(dp(16), dp(10), 0, 0);
        top.addView(date);

        View spacer = new View(this);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        top.addView(makeButton("VPN", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openVpnClient();
            }
        }));
        top.addView(makeButton("Настройки", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingsMenu();
            }
        }));
        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // избранное
        HorizontalScrollView favScroll = new HorizontalScrollView(this);
        favScroll.setHorizontalScrollBarEnabled(false);
        favScroll.setClipToPadding(false);
        favScroll.setPadding(0, dp(16), 0, dp(8));
        favRow = new LinearLayout(this);
        favRow.setOrientation(LinearLayout.HORIZONTAL);
        favScroll.addView(favRow);
        root.addView(favScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // все приложения
        TextView title = new TextView(this);
        title.setText("Все приложения");
        title.setTextColor(Color.parseColor("#9AA4B2"));
        title.setTextSize(16);
        title.setPadding(dp(8), dp(8), 0, dp(8));
        root.addView(title);

        grid = new GridView(this);
        grid.setNumColumns(6);
        grid.setVerticalSpacing(dp(8));
        grid.setHorizontalSpacing(dp(8));
        grid.setSelector(focusDrawable());
        grid.setClipToPadding(false);
        grid.setVerticalScrollBarEnabled(false);
        adapter = new GridAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                launch(gridApps.get(position));
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                showAppMenu(gridApps.get(position));
                return true;
            }
        });
        root.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        applyTheme();
    }

    private Drawable focusDrawable() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor("#33FFFFFF"));
        d.setCornerRadius(dp(12));
        d.setStroke(dp(3), Color.parseColor("#4FC3F7"));
        return d;
    }

    private Drawable focusStateDrawable() {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focusDrawable());
        s.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        return s;
    }

    private TextView makeButton(String text, View.OnClickListener l) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(20), dp(10), dp(20), dp(10));
        b.setFocusable(true);
        b.setClickable(true);
        b.setBackground(focusStateDrawable());
        b.setOnClickListener(l);
        return b;
    }

    private View makeTile(final App a, int iconDp, int widthDp) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(dp(8), dp(10), dp(8), dp(8));

        ImageView iv = new ImageView(this);
        iv.setImageDrawable(a.icon);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        t.addView(iv, new LinearLayout.LayoutParams(dp(iconDp), dp(iconDp)));

        TextView tv = new TextView(this);
        tv.setText(a.label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(14);
        tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp(6), 0, 0);
        t.addView(tv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (widthDp > 0) {
            t.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return t;
    }

    private class GridAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return gridApps.size();
        }

        @Override
        public Object getItem(int position) {
            return gridApps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            App a = gridApps.get(position);
            if (convertView == null) {
                convertView = makeTile(a, 64, 0);
            } else {
                LinearLayout t = (LinearLayout) convertView;
                ((ImageView) t.getChildAt(0)).setImageDrawable(a.icon);
                ((TextView) t.getChildAt(1)).setText(a.label);
            }
            return convertView;
        }
    }

    // ---------- фон ----------

    private File customBg() {
        return new File(getExternalFilesDir(null), "background.jpg");
    }

    private void applyTheme() {
        int t = prefs.getInt("theme", 1);
        Drawable bg = null;
        if (t == 4 && customBg().exists()) {
            try {
                String path = customBg().getAbsolutePath();
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, o);
                int sample = 1;
                while (o.outWidth / (sample * 2) >= 1920) sample *= 2;
                o = new BitmapFactory.Options();
                o.inSampleSize = sample;
                Bitmap b = BitmapFactory.decodeFile(path, o);
                if (b != null) {
                    bg = new LayerDrawable(new Drawable[]{
                            new BitmapDrawable(getResources(), b),
                            new ColorDrawable(0x40000000)});
                }
            } catch (Throwable ignored) {
            }
        }
        if (bg == null) {
            if (t > 3 || t < 0) t = 1;
            bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, THEME_COLORS[t]);
        }
        rootView.setBackground(bg);
    }

    private void nextTheme() {
        int t = prefs.getInt("theme", 1) + 1;
        if (t == 4 && !customBg().exists()) t = 0;
        if (t > 4) t = 0;
        prefs.edit().putInt("theme", t).apply();
        applyTheme();
    }

    // ---------- данные ----------

    private void loadApps() {
        if (loading) return;
        loading = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<App> res = queryApps();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        allApps.clear();
                        allApps.addAll(res);
                        autoPinOnce();
                        refreshViews();
                        loading = false;
                    }
                });
            }
        }, "load-apps").start();
    }

    private List<App> queryApps() {
        PackageManager pm = getPackageManager();
        Set<String> seen = new HashSet<>();
        List<App> res = new ArrayList<>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                App a = new App();
                a.pkg = pkg;
                a.label = String.valueOf(ri.loadLabel(pm));
                a.icon = ri.loadIcon(pm);
                a.intent = new Intent(Intent.ACTION_MAIN).addCategory(cat)
                        .setClassName(pkg, ri.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                res.add(a);
            }
        }
        Collections.sort(res, new Comparator<App>() {
            @Override
            public int compare(App x, App y) {
                return x.label.toLowerCase(Locale.getDefault()).compareTo(y.label.toLowerCase(Locale.getDefault()));
            }
        });
        return res;
    }

    private List<String> getFavs() {
        List<String> list = new ArrayList<>();
        String s = prefs.getString(KEY_FAVS, "");
        if (s.length() > 0) Collections.addAll(list, s.split(","));
        return list;
    }

    private void saveFavs(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String p : list) {
            if (sb.length() > 0) sb.append(',');
            sb.append(p);
        }
        prefs.edit().putString(KEY_FAVS, sb.toString()).apply();
    }

    private Set<String> getHidden() {
        return new HashSet<>(prefs.getStringSet(KEY_HIDDEN, new HashSet<String>()));
    }

    private void autoPinOnce() {
        if (allApps.isEmpty()) return;
        List<String> favs = getFavs();
        Set<String> done = new HashSet<>(prefs.getStringSet("autopinned", new HashSet<String>()));
        for (String key : AUTO_PIN) {
            for (App a : allApps) {
                if (a.label.toLowerCase(Locale.getDefault()).contains(key) && !done.contains(a.pkg)) {
                    if (!favs.contains(a.pkg)) favs.add(a.pkg);
                    done.add(a.pkg);
                }
            }
        }
        saveFavs(favs);
        prefs.edit().putStringSet("autopinned", done).apply();
    }

    private App find(String pkg) {
        for (App a : allApps) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    private void refreshViews() {
        Set<String> hidden = getHidden();
        List<String> favs = getFavs();

        gridApps.clear();
        for (App a : allApps) {
            if (showHidden || !hidden.contains(a.pkg)) gridApps.add(a);
        }
        adapter.notifyDataSetChanged();

        favRow.removeAllViews();
        for (String pkg : favs) {
            final App a = find(pkg);
            if (a == null) continue;
            View t = makeTile(a, 88, 132);
            t.setFocusable(true);
            t.setClickable(true);
            t.setBackground(focusStateDrawable());
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    launch(a);
                }
            });
            t.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showAppMenu(a);
                    return true;
                }
            });
            favRow.addView(t);
        }
        if (favRow.getChildCount() == 0) {
            TextView hint = new TextView(this);
            hint.setText("Избранное пусто: удерживайте OK на приложении и выберите «В избранное»");
            hint.setTextColor(Color.parseColor("#9AA4B2"));
            hint.setTextSize(14);
            hint.setPadding(dp(8), dp(24), 0, dp(24));
            favRow.addView(hint);
        }
    }

    // ---------- действия ----------

    private void launch(App a) {
        try {
            startActivity(a.intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Не удалось запустить: " + a.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void openVpnClient() {
        for (String pkg : VPN_PACKAGES) {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(i);
                    return;
                } catch (Exception ignored) {
                }
            }
        }
        for (App a : allApps) {
            if (a.label.toLowerCase(Locale.getDefault()).contains("happ")) {
                launch(a);
                return;
            }
        }
        // клиент не найден — открываем системные настройки VPN
        Toast.makeText(this, "VPN-клиент не найден (установите Happ). Открываю настройки VPN.", Toast.LENGTH_LONG).show();
        openSettingsAction(Settings.ACTION_VPN_SETTINGS);
    }

    private void openSettingsAction(String action) {
        try {
            startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e2) {
                Toast.makeText(this, "Настройки недоступны", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showAppMenu(final App a) {
        final List<String> favs = getFavs();
        final boolean isFav = favs.contains(a.pkg);
        final Set<String> hidden = getHidden();
        final boolean isHidden = hidden.contains(a.pkg);

        String[] items = {
                isFav ? "Убрать из избранного" : "В избранное",
                isHidden ? "Показать в списке" : "Скрыть из списка",
                "О приложении",
                "Удалить"
        };
        new AlertDialog.Builder(this)
                .setTitle(a.label)
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            if (isFav) favs.remove(a.pkg);
                            else favs.add(a.pkg);
                            saveFavs(favs);
                            break;
                        case 1:
                            if (isHidden) hidden.remove(a.pkg);
                            else hidden.add(a.pkg);
                            prefs.edit().putStringSet(KEY_HIDDEN, hidden).apply();
                            break;
                        case 2:
                            try {
                                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:" + a.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            } catch (Exception ignored) {
                            }
                            break;
                        case 3:
                            try {
                                startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            } catch (Exception ignored) {
                            }
                            break;
                    }
                    refreshViews();
                })
                .show();
    }

    private void showSettingsMenu() {
        String[] items = {
                "Настройки Android",
                "Сеть / Wi‑Fi",
                "Системные настройки VPN",
                showHidden ? "Не показывать скрытые приложения" : "Показать скрытые приложения",
                "Сбросить избранное",
                "Скрыть всё, кроме избранного",
                "Сменить фон (сейчас: " + THEME_NAMES[Math.min(4, prefs.getInt("theme", 1))] + ")"
        };
        new AlertDialog.Builder(this)
                .setTitle("Меню")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            openSettingsAction(Settings.ACTION_SETTINGS);
                            break;
                        case 1:
                            openSettingsAction(Settings.ACTION_WIFI_SETTINGS);
                            break;
                        case 2:
                            openSettingsAction(Settings.ACTION_VPN_SETTINGS);
                            break;
                        case 3:
                            showHidden = !showHidden;
                            refreshViews();
                            break;
                        case 4:
                            prefs.edit().remove("autopinned").putString(KEY_FAVS, "").apply();
                            autoPinOnce();
                            refreshViews();
                            break;
                        case 5:
                            List<String> keep = getFavs();
                            Set<String> hide = getHidden();
                            for (App x : allApps) {
                                if (!keep.contains(x.pkg)) hide.add(x.pkg);
                            }
                            prefs.edit().putStringSet(KEY_HIDDEN, hide).apply();
                            refreshViews();
                            Toast.makeText(MainActivity.this,
                                    "Скрыто. Вернуть: Меню → Показать скрытые приложения",
                                    Toast.LENGTH_LONG).show();
                            break;
                        case 6:
                            nextTheme();
                            break;
                    }
                })
                .show();
    }
}
