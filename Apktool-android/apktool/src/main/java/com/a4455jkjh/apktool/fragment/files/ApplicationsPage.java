package com.a4455jkjh.apktool.fragment.files;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.view.inputmethod.InputMethodManager;
import com.a4455jkjh.apktool.MainActivity;
import com.a4455jkjh.apktool.R;
import com.a4455jkjh.apktool.fragment.FilesFragment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

/**
 * Full-screen installed-app drawer used by Apktool.
 *
 * The old implementation rendered a hierarchical TreeView inside the narrow
 * Apktool drawer. This page keeps the existing app actions, but presents each
 * package as an icon card in a responsive grid.
 */
public class ApplicationsPage {
    private final View view;
    private final Context context;
    private final PackageManager packageManager;
    private final CharSequence title;
    private final LinearLayout appContent;
    private final EditText search;
    private final Spinner sort;
    private final boolean fullPage;

    public ApplicationsPage(Context context) {
        this(context, false);
    }

    public ApplicationsPage(Context context, boolean fullPage) {
        this.context = context;
        this.fullPage = fullPage;
        packageManager = context.getPackageManager();
        LayoutInflater inflater = LayoutInflater.from(context);
        view = inflater.inflate(R.layout.applications, null);
        title = context.getText(R.string.apps);
        appContent = view.findViewById(R.id.apps_content);
        search = view.findViewById(R.id.apps_search);
        sort = view.findViewById(R.id.apps_sort);

        sort.setAdapter(new ArrayAdapter<String>(context,
                android.R.layout.simple_spinner_dropdown_item,
                new String[] {context.getString(R.string.apps_sort_name),
                        context.getString(R.string.apps_sort_recent)}));
        View.OnClickListener refreshListener = new View.OnClickListener() {
            @Override
            public void onClick(View clicked) {
                refreshApps();
                search.clearFocus();
                InputMethodManager inputMethodManager = (InputMethodManager)
                        context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (inputMethodManager != null) {
                    inputMethodManager.hideSoftInputFromWindow(search.getWindowToken(), 0);
                }
            }
        };
        view.findViewById(R.id.apps_search_button).setOnClickListener(refreshListener);
        sort.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View selected,
                                       int position, long id) {
                refreshApps();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        Button exit = view.findViewById(R.id.apps_master_home);
        if (fullPage) {
            exit.setText(R.string.return_apktool);
            exit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View clicked) {
                    if (context instanceof Activity) {
                        ((Activity) context).finish();
                    }
                }
            });
        } else {
            exit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View clicked) {
                    Intent home = new Intent(context, MainActivity.class);
                    home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    context.startActivity(home);
                }
            });
        }
    }

    public void init(FilesFragment ignored) {
        refreshApps();
    }

    public View getView() {
        return view;
    }

    public CharSequence getTitle() {
        return title;
    }

    public void refresh() {
        refreshApps();
    }

    private void refreshApps() {
        appContent.removeAllViews();
        String normalizedQuery = search.getText().toString().trim().toLowerCase(Locale.US);
        boolean recentFirst = sort.getSelectedItemPosition() == 1;
        ArrayList<PackageInfo> packages = new ArrayList<PackageInfo>(
                packageManager.getInstalledPackages(0));
        Collections.sort(packages, new Comparator<PackageInfo>() {
            @Override
            public int compare(PackageInfo left, PackageInfo right) {
                if (recentFirst) {
                    int byTime = Long.compare(right.firstInstallTime, left.firstInstallTime);
                    if (byTime != 0) {
                        return byTime;
                    }
                }
                String leftName = left.applicationInfo.loadLabel(packageManager).toString();
                String rightName = right.applicationInfo.loadLabel(packageManager).toString();
                return leftName.toLowerCase(Locale.US)
                        .compareTo(rightName.toLowerCase(Locale.US));
            }
        });

        ArrayList<PackageInfo> installed = new ArrayList<PackageInfo>();
        ArrayList<PackageInfo> system = new ArrayList<PackageInfo>();
        for (PackageInfo pkg : packages) {
            String label = pkg.applicationInfo.loadLabel(packageManager).toString();
            String searchable = (label + " " + pkg.packageName).toLowerCase(Locale.US);
            if (normalizedQuery.length() > 0 && !searchable.contains(normalizedQuery)) {
                continue;
            }
            String sourceDir = pkg.applicationInfo == null
                    ? "" : pkg.applicationInfo.sourceDir;
            if (sourceDir != null && sourceDir.startsWith("/data/")) {
                installed.add(pkg);
            } else {
                system.add(pkg);
            }
        }

        addSection(R.string.apps_installed, installed);
        addSection(R.string.apps_system, system);
    }

    private void addSection(int headingText, ArrayList<PackageInfo> packages) {
        if (packages.isEmpty()) {
            return;
        }
        TextView heading = new TextView(context);
        heading.setText(headingText);
        heading.setTextSize(18);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setTextColor(resolveColor(android.R.attr.textColorPrimary, Color.WHITE));
        heading.setPadding(dp(8), dp(14), dp(8), dp(8));
        appContent.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        GridLayout grid = new GridLayout(context);
        int columns = Math.max(2, context.getResources().getDisplayMetrics().widthPixels / dp(96));
        grid.setColumnCount(columns);
        grid.setUseDefaultMargins(false);
        for (PackageInfo pkg : packages) {
            grid.addView(createAppCard(pkg, headingText == R.string.apps_system), cardParams(columns));
        }
        appContent.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private GridLayout.LayoutParams cardParams(int columns) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f));
        params.width = 0;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.setMargins(dp(4), dp(4), dp(4), dp(8));
        return params;
    }

    private View createAppCard(final PackageInfo pkg, final boolean systemApp) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(6), dp(10), dp(6), dp(10));
        card.setBackgroundResource(R.drawable.master_button_secondary);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View clicked) {
                ApplicationItem.showActions(clicked, pkg, packageManager, systemApp);
            }
        });

        ImageView icon = new ImageView(context);
        icon.setImageDrawable(pkg.applicationInfo.loadIcon(packageManager));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        card.addView(icon, new LinearLayout.LayoutParams(dp(58), dp(58)));

        TextView label = new TextView(context);
        label.setText(pkg.applicationInfo.loadLabel(packageManager));
        label.setTextSize(12);
        label.setTextColor(resolveColor(android.R.attr.textColorPrimary, Color.WHITE));
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private int resolveColor(int attribute, int fallback) {
        android.util.TypedValue value = new android.util.TypedValue();
        if (context.getTheme().resolveAttribute(attribute, value, true)) {
            if (value.resourceId != 0) {
                return context.getResources().getColor(value.resourceId);
            }
            return value.data;
        }
        return fallback;
    }
}