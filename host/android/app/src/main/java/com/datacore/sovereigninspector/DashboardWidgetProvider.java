package com.datacore.sovereigninspector;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class DashboardWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_DASH_REFRESH = "com.datacore.sovereigninspector.DASH_REFRESH";

    private static int totalDms = 148;

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_DASH_REFRESH.equals(intent.getAction())) {
            totalDms += 10;
            updateAllWidgets(context);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    private static void updateAllWidgets(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName componentName = new ComponentName(context, DashboardWidgetProvider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        Intent launchIntent = new Intent(context, MainActivity.class);
        launchIntent.setAction(Intent.ACTION_VIEW);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingLaunchIntent = PendingIntent.getActivity(
                context, appWidgetId, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent refreshIntent = new Intent(context, DashboardWidgetProvider.class);
        refreshIntent.setAction(ACTION_DASH_REFRESH);
        PendingIntent pendingRefreshIntent = PendingIntent.getBroadcast(
                context, appWidgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.dashboard_widget_layout);
        views.setTextViewText(R.id.dash_stat2_val, totalDms + " Total");

        views.setOnClickPendingIntent(R.id.dashboard_widget_container, pendingLaunchIntent);
        views.setOnClickPendingIntent(R.id.btn_dash_sync, pendingRefreshIntent);
        views.setOnClickPendingIntent(R.id.btn_dash_refresh, pendingRefreshIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}
