package com.a4455jkjh.apktool.fragment;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;
import com.a4455jkjh.apktool.ApktoolActivity;
import com.a4455jkjh.apktool.ApktoolPermissions;
import com.a4455jkjh.apktool.R;
import com.a4455jkjh.apktool.fragment.files.ErrorTree;
import com.a4455jkjh.apktool.fragment.files.FilesPagerAdapter;
import java.io.File;

public class FilesFragment extends Fragment {
	private EditorFragment editor;
	private ViewPager files_pager;
	private FilesPagerAdapter adapter;
	private boolean initialized;

	public void setPage(int idx) {
		files_pager.setCurrentItem(idx);
	}

	public void setRoot(ErrorTree errors) {
		editor.setRoot(errors);
	}
	public void bind(EditorFragment editor) {
		this.editor = editor;
	}

	public void focus(){
		files_pager.requestFocus();
	}
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.files_pager, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		files_pager = view.findViewById(R.id.files_pager);
		TabLayout tab = view.findViewById(R.id.tab);
		tab.setupWithViewPager(files_pager, true);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		adapter = new FilesPagerAdapter(getActivity());
		files_pager.setAdapter(adapter);
		if (ApktoolPermissions.hasFileAccess(getActivity()))
			init(savedInstanceState);
		else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			ApktoolPermissions.openAllFilesAccessSettings(getActivity());
		} else
			requestPermissions(new String[]{
								   Manifest.permission.WRITE_EXTERNAL_STORAGE},
							   10);
	}

	@Override
	public void onResume() {
		super.onResume();
		if (adapter != null && !initialized
				&& ApktoolPermissions.hasFileAccess(getActivity())) {
			initialized = true;
			init(null);
		} else if (adapter != null && initialized) {
			adapter.refreshApplications();
		}
	}
	private void init(Bundle savedInstanceState) {
		if (initialized) {
			return;
		}
		initialized = true;
		adapter.init(savedInstanceState, this);
		editor.init();
		((ApktoolActivity)getActivity()).init();
	}
	public void edit(File file) {
		editor.open(file);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		adapter.save(outState);
	}
	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode == 10 && grantResults[0] == 0) {
			initialized = false;
			init(null);
			return;
		}
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}
}
