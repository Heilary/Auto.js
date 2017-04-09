package com.stardust.scriptdroid.ui.edit;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.util.SparseArray;
import android.view.View;
import android.view.inputmethod.InputMethod;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.stardust.autojs.engine.JavaScriptEngine;
import com.stardust.autojs.ScriptExecutionListener;
import com.stardust.autojs.script.FileScriptSource;
import com.stardust.autojs.script.ScriptSource;
import com.stardust.autojs.script.StringScriptSource;
import com.stardust.pio.PFile;
import com.stardust.scriptdroid.Pref;
import com.stardust.scriptdroid.autojs.AutoJs;
import com.stardust.scriptdroid.scripts.ScriptFile;
import com.stardust.scriptdroid.ui.BaseActivity;
import com.stardust.scriptdroid.ui.edit.editor920.Editor920Activity;
import com.stardust.scriptdroid.ui.edit.editor920.Editor920Utils;
import com.stardust.scriptdroid.ui.edit.sidemenu.EditSideMenuFragment;
import com.stardust.scriptdroid.ui.edit.sidemenu.FunctionListRecyclerView;
import com.stardust.theme.dialog.ThemeColorMaterialDialogBuilder;
import com.stardust.util.SparseArrayEntries;
import com.stardust.view.ViewBinding;
import com.stardust.widget.ToolbarMenuItem;
import com.jecelyin.editor.v2.common.Command;
import com.jecelyin.editor.v2.common.SaveListener;
import com.jecelyin.editor.v2.core.widget.TextView;
import com.jecelyin.editor.v2.ui.EditorDelegate;
import com.jecelyin.editor.v2.view.EditorView;
import com.jecelyin.editor.v2.view.menu.MenuDef;
import com.stardust.scriptdroid.App;
import com.stardust.scriptdroid.R;
import com.stardust.scriptdroid.ui.edit.completion.InputMethodEnhanceBar;
import com.stardust.theme.ThemeColorManager;
import com.stardust.view.ViewBinder;

import java.io.File;

import timber.log.Timber;

/**
 * Created by Stardust on 2017/1/29.
 */

public class EditActivity extends Editor920Activity {

    public static class InputMethodEnhanceBarBridge implements InputMethodEnhanceBar.EditTextBridge {

        private Editor920Activity mEditor920Activity;
        private TextView mTextView;

        public InputMethodEnhanceBarBridge(Editor920Activity editor920Activity, TextView textView) {
            mEditor920Activity = editor920Activity;
            mTextView = textView;
        }

        @Override
        public void appendText(CharSequence text) {
            mEditor920Activity.insertText(text);
        }

        @Override
        public void backspace(int count) {

        }

        @Override
        public TextView getEditText() {
            return mTextView;
        }
    }

    public static final String EXTRA_CONTENT = "Still Love Eating 17.4.5";

    private static final String ACTION_ON_RUN_FINISHED = "ACTION_ON_RUN_FINISHED";
    private static final String EXTRA_EXCEPTION_MESSAGE = "EXTRA_EXCEPTION_MESSAGE";


    private static final ScriptExecutionListener SCRIPT_EXECUTION_LISTENER = new ScriptExecutionListener() {

        @Override
        public void onStart(JavaScriptEngine engine, ScriptSource source) {
            AutoJs.getInstance().getScriptEngineService().getDefaultListener().onStart(engine, source);
        }

        @Override
        public void onSuccess(JavaScriptEngine engine, ScriptSource source, Object result) {
            App.getApp().sendBroadcast(new Intent(ACTION_ON_RUN_FINISHED));
        }

        @Override
        public void onException(JavaScriptEngine engine, ScriptSource source, Exception e) {
            App.getApp().sendBroadcast(new Intent(ACTION_ON_RUN_FINISHED)
                    .putExtra(EXTRA_EXCEPTION_MESSAGE, e.getMessage()));
            e.printStackTrace();
        }

    };

    public static void editFile(Context context, String path) {
        editFile(context, null, path);
    }

