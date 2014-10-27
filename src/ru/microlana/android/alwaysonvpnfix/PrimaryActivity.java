package ru.microlana.android.alwaysonvpnfix;

import android.app.Activity;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class PrimaryActivity 
		extends Activity {
	private final static String TAG = 
			PrimaryActivity.class.getSimpleName();
	
	private boolean approve;

	/**
	 * Вызывается при создании activity
	 */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "Create primary activity.");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.primary);
        
        // По умолчанию считаем, что пользователь согласен использовать приложение
        approve = true;

        final TextView textViewDescription = 
        		(TextView)findViewById(R.id.textViewDescription);
        
        textViewDescription.setClickable(true);
        textViewDescription.setMovementMethod (LinkMovementMethod.getInstance());
        
        // Установка текстового описания
        textViewDescription
        		.setText(Html.fromHtml(getString(R.string.message_primary_description)));
        
        // Добавляем действие на кнопку "Применить"
        ((Button)findViewById(R.id.buttonApply)).setOnClickListener(new OnClickListener() {
        	/**
        	 * Метод, вызываемый при нажатии на кнопку
        	 */
			@Override
			public void onClick(View v) {
				// Закрыть activity
				finish();
			}
        });

        // Добавляем действие на кнопку "Отменить"
        ((Button)findViewById(R.id.buttonCancel)).setOnClickListener(new OnClickListener() {
        	/**
        	 * Метод, вызываемый при нажатии на кнопку
        	 */
			@Override
			public void onClick(View v) {
				// Пользователь отказался от приложения
		        approve = false;
		        
				// Закрыть activity
				finish();
			}
        });
        
		if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
			// Закрыть activity при нажатии куда-нибудь за ее пределами
			setFinishOnTouchOutside(true);
		}
    }

    /**
     * Вызывается при разрушении activity
     */
	@Override
	protected void onDestroy() {
		if(approve) {
			// Выполняем старт сервиса мониторинга сетевых подключений
			NetworkMonitorService.startService(this);			
		}

		// Выполняем базовый метод
		super.onDestroy();
	}
    
    
}
