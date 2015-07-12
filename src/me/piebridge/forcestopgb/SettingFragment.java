package me.piebridge.forcestopgb;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public abstract class SettingFragment extends ListFragment {

    private Adapter mAdapter;
    private Locale prevLocale;
    private SettingActivity mActivity;
    private Set<String> prevNames = null;
    private View filter;
    private CheckBox check;
    private EditText query;
    private boolean filtering;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        registerForContextMenu(getListView());
        mActivity = (SettingActivity) getActivity();
        if (mActivity != null) {
            setNewAdapterIfNeeded(mActivity, true);
        }
    }

    @Override
    public void onDestroyView() {
        saveListPosition();
        super.onDestroyView();
        mActivity = null;
        setListAdapter(null);
    }

    private void selectAll(boolean checked) {
        if (mActivity != null && mAdapter != null) {
            Set<String> selections = mActivity.getSelection();
            if (checked) {
                selections.addAll(mAdapter.getAllPackages());
            } else {
                selections.clear();
            }
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.list, container, false);
        filter = view.findViewById(R.id.filter);
        check = (CheckBox) filter.findViewById(R.id.filter_check);
        query = (EditText) filter.findViewById(R.id.filter_query);
        check.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectAll(check.isChecked());
            }
        });
        query.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int before, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int after) {
                filtering = true;
                if (mAdapter != null) {
                    mAdapter.getFilter().filter(s);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }

        });
        return view;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        l.showContextMenuForChild(v);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        if (mActivity == null || menu == null || menuInfo == null) {
            return;
        }
        menu.clear();
        ViewHolder holder = (ViewHolder) ((AdapterContextMenuInfo) menuInfo).targetView.getTag();
        menu.setHeaderTitle(holder.nameView.getText());
        if (holder.icon != null) {
            menu.setHeaderIcon(holder.icon);
        }
        menu.add(Menu.NONE, R.string.app_info, Menu.NONE, R.string.app_info);
        if (mActivity.getPreventPackages().containsKey(holder.packageName)) {
            menu.add(Menu.NONE, R.string.remove, Menu.NONE, R.string.remove);
        } else {
            menu.add(Menu.NONE, R.string.prevent, Menu.NONE, R.string.prevent);
        }
        if (getMainIntent(holder.packageName) != null) {
            menu.add(Menu.NONE, R.string.open, Menu.NONE, R.string.open);
        }
        if (holder.canUninstall) {
            menu.add(Menu.NONE, R.string.uninstall, Menu.NONE, R.string.uninstall);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (mActivity == null || item == null) {
            return false;
        }
        ViewHolder holder = (ViewHolder) ((AdapterContextMenuInfo) item.getMenuInfo()).targetView.getTag();
        String packageName = holder.packageName;
        switch (item.getItemId()) {
            case R.string.app_info:
                mActivity.startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)));
                return true;
            case R.string.remove:
                holder.preventView.setVisibility(View.GONE);
                mActivity.changePrevent(packageName, false);
                return true;
            case R.string.prevent:
                holder.preventView.setVisibility(View.VISIBLE);
                holder.preventView.setImageResource(holder.running != null ? R.drawable.ic_menu_stop : R.drawable.ic_menu_block);
                mActivity.changePrevent(packageName, true);
                return true;
            case R.string.open:
                try {
                    mActivity.startActivity(getMainIntent(holder.packageName));
                } catch (ActivityNotFoundException e) {
                    // do nothing
                } catch (Exception e) {
                    // do nothing
                }
                return true;
            case R.string.uninstall:
                mActivity.startActivity(new Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null)));
                return true;
            default:
                return false;
        }
    }

    private Intent getMainIntent(String packageName) {
        PackageManager pm = mActivity.getPackageManager();
        Intent launcher = pm.getLaunchIntentForPackage(packageName);
        if (launcher != null) {
            return launcher;
        } else {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setPackage(packageName);
            List<ResolveInfo> ris = pm.queryIntentActivities(intent, 0);
            if (ris != null && ris.size() > 0) {
                // find the first exported activity
                for (ResolveInfo ri : ris) {
                    ActivityInfo ai = ri.activityInfo;
                    if (!ai.exported) {
                        continue;
                    }
                    if (ai.enabled) {
                        return new Intent().setFlags(Intent.FLAG_ACTIVITY_NEW_TASK).setClassName(ai.packageName, ai.name);
                    } else {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    public void refresh(boolean force) {
        if (mActivity != null) {
            setNewAdapterIfNeeded(mActivity, force);
            if (mActivity.getSelection().isEmpty()) {
                check.setChecked(false);
            }
        }
    }

    protected abstract Set<String> getPackageNames(SettingActivity activity);

    protected abstract boolean canUseCache();

    protected abstract void setListPosition(Position position);

    protected abstract Position getListPosition();

    private void saveListPosition() {
        ListView l = getListView();
        int position = l.getFirstVisiblePosition();
        View v = l.getChildAt(0);
        int top = (v == null) ? 0 : v.getTop();
        setListPosition(new Position(position, top));
    }

    private void setNewAdapterIfNeeded(SettingActivity activity, boolean force) {
        Set<String> names;
        if (force || prevNames == null) {
            names = getPackageNames(activity);
        } else {
            names = prevNames;
        }
        if (mAdapter == null || !Locale.getDefault().equals(prevLocale) || !names.equals(prevNames)) {
            if (mAdapter != null) {
                setListAdapter(null);
            }
            mAdapter = new Adapter(activity, names, canUseCache());
            setListAdapter(mAdapter);
            if (prevNames == null) {
                prevNames = new HashSet<String>();
            }
            prevNames.clear();
            prevNames.addAll(names);
            prevLocale = Locale.getDefault();
        } else {
            mAdapter.notifyDataSetChanged();
            Position position = getListPosition();
            if (position != null) {
                getListView().setSelectionFromTop(position.position, position.top);
            }
        }
    }

    public void showFilter() {
        if (filter != null) {
            filter.setVisibility(View.VISIBLE);
        }
    }

    static class Position {
        int position;
        int top;

        public Position(int _position, int _top) {
            position = _position;
            top = _top;
        }
    }

    static class AppInfo implements Comparable<AppInfo> {
        int flags;
        String name = "";
        String packageName;
        Set<Integer> running;

        public AppInfo(String _packageName, String _name, Set<Integer> _running) {
            super();
            packageName = _packageName;
            if (_name != null) {
                name = _name;
            }
            running = _running;
        }

        @Override
        public String toString() {
            return (running == null ? "1" : "0") + (isSystem() ? "1" : "0") + "/" + name + "/" + packageName;
        }

        @Override
        public int compareTo(AppInfo another) {
            return Collator.getInstance().compare(toString(), another.toString());
        }

        public AppInfo flags(int _flags) {
            flags = _flags;
            return this;
        }

        public boolean isSystem() {
            return (flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
        }
    }

    static class ViewHolder {
        String label;
        String packageName;
        CheckBox checkView;
        ImageView iconView;
        TextView nameView;
        TextView summaryView;
        TextView loadingView;
        ImageView preventView;
        Drawable icon;
        Set<Integer> running;
        boolean canUninstall;
    }

    static class Adapter extends ArrayAdapter<AppInfo> {
        private PackageManager pm;
        private LayoutInflater inflater;
        private SettingActivity mActivity;
        private static Map<String, String> labels = new HashMap<String, String>();
        private final CompoundButton.OnCheckedChangeListener mListener;

        private ArrayList<AppInfo> mAppInfos = new ArrayList<AppInfo>();
        private Set<String> mNames = new HashSet<String>();
        private Set<String> mFiltered;
        private Filter mFilter;

        public Filter getFilter() {
            if (mFilter == null) {
                mFilter = new SimpleFilter();
            }
            return mFilter;
        }

        public Collection<String> getAllPackages() {
            if (mFiltered == null) {
                return mNames;
            } else {
                return mFiltered;
            }
        }

        class SimpleFilter extends Filter {
            @Override
            protected FilterResults performFiltering(CharSequence prefix) {
                FilterResults results = new FilterResults();
                String filter = null;
                if (prefix != null && prefix.length() > 0) {
                    filter = prefix.toString().toLowerCase(Locale.US);
                }
                if (mFiltered == null) {
                    mFiltered = new HashSet<String>();
                }
                List<AppInfo> values = new ArrayList<AppInfo>();
                if (filter == null) {
                    values.addAll(mAppInfos);
                    mFiltered.addAll(mNames);
                } else {
                    mFiltered.clear();
                    for (AppInfo appInfo : mAppInfos) {
                        if (appInfo.name.contains(filter) || ("-3".equals(filter) && !appInfo.isSystem()) || ("-s".equals(filter) && appInfo.isSystem())) {
                            values.add(appInfo);
                            mFiltered.add(appInfo.packageName);
                        }
                    }
                }
                results.values = values;
                results.count = values.size();
                return results;
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void publishResults(CharSequence constraint, FilterResults results) {
                setNotifyOnChange(false);
                clear();
                for (AppInfo appInfo : (List<AppInfo>) results.values) {
                    add(appInfo);
                }
                notifyDataSetChanged();
            }
        }

        public Adapter(SettingActivity activity) {
            super(activity, R.layout.item);
            mActivity = activity;
            pm = mActivity.getPackageManager();
            inflater = LayoutInflater.from(activity);
            mListener = new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    ViewHolder holder = (ViewHolder) buttonView.getTag();
                    Set<String> selections = mActivity.getSelection();
                    if (isChecked) {
                        selections.add(holder.packageName);
                    } else {
                        selections.remove(holder.packageName);
                    }
                    mActivity.checkSelection();
                }
            };
        }

        public Adapter(final SettingActivity activity, Set<String> names, boolean cache) {
            this(activity);
            if (!cache && !Hook.isHookEnabled()) {
                // @formatter:off
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.app_name)
                        .setMessage(R.string.app_notenabled)
                        .setIcon(R.drawable.ic_launcher)
                        .setPositiveButton(activity.getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent("de.robv.android.xposed.installer.OPEN_SECTION")
                                        .setPackage("de.robv.android.xposed.installer")
                                        .putExtra("section", "modules")
                                        .putExtra("module", mActivity.getPackageName())
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                try {
                                    activity.startActivity(intent);
                                } catch (ActivityNotFoundException e) {
                                    activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.saurik.substrate")));
                                }
                            }
                        }).create().show();
                // @formatter:on
            } else {
                mNames.addAll(names);
                addAll(names, cache);
            }
        }

        public void addAll(final Set<String> names, final boolean cache) {
            new AsyncTask<Void, Integer, TreeSet<AppInfo>>() {
                ProgressDialog dialog;

                @Override
                protected void onPreExecute() {
                    if (!cache) {
                        labels.clear();
                        dialog = new ProgressDialog(mActivity);
                        dialog.setMessage(mActivity.getString(R.string.loading));
                        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                        dialog.setCancelable(false);
                        dialog.setMax(names.size());
                        dialog.show();
                    }
                }

                @Override
                protected TreeSet<AppInfo> doInBackground(Void... params) {
                    Map<String, Set<Integer>> running = mActivity.getRunningProcesses();
                    TreeSet<AppInfo> applications = new TreeSet<AppInfo>();
                    int i = 1;
                    for (String name : names) {
                        try {
                            publishProgress(++i);
                            ApplicationInfo info = pm.getApplicationInfo(name, 0);
                            if (!info.enabled) {
                                continue;
                            }
                            String label;
                            if (!cache) {
                                label = info.loadLabel(pm).toString();
                                labels.put(name, label);
                            } else {
                                label = labels.get(name);
                                if (label == null) {
                                    label = info.loadLabel(pm).toString();
                                }
                            }
                            applications.add(new AppInfo(name, label, running.get(name)).flags(info.flags));
                        } catch (NameNotFoundException e) { // NOSONAR
                            // do nothing
                        }
                    }
                    return applications;
                }

                @Override
                protected void onProgressUpdate(Integer... progress) {
                    if (!cache) {
                        dialog.setProgress(progress[0]);
                    }
                }

                @Override
                protected void onPostExecute(TreeSet<AppInfo> applications) {
                    for (AppInfo application : applications) {
                        add(application);
                        mAppInfos.add(application);
                    }
                    try {
                        if (!cache) {
                            dialog.dismiss();
                        }
                    } catch (Exception e) {
                        // do nothing
                    }
                    mActivity.showFilter();
                }
            }.execute();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = inflater.inflate(R.layout.item, null, true);
                ViewHolder viewHolder = new ViewHolder();
                viewHolder.checkView = (CheckBox) view.findViewById(R.id.check);
                viewHolder.iconView = (ImageView) view.findViewById(R.id.icon);
                viewHolder.nameView = (TextView) view.findViewById(R.id.name);
                viewHolder.summaryView = (TextView) view.findViewById(R.id.summary);
                viewHolder.loadingView = (TextView) view.findViewById(R.id.loading);
                viewHolder.preventView = (ImageView) view.findViewById(R.id.prevent);
                viewHolder.checkView.setOnCheckedChangeListener(mListener);
                viewHolder.checkView.setTag(viewHolder);
                view.setTag(viewHolder);
            }

            ViewHolder holder = (ViewHolder) view.getTag();
            AppInfo appInfo = getItem(position);
            holder.label = appInfo.name;
            holder.packageName = appInfo.packageName;
            holder.nameView.setText(appInfo.name);
            holder.summaryView.setVisibility(View.GONE);
            holder.loadingView.setVisibility(View.VISIBLE);
            holder.checkView.setChecked(mActivity.getSelection().contains(holder.packageName));
            Boolean result = mActivity.getPreventPackages().get(appInfo.packageName);
            if (appInfo.isSystem()) {
                view.setBackgroundColor(mActivity.getDangerousColor());
            } else {
                view.setBackgroundColor(mActivity.getTransparentColor());
            }
            holder.canUninstall = ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) || ((appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0);
            if (result == null) {
                holder.preventView.setVisibility(View.INVISIBLE);
            } else {
                holder.preventView.setVisibility(View.VISIBLE);
                holder.preventView.setImageResource(result ? R.drawable.ic_menu_block : R.drawable.ic_menu_stop);
            }
            new AsyncTask<Object, Void, ViewHolder>() {
                @Override
                protected ViewHolder doInBackground(Object... params) {
                    ViewHolder holder = (ViewHolder) params[0];
                    AppInfo appInfo = (AppInfo) params[1];
                    try {
                        holder.icon = ((PackageManager) params[2]).getApplicationIcon(appInfo.packageName);
                    } catch (NameNotFoundException e) {
                        // do nothing
                    }
                    holder.running = mActivity.getRunningProcesses().get(appInfo.packageName);
                    return holder;
                }

                @Override
                protected void onPostExecute(ViewHolder holder) {
                    holder.iconView.setImageDrawable(holder.icon);
                    holder.loadingView.setVisibility(View.GONE);
                    holder.summaryView.setText(formatRunning(holder.running));
                    holder.summaryView.setVisibility(View.VISIBLE);
                }
            }.execute(holder, appInfo, pm);
            return view;
        }

        private CharSequence formatRunning(Set<Integer> running) {
            if (running == null) {
                return mActivity.getString(R.string.notrunning);
            } else {
                TreeSet<String> sets = new TreeSet<String>();
                for (Integer i : running) {
                    switch (i) {
                        case RunningAppProcessInfo.IMPORTANCE_BACKGROUND:
                            sets.add(mActivity.getString(R.string.background));
                            break;
                        case RunningAppProcessInfo.IMPORTANCE_EMPTY:
                            sets.add(mActivity.getString(R.string.empty));
                            break;
                        case RunningAppProcessInfo.IMPORTANCE_FOREGROUND:
                            sets.add(mActivity.getString(R.string.foreground));
                            break;
                        case RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE:
                            sets.add(mActivity.getString(R.string.perceptible));
                            break;
                        case RunningAppProcessInfo.IMPORTANCE_SERVICE:
                            sets.add(mActivity.getString(R.string.service));
                            break;
                        case RunningAppProcessInfo.IMPORTANCE_VISIBLE:
                            sets.add(mActivity.getString(R.string.visible));
                            break;
                        default:
                            break;
                    }
                }
                StringBuilder buffer = new StringBuilder();
                Iterator<?> it = sets.iterator();
                while (true) {
                    buffer.append(it.next());
                    if (it.hasNext()) {
                        buffer.append(", ");
                    } else {
                        break;
                    }
                }
                return buffer.toString();
            }
        }

    }

}