package com.stardust.scriptdroid.autojs;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.support.annotation.Nullable;

import com.stardust.autojs.ScriptEngineService;
import com.stardust.autojs.ScriptEngineServiceBuilder;
import com.stardust.autojs.engine.NodeJsJavaScriptEngineManager;
import com.stardust.autojs.runtime.*;
import com.stardust.autojs.runtime.api.Console;
import com.stardust.automator.AccessibilityEventCommandHost;
import com.stardust.automator.simple_action.SimpleActionPerformHost;
import com.stardust.scriptdroid.App;
import com.stardust.scriptdroid.Pref;
import com.stardust.scriptdroid.R;
import com.stardust.scriptdroid.scripts.StorageScriptProvider;
import com.stardust.view.accessibility.AccessibilityInfoProvider;
import com.stardust.scriptdroid.layout_inspector.LayoutInspector;
import com.stardust.scriptdroid.record.accessibility.AccessibilityActionRecorder;
import com.stardust.scriptdroid.service.AccessibilityWatchDogService;
import com.stardust.scriptdroid.tool.AccessibilityServiceTool;
import com.stardust.scriptdroid.ui.console.TimberConsole;
import com.stardust.view.accessibility.AccessibilityServiceUtils;


/**
 * Created by Stardust on 2017/4/2.
 */

public class AutoJs implements AccessibilityBridge {

    private static AutoJs instance;

    public static AutoJs getInstance() {
        return instance;
    }

    public static void initInstance(Context context) {
        instance = new AutoJs(context);
    }

    private final AccessibilityEventCommandHost mAccessibilityEventCommandHost = new AccessibilityEventCommandHost();
    private final SimpleActionPerformHost mSimpleActionPerformHost = new SimpleActionPerformHost();
    private final AccessibilityActionRecorder mAccessibilityActionRecorder = new AccessibilityActionRecorder();
    private final LayoutInspector mLayoutInspector = new LayoutInspector();
    private final ScriptEngineService mScriptEngineService;
    private final AccessibilityInfoProvider mAccessibilityInfoProvider;
    private final ScriptRuntime mRuntime;


    private AutoJs(Context context) {
        mAccessibilityInfoProvider = new AccessibilityInfoProvider(context.getPackageManager());
        Console console = new TimberConsole();
        mRuntime = new ScriptRuntime(context, console, this);
        NodeJsJavaScriptEngineManager manager = new NodeJsJavaScriptEngineManager(context, mRuntime);
        manager.setRequirePath(StorageScriptProvider.DEFAULT_DIRECTORY_PATH);
        mScriptEngineService = new ScriptEngineServiceBuilder()
                .context(context)
                .engineManger(manager)
                .console(console)
                .runtime(mRuntime)
                .build();
        ScriptEngineService.setInstance(mScriptEngineService);
        addAccessibilityServiceDelegates();
    }

    private void addAccessibilityServiceDelegates() {
        AccessibilityWatchDogService.addDelegate(100, mAccessibilityInfoProvider);
        // AccessibilityWatchDogService.addDelegate(200, mLayoutInspector);
        AccessibilityWatchDogService.addDelegate(300, mAccessibilityActionRecorder);
        // AccessibilityWatchDogService.addDelegate(400, mSimpleActionPerformHost);
        //AccessibilityWatchDogService.addDelegate(500, mAccessibilityEventCommandHost);
    }

    public AccessibilityActionRecorder getAccessibilityActionRecorder() {
        return mAccessibilityActionRecorder;
    }

    public LayoutInspector getLayoutInspector() {
        return mLayoutInspector;
    }

    @Override
    public AccessibilityEventCommandHost getCommandHost() {
        return mAccessibilityEventCommandHost;
    }

    @Override
    public SimpleActionPerformHost getActionPerformHost() {
        return mSimpleActionPerformHost;
    }

    @Nullable
    @Override
    public AccessibilityService getService() {
        return AccessibilityWatchDogService.getInstance();
    }

    @Override
    public void ensureServiceEnabled() {
        if (AccessibilityWatchDogService.getInstance() == null) {
            String errorMessage = null;
            if (AccessibilityServiceUtils.isAccessibilityServiceEnabled(App.getApp(), AccessibilityWatchDogService.class)) {
                errorMessage = App.getApp().getString(R.string.text_auto_operate_service_enabled_but_not_running);
            } else {
                if (Pref.enableAccessibilityServiceByRoot()) {
                    if (!AccessibilityServiceTool.enableAccessibilityServiceByRootAndWaitFor(2000)) {
                        errorMessage = App.getApp().getString(R.string.text_enable_accessibility_service_by_root_timeout);
                    }
                } else {
                    errorMessage = App.getApp().getString(R.string.text_no_accessibility_permission);
                }
            }
            if (errorMessage != null) {
                mRuntime.toast(errorMessage);
                throw new ScriptStopException(errorMessage);
            }
        }
    }

    @Override
    public AccessibilityInfoProvider getInfoProvider() {
        return mAccessibilityInfoProvider;
    }

    public ScriptEngineService getScriptEngineService() {
        return mScriptEngineService;
    }
}
