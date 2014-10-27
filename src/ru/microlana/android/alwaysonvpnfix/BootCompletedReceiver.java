package ru.microlana.android.alwaysonvpnfix;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;


public class BootCompletedReceiver 
		extends BroadcastReceiver {
	private final static String TAG = 
			BootCompletedReceiver.class.getSimpleName();

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d(TAG, "Received BOOT_COMPLETED broadcast, start network monitor service.");

		// Запускаем службу, которая отслеживает состояния сетевых подключений
		NetworkMonitorService.startService(context);
	}
}
