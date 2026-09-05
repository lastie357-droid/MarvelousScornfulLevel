package com.a4455jkjh.apktool.fragment.files;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;
import com.a4455jkjh.apktool.R;
import com.a4455jkjh.apktool.task.DecodeTask;
import com.a4455jkjh.apktool.task.ImportFrameworkTask;
import com.a4455jkjh.apktool.util.PopupUtils;
import java.io.File;

public class ApplicationItem extends ErrorTree {
	private final boolean isSystem;
	public ApplicationItem(CharSequence msg, boolean isSystem) {
		super(msg);
		this.isSystem = isSystem;
	}

	@Override
	public void setIcon(ImageView icon) {
		FileItem.Icon.APK.set(icon);
	}
	public void addApp(final PackageInfo pkg, final PackageManager pm) {
		addChild(new ErrorTree(String.format("%s_%s", pkg.applicationInfo.loadLabel(pm), pkg.versionName)){
				@Override
				public void setIcon(ImageView icon) {
					icon.setImageDrawable(
						pkg.applicationInfo.loadIcon(pm));
				}
				@Override
				public void click(View view) {
					PopupUtils.show(view, R.menu.app,
						new PopupUtils.Callback(){
							@Override
							public void call(Context ctx, int id) {
								File file = new File(pkg.applicationInfo.sourceDir);
								switch (id) {
									case R.id.details:
										showAppDetails(ctx, pkg, pm);
										break;
									case R.id.launch_app:
										launchApp(ctx, pkg, pm);
										break;
									case R.id.uninstall_app:
										uninstallApp(ctx, pkg);
										break;
									case R.id.import_framework:
										new ImportFrameworkTask(ctx).execute(file);
										break;
									default:
										decode(ctx, file, id, getMessage() + ".apk");
										break;
								}
							}
						});
				}
			});
	}
	protected void decode(Context ctx, File file, int id, String name) {
		switch (id) {
			case R.id.decompile_all:
				new DecodeTask(ctx, null, 3, name).execute(file);
				break;
			case R.id.decompile_res:
				new DecodeTask(ctx, null, 2, name).execute(file);
				break;
			case R.id.decompile_dex:
				new DecodeTask(ctx, null, 1, name).execute(file);
				break;
		}
	}
	private static void launchApp(Context context, PackageInfo pkg, PackageManager pm) {
		Intent launch = pm.getLaunchIntentForPackage(pkg.packageName);
		if (launch == null) {
			android.widget.Toast.makeText(context, R.string.app_no_launch_activity,
					android.widget.Toast.LENGTH_SHORT).show();
			return;
		}
		context.startActivity(launch);
	}
	private static void uninstallApp(Context context, PackageInfo pkg) {
		Intent uninstall = new Intent(Intent.ACTION_DELETE);
		uninstall.setData(Uri.parse("package:" + pkg.packageName));
		uninstall.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		try {
			context.startActivity(uninstall);
		} catch (Exception exception) {
			android.widget.Toast.makeText(context, R.string.app_uninstall_unavailable,
					android.widget.Toast.LENGTH_SHORT).show();
		}
	}
	protected static void showAppDetails(Context context, PackageInfo pkg, PackageManager pm) {
		StringBuilder sb = new StringBuilder();
		sb.append("VersionCode: ");
		sb.append(pkg.versionCode);
		sb.append("\nVersionName: ");
		sb.append(pkg.versionName);
		sb.append("\nPackage: ");
		ApplicationInfo info = pkg.applicationInfo;
		sb.append(info.packageName);
		sb.append("\nPath: ");
		sb.append(info.sourceDir);
		new AlertDialog.Builder(context).
			setTitle(info.loadLabel(pm)).
			setIcon(info.loadIcon(pm)).
			setPositiveButton(R.string.ok, null).
			setMessage(sb).
			show();
	}
}
