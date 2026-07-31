package com.datacore.sovereigninspector;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/**
 * CurrencyWidgetProvider — Interactive 3x1 Horizontal Banner AppWidget
 */
public class CurrencyWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_SWAP_CURRENCY = "com.datacore.sovereigninspector.SWAP_CURRENCY";
    public static final String ACTION_REFRESH_CURRENCY = "com.datacore.sovereigninspector.REFRESH_CURRENCY";

    private static int pairIndex = 0;

    private static final String[][] PAIRS = {
        {"1 USD = 4.42 MYR", "1 ETH = $3,450.00"},
        {"1 EUR = 1.08 USD", "1 BTC = $67,200.00"},
        {"1 SOL = $185.50", "1 MYR = 0.226 USD"}
    };

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        String action = intent.getAction();
        if (ACTION_SWAP_CURRENCY.equals(action)) {
            pairIndex = (pairIndex + 1) % PAIRS.length;
            updateAllWidgets(context);
        } else if (ACTION_REFRESH_CURRENCY.equals(action)) {
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
        ComponentName componentName = new ComponentName(context, CurrencyWidgetProvider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        // App launch intent
        Intent launchIntent = new Intent(context, MainActivity.class);
        launchIntent.setAction(Intent.ACTION_VIEW);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingLaunchIntent = PendingIntent.getActivity(
                context, appWidgetId, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Broadcast intent for Swap button
        Intent swapIntent = new Intent(context, CurrencyWidgetProvider.class);
        swapIntent.setAction(ACTION_SWAP_CURRENCY);
        PendingIntent pendingSwapIntent = PendingIntent.getBroadcast(
                context, appWidgetId, swapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Broadcast intent for Refresh button
        Intent refreshIntent = new Intent(context, CurrencyWidgetProvider.class);
        refreshIntent.setAction(ACTION_REFRESH_CURRENCY);
        PendingIntent pendingRefreshIntent = PendingIntent.getBroadcast(
                context, appWidgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.currency_widget_layout);
        views.setTextViewText(R.id.currency_widget_rate, PAIRS[pairIndex][0]);
        views.setTextViewText(R.id.currency_widget_sub, PAIRS[pairIndex][1]);
        views.setTextViewText(R.id.btn_currency_refresh, "🔄 Live");

        // Bind interactive click handlers
        views.setOnClickPendingIntent(R.id.currency_widget_container, pendingLaunchIntent);
        views.setOnClickPendingIntent(R.id.btn_currency_swap, pendingSwapIntent);
        views.setOnClickPendingIntent(R.id.btn_currency_refresh, pendingRefreshIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}