    public static void view(Context context, String name, String content) {
        context.startActivity(new Intent(context, EditActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("readOnly", true)
                .putExtra("content", content)
                .putExtra("name", name));
    }

    public static void editFile(Context context, String name, String path) {
        context.startActivity(new Intent(context, EditActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("path", path)
                .putExtra("name", name));
    }

    public static void editFile(Context context, ScriptFile file) {
        editFile(context, file.getSimplifiedName(), file.getPath());
    }

    private String mName;
    private File mFile;
    private View mView;
    private DrawerLayout mDrawerLayout;
    private EditorDelegate mEditorDelegate;
    private SparseArray<ToolbarMenuItem> mMenuMap;
    private boolean mReadOnly = false;
    private BroadcastReceiver mOnRunFinishedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_ON_RUN_FINISHED)) {
                setMenuStatus(R.id.run, MenuDef.STATUS_NORMAL);
                String msg = intent.getStringExtra(EXTRA_EXCEPTION_MESSAGE);
                if (msg != null) {
                    Snackbar.make(mView, getString(R.string.text_error) + ": " + msg, Snackbar.LENGTH_LONG).show();
                    Timber.e(msg);
                }
            }
        }
    };

    public void onCreate(Bundle b) {
        super.onCreate(b);
        setTheme(R.style.EditorTheme);
        mView = View.inflate(this, R.layout.activity_edit, null);
        setContentView(mView);
        handleIntent(getIntent());
        setUpUI();
        setUpEditor();
        registerReceiver(mOnRunFinishedReceiver, new IntentFilter(ACTION_ON_RUN_FINISHED));
    }

    @Override
    protected void onStart() {
        super.onStart();
        openDrawerIfFirstUse();
    }

    private void openDrawerIfFirstUse() {
        if (Pref.isEditActivityFirstUsing()) {
            mDrawerLayout.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mDrawerLayout.openDrawer(GravityCompat.END);
                }
            }, 1000);
        }
    }

    private void handleIntent(Intent intent) {
        String path = intent.getStringExtra("path");
        mName = intent.getStringExtra("name");
        mReadOnly = intent.getBooleanExtra("readOnly", false);
        boolean saveEnabled = intent.getBooleanExtra("saveEnabled", true);
        if (mReadOnly || !saveEnabled) {
            findViewById(R.id.save).setVisibility(View.GONE);
        }
        String content = intent.getStringExtra("content");
        if (content != null) {
            mEditorDelegate = new EditorDelegate(0, mName, content);
        } else {
            mFile = new File(path);
            if (mName == null) {
                mName = mFile.getName();
            }
            mEditorDelegate = new EditorDelegate(0, mFile, 0, "utf-8");
        }
    }

    private void setUpUI() {
        ThemeColorManager.addActivityStatusBar(this);
        mDrawerLayout = (DrawerLayout) mView.findViewById(R.id.drawer_layout);
        initSideMenuFragment();
        setUpToolbar();
        initMenuItem();
        ViewBinder.bind(this);
    }

    private void initSideMenuFragment() {
        EditSideMenuFragment.setFragment(EditActivity.this, R.id.fragment_edit_side_menu)
                .setOnFunctionClickListener(new FunctionListRecyclerView.OnFunctionClickListener() {
                    @Override
                    public void onClick(FunctionListRecyclerView.Function function, int position) {
                        if (!mReadOnly)
                            insertText(function.name);
                        mDrawerLayout.closeDrawer(GravityCompat.END);
                    }
                });
    }

    private void setUpEditor() {
        final EditorView editorView = (EditorView) findViewById(R.id.editor);
        mEditorDelegate.setEditorView(editorView);
        if (mFile == null)
            Editor920Utils.setLang(mEditorDelegate, "JavaScript");
        editorView.getEditText().setReadOnly(mReadOnly);
        editorView.getEditText().setHorizontallyScrolling(true);
        setUpInputMethodEnhanceBar(editorView);

    }

    private void setUpInputMethodEnhanceBar(final EditorView editorView) {
        InputMethodEnhanceBar inputMethodEnhanceBar = (InputMethodEnhanceBar) findViewById(R.id.input_method_enhance_bar);
        if (mReadOnly) {
            inputMethodEnhanceBar.setVisibility(View.GONE);
        } else {
            inputMethodEnhanceBar.setEditTextBridge(new InputMethodEnhanceBarBridge(this, editorView.getEditText()));
        }
    }


    private void setUpToolbar() {
        BaseActivity.setToolbarAsBack(this, R.id.toolbar, mName);
    }

    @ViewBinding.Click(R.id.run)
    private void runAndSaveFileIFNeeded() {
        if (!mReadOnly && mEditorDelegate.isChanged()) {
            saveFile(false, new SaveListener() {
                @Override
                public void onSaved() {
                    run();
                }
            });
        } else {
            run();
        }
    }

    private void saveFile(boolean toast, SaveListener listener) {
        Command command = new Command(Command.CommandEnum.SAVE);
        command.args = new Bundle();
        command.args.putBoolean("is_cluster", !toast);
        command.object = listener;
        mEditorDelegate.doCommand(command);
    }

    private void run() {
        Snackbar.make(mView, R.string.text_start_running, Snackbar.LENGTH_SHORT).show();
        setMenuStatus(R.id.run, MenuDef.STATUS_DISABLED);
        if (mFile != null) {
            AutoJs.getInstance().getScriptEngineService().execute(new FileScriptSource(mName, mFile), SCRIPT_EXECUTION_LISTENER);
        } else {
            AutoJs.getInstance().getScriptEngineService().execute(new StringScriptSource(mName, mEditorDelegate.getText()), SCRIPT_EXECUTION_LISTENER);
        }
    }

    @ViewBinding.Click(R.id.undo)
    private void undo() {
        Command command = new Command(Command.CommandEnum.UNDO);
        mEditorDelegate.doCommand(command);
    }

    @ViewBinding.Click(R.id.redo)
    private void redo() {
        Command command = new Command(Command.CommandEnum.REDO);
        mEditorDelegate.doCommand(command);
    }


    @ViewBinding.Click(R.id.save)
    private void saveFile() {
        saveFile(false, null);
    }

    private void initMenuItem() {
        mMenuMap = new SparseArrayEntries<ToolbarMenuItem>()
                .entry(com.jecelyin.editor.v2.R.id.m_redo, (ToolbarMenuItem) findViewById(R.id.redo))
                .entry(com.jecelyin.editor.v2.R.id.m_undo, (ToolbarMenuItem) findViewById(R.id.undo))
                .entry(com.jecelyin.editor.v2.R.id.m_save, (ToolbarMenuItem) findViewById(R.id.save))
                .entry(R.id.run, (ToolbarMenuItem) findViewById(R.id.run))
                .sparseArray();
    }

    public void setMenuStatus(int menuResId, int status) {
        ToolbarMenuItem menuItem = mMenuMap.get(menuResId);
        if (menuItem == null)
            return;
        boolean disabled = status == MenuDef.STATUS_DISABLED;
        menuItem.setEnabled(!disabled);
    }

    @Override
    public void finish() {
        if (!mReadOnly && mEditorDelegate.isChanged()) {
            showExitConfirmDialog();
        } else {
            super.finish();
        }
    }


    private void showExitConfirmDialog() {
        new ThemeColorMaterialDialogBuilder(this)
                .title(R.string.text_alert)
                .content(R.string.edit_exit_without_save_warn)
                .positiveText(R.string.text_cancel)
                .negativeText(R.string.text_save_and_exit)
                .neutralText(R.string.text_exit_directly)
                .onNegative(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        saveFile(true, null);
                        EditActivity.super.finish();
                    }
                })
                .onNeutral(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        EditActivity.super.finish();
                    }
                })
                .show();
    }

    @Override
    public void doCommand(Command command) {
        mEditorDelegate.doCommand(command);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mOnRunFinishedReceiver);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        try {
            super.onRestoreInstanceState(savedInstanceState);
        } catch (RuntimeException e) {
            // FIXME: 2017/3/20
            e.printStackTrace();
        }
    }
}
