package com.datacore.sovereigninspector;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.util.Locale;

/**
 * CurrencyWidgetProvider — Interactive 3x1 Currency & Crypto Converter Widget
 *
 * Left Button: Cycle Pair (USD/MYR, EUR/USD, ETH/USD, BTC/USD, SOL/USD)
 * Right Button 1: Cycle Amount ($10, $100, $1,000, $5,000)
 * Right Button 2: Reverse Trade Direction (Base ➔ Quote ↔ Quote ➔ Base)
 */
public class CurrencyWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_CYCLE_PAIR = "com.datacore.sovereigninspector.CYCLE_PAIR";
    public static final String ACTION_CYCLE_AMOUNT = "com.datacore.sovereigninspector.CYCLE_AMOUNT";
    public static final String ACTION_TOGGLE_DIRECTION = "com.datacore.sovereigninspector.TOGGLE_DIRECTION";

    private static int pairIndex = 0;
    private static int amountIndex = 1; // Default $100
    private static boolean isReversed = false;

    private static class CurrencyPair {
        String base;
        String quote;
        double rate;

        CurrencyPair(String base, String quote, double rate) {
            this.base = base;
            this.quote = quote;
            this.rate = rate;
        }
    }

    private static final CurrencyPair[] PAIRS = {
        new CurrencyPair("USD", "MYR", 4.42),
        new CurrencyPair("EUR", "USD", 1.08),
        new CurrencyPair("ETH", "USD", 3450.00),
        new CurrencyPair("BTC", "USD", 67200.00),
        new CurrencyPair("SOL", "USD", 185.50)
    };

    private static final double[] AMOUNTS = { 10.0, 100.0, 1000.0, 5000.0 };

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        String action = intent.getAction();
        if (ACTION_CYCLE_PAIR.equals(action)) {
            pairIndex = (pairIndex + 1) % PAIRS.length;
            isReversed = false;
            updateAllWidgets(context);
        } else if (ACTION_CYCLE_AMOUNT.equals(action)) {
            amountIndex = (amountIndex + 1) % AMOUNTS.length;
            updateAllWidgets(context);
        } else if (ACTION_TOGGLE_DIRECTION.equals(action)) {
            isReversed = !isReversed;
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

        // Broadcast intent for Cycle Pair (Left Button)
        Intent pairIntent = new Intent(context, CurrencyWidgetProvider.class);
        pairIntent.setAction(ACTION_CYCLE_PAIR);
        PendingIntent pendingPairIntent = PendingIntent.getBroadcast(
                context, appWidgetId, pairIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Broadcast intent for Cycle Amount (Right Button 1)
        Intent amountIntent = new Intent(context, CurrencyWidgetProvider.class);
        amountIntent.setAction(ACTION_CYCLE_AMOUNT);
        PendingIntent pendingAmountIntent = PendingIntent.getBroadcast(
                context, appWidgetId, amountIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Broadcast intent for Toggle Direction (Right Button 2)
        Intent directionIntent = new Intent(context, CurrencyWidgetProvider.class);
        directionIntent.setAction(ACTION_TOGGLE_DIRECTION);
        PendingIntent pendingDirectionIntent = PendingIntent.getBroadcast(
                context, appWidgetId, directionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        CurrencyPair pair = PAIRS[pairIndex];
        double inputAmount = AMOUNTS[amountIndex];

        String fromCurr = isReversed ? pair.quote : pair.base;
        String toCurr = isReversed ? pair.base : pair.quote;

        double convertedVal;
        if (!isReversed) {
            convertedVal = inputAmount * pair.rate;
        } else {
            convertedVal = inputAmount / pair.rate;
        }

        String rateStr;
        String resultStr;
        if (convertedVal >= 1000) {
            resultStr = String.format(Locale.US, "%.0f %s = %.2f %s", inputAmount, fromCurr, convertedVal, toCurr);
        } else if (convertedVal >= 1) {
            resultStr = String.format(Locale.US, "%.0f %s = %.2f %s", inputAmount, fromCurr, convertedVal, toCurr);
        } else {
            resultStr = String.format(Locale.US, "%.0f %s = %.4f %s", inputAmount, fromCurr, convertedVal, toCurr);
        }

        rateStr = String.format(Locale.US, "%s ➔ %s @ %.2f", fromCurr, toCurr, isReversed ? (1.0 / pair.rate) : pair.rate);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.currency_widget_layout);
        views.setTextViewText(R.id.btn_currency_pair, "💱 " + pair.base + "/" + pair.quote);
        views.setTextViewText(R.id.currency_widget_rate, resultStr);
        views.setTextViewText(R.id.currency_widget_sub, rateStr);

        String amtLabel = String.format(Locale.US, "$%.0f", inputAmount);
        views.setTextViewText(R.id.btn_currency_amount, amtLabel);
        views.setTextViewText(R.id.btn_currency_direction, isReversed ? "⇄ REV" : "⇄ FWD");

        // Bind interactive click handlers
        views.setOnClickPendingIntent(R.id.currency_widget_container, pendingLaunchIntent);
        views.setOnClickPendingIntent(R.id.btn_currency_pair, pendingPairIntent);
        views.setOnClickPendingIntent(R.id.btn_currency_amount, pendingAmountIntent);
        views.setOnClickPendingIntent(R.id.btn_currency_direction, pendingDirectionIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}
