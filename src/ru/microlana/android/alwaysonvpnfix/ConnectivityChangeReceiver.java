package ru.microlana.android.alwaysonvpnfix;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class ConnectivityChangeReceiver 
		extends BroadcastReceiver {
	private final static String TAG = 
			ConnectivityChangeReceiver.class.getSimpleName();

	/**
	 * ConnectivityManager.EXTRA_NETWORK_INFO is deprecated in API 14.
	 */
	@TargetApi(android.os.Build.VERSION_CODES.JELLY_BEAN_MR1)
	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d(TAG, "Network state changed.");

		final ConnectivityManager connectivityManager = 
				(ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		
		if(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			@SuppressWarnings("deprecation")
			final NetworkInfo networkInfo = 
				intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
			
			networkStateChanged(context, networkInfo);
		} else if(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
			final NetworkInfo[] networks =
				connectivityManager.getAllNetworkInfo();
			
			if(networks != null) {
				for(final NetworkInfo networkInfo : networks) {
					networkStateChanged(context, networkInfo);
				}
			}
		} else {
			final int networkType = intent.getIntExtra(ConnectivityManager.EXTRA_NETWORK_TYPE, -1);
			
			if(networkType < 0) {
				return;
			}
			
			final NetworkInfo networkInfo = 
				connectivityManager.getNetworkInfo(networkType);
			
			networkStateChanged(context, networkInfo);
		}
	}

	private void networkStateChanged(final Context context,
									 final NetworkInfo networkInfo) {
		if(networkInfo == null) {
			return;
		}
		
		Log.d(TAG, 
			  "Changed state for network" +
			  networkInfo.getTypeName() +
			  " (" +
			  networkInfo.getSubtypeName() +
			  ")");
		
		// Обращение к сервису для исправления возможной ошибки в firewall
		NetworkMonitorService.startService(context);
	}
}
