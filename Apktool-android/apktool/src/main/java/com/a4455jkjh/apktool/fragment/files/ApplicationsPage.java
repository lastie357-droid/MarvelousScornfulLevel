package com.a4455jkjh.apktool.fragment.files;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import com.a4455jkjh.apktool.R;
import com.a4455jkjh.apktool.MainActivity;
import com.a4455jkjh.apktool.fragment.FilesFragment;
import com.a4455jkjh.apktool.view.TreeView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

public class ApplicationsPage {
	private final View view;
	private final Context context;
	private final PackageManager mPackageManager;
	private final CharSequence title;
	private TreeView<ErrorTree> apps;
	private ErrorsAdapter adapter;
	private final ApplicationItem installedApps;
	private final ApplicationItem systemApps;// = 
	public ApplicationsPage(Context context) {
		this.context = context;
		mPackageManager = context.getPackageManager();
		LayoutInflater inflater = LayoutInflater.from(context);
		view = inflater.inflate(
			R.layout.applications, null);
		title = context.getText(R.string.apps);
		apps = view.findViewById(R.id.apps_tree);
		installedApps = new ApplicationItem(context.getText(R.string.apps_installed), false);
		systemApps = new ApplicationItem(context.getText(R.string.apps_system), true);
		final EditText search = view.findViewById(R.id.apps_search);
		Button searchButton = view.findViewById(R.id.apps_search_button);
		Spinner sort = view.findViewById(R.id.apps_sort);
		sort.setAdapter(new ArrayAdapter<String>(context,
				android.R.layout.simple_spinner_dropdown_item,
				new String[] {context.getString(R.string.apps_sort_name),
						context.getString(R.string.apps_sort_recent)}));
		searchButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View clicked) {
				refreshApps(search.getText().toString(), false);
				search.clearFocus();
				((InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE))
						.hideSoftInputFromWindow(search.getWindowToken(), 0);
			}
		});
		sort.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(android.widget.AdapterView<?> parent, View selected,
									   int position, long id) {
				refreshApps(search.getText().toString(), position == 1);
			}
			@Override
			public void onNothingSelected(android.widget.AdapterView<?> parent) {
			}
		});
		view.findViewById(R.id.apps_master_home).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View clicked) {
				Intent home = new Intent(context, MainActivity.class);
				home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
				context.startActivity(home);
			}
		});
	}
	public void init(FilesFragment frag) {
		adapter = new ErrorsAdapter(){
			@Override
			public boolean shouldShowExpandDrawable() {
				return true;
			}
			
			@Override
				public void reset() {
				childs.clear();
					refreshApps("", false);
				refresh();
			}
		};
		adapter.reset();
		adapter.addChild(installedApps);
		adapter.addChild(systemApps);
		apps.setAdapter(adapter);
	}
	public View getView() {
		return view;
	}

	public CharSequence getTitle() {
		return title;
	}
	public void refresh() {
		refreshApps("", false);
	}
	protected void refreshApps() {
		refreshApps("", false);
	}

	private void refreshApps(String query, boolean recentFirst) {
		ApplicationItem installedApps = this.installedApps;
		ApplicationItem systemApps = this.systemApps;
		installedApps.reset();
		systemApps.reset();
		PackageManager mPackageManager = this.mPackageManager;
		String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.US);
		ArrayList<PackageInfo> packages = new ArrayList<PackageInfo>(
				mPackageManager.getInstalledPackages(0));
		Collections.sort(packages, new Comparator<PackageInfo>() {
			@Override
			public int compare(PackageInfo left, PackageInfo right) {
				if (recentFirst) {
					int byTime = Long.compare(right.firstInstallTime, left.firstInstallTime);
					if (byTime != 0) {
						return byTime;
					}
				}
				String leftName = left.applicationInfo.loadLabel(mPackageManager).toString();
				String rightName = right.applicationInfo.loadLabel(mPackageManager).toString();
				return leftName.toLowerCase(Locale.US)
						.compareTo(rightName.toLowerCase(Locale.US));
			}
		});
		for (PackageInfo pkg: packages) {
			String label = pkg.applicationInfo.loadLabel(mPackageManager).toString();
			String searchable = (label + " " + pkg.packageName).toLowerCase(Locale.US);
			if (normalizedQuery.length() > 0 && !searchable.contains(normalizedQuery)) {
				continue;
			}
			if (pkg.applicationInfo.sourceDir.startsWith("/data/"))
				installedApps.addApp(pkg, mPackageManager);
			else
				systemApps.addApp(pkg, mPackageManager);
		}
		if (!recentFirst) {
			installedApps.sort();
			systemApps.sort();
		}
		if (adapter != null) {
			adapter.refresh();
		}
	}
}
