package group.beto.grexnexus.child;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import group.beto.grexnexus.child.pkgplaceholder00000.R;

public class GrexWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_TOGGLE_AUTO_REPLY = "group.beto.grexnexus.child.TOGGLE_AUTO_REPLY";
    public static final String ACTION_REFRESH_WIDGET = "group.beto.grexnexus.child.REFRESH_WIDGET";

    private static boolean isAutoReplyActive = true;
    private static int processedDmsCount = 148;

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        String action = intent.getAction();
        if (ACTION_TOGGLE_AUTO_REPLY.equals(action)) {
            isAutoReplyActive = !isAutoReplyActive;
            updateAllWidgets(context);
        } else if (ACTION_REFRESH_WIDGET.equals(action)) {
            processedDmsCount += 5;
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
        ComponentName componentName = new ComponentName(context, GrexWidgetProvider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        Intent launchIntent = new Intent(context, ChildActivity.class);
        launchIntent.setAction(Intent.ACTION_VIEW);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingLaunchIntent = PendingIntent.getActivity(
                context, appWidgetId, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent toggleIntent = new Intent(context, GrexWidgetProvider.class);
        toggleIntent.setAction(ACTION_TOGGLE_AUTO_REPLY);
        PendingIntent pendingToggleIntent = PendingIntent.getBroadcast(
                context, appWidgetId, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent refreshIntent = new Intent(context, GrexWidgetProvider.class);
        refreshIntent.setAction(ACTION_REFRESH_WIDGET);
        PendingIntent pendingRefreshIntent = PendingIntent.getBroadcast(
                context, appWidgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.grex_widget_layout);
        views.setTextViewText(R.id.widget_title, "Social Bot Engine");

        if (isAutoReplyActive) {
            views.setTextViewText(R.id.widget_status, "🟢 Active • AI Ready");
            views.setTextViewText(R.id.widget_stat2_val, "ACTIVE");
            views.setTextColor(R.id.widget_stat2_val, 0xFF22C55E);
        } else {
            views.setTextViewText(R.id.widget_status, "🟡 Paused");
            views.setTextViewText(R.id.widget_stat2_val, "PAUSED");
            views.setTextColor(R.id.widget_stat2_val, 0xFFEAB308);
        }

        views.setTextViewText(R.id.widget_stat1_val, processedDmsCount + " DMs");

        views.setOnClickPendingIntent(R.id.widget_container, pendingLaunchIntent);
        views.setOnClickPendingIntent(R.id.btn_widget_toggle, pendingToggleIntent);
        views.setOnClickPendingIntent(R.id.btn_widget_refresh, pendingRefreshIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}
