package com.example.myaac.hybrid;

import android.os.Bundle;

import com.example.myaac.hybrid.plugins.AppLauncherPlugin;
import com.example.myaac.hybrid.plugins.FluentlyTtsPlugin;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Custom local plugins (not published to npm) must be registered explicitly
        // before super.onCreate() -- Capacitor's automatic plugin discovery only
        // covers plugins that ship their own npm package + gradle module.
        registerPlugin(FluentlyTtsPlugin.class);
        registerPlugin(AppLauncherPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
