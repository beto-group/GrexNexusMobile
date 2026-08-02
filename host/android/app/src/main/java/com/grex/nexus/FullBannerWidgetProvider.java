package com.grex.nexus;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class FullBannerWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_BANNER_CYCLE = "com.grex.nexus.BANNER_CYCLE";

    private static int bannerIdx = 0;
    private static final String[] TICKERS = {
        "USD 4.42 MYR  •  EUR 1.08 USD  •  ETH $3,450  •  BTC $67,200",
        "SOL $185.50  •  MYR 0.226 USD  •  BNB $580.10  •  DOGE $0.125",
        "DMs 148  •  Auto-Reply ACTIVE  •  AI Sovereign  •  Status 🟢 LIVE"
    };

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_BANNER_CYCLE.equals(intent.getAction())) {
            bannerIdx = (bannerIdx + 1) % TICKERS.length;
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
        ComponentName componentName = new ComponentName(context, FullBannerWidgetProvider.class);
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

        Intent cycleIntent = new Intent(context, FullBannerWidgetProvider.class);
        cycleIntent.setAction(ACTION_BANNER_CYCLE);
        PendingIntent pendingCycleIntent = PendingIntent.getBroadcast(
                context, appWidgetId, cycleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.full_banner_widget_layout);
        views.setTextViewText(R.id.full_banner_ticker, TICKERS[bannerIdx]);

        views.setOnClickPendingIntent(R.id.full_banner_container, pendingLaunchIntent);
        views.setOnClickPendingIntent(R.id.btn_full_banner_pair, pendingCycleIntent);
        views.setOnClickPendingIntent(R.id.btn_full_banner_refresh, pendingCycleIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}
