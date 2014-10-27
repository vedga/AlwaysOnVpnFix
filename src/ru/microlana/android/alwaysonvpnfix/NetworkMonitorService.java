package ru.microlana.android.alwaysonvpnfix;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import eu.chainfire.libsuperuser.Shell;

/**
 * Служба, выполняющая операции по исправлению ошибки firewall
 * @author monster
 *
 */
public class NetworkMonitorService 
		extends Service {
	private final static String TAG = 
			NetworkMonitorService.class.getSimpleName();

	// Команда проверки наличия iptables
	private final static String cmdProbeIptables = 
		"iptables --version";
	
	// Команда проверки на наличие подозрительных правил firewall
	private final static String cmdFetchSuspiciousFirewallRules =
		"iptables -v -n --line-numbers -L fw_OUTPUT";
	// Команда исправления правил firewall
	private final static String cmdFixForwardFirewallRejectRule = 
		"iptables -R fw_OUTPUT %1$d -j RETURN";
	
	// Объект проверки возможности использования root-прав
    private ProbeSU probeSu;
    
    // Признак отсутствия диагностических сообщений
    private boolean silent;
    
	/**
	 * Запуск этой службы
	 * @param context
	 */
	public static void startService(Context context) {
		Intent startServiceIntent = new Intent(context, 
			  	   							   NetworkMonitorService.class);

		context.startService(startServiceIntent);
	}
	
	/**
	 * Инициализация перед первым запуском сервиса
	 */
	@Override
	public void onCreate() {
		Log.d(TAG, "Create Network Monitor Service");
		super.onCreate();

		// По умолчанию будем выводить уведомления
		silent = false;
		
		// Объект, проверяющий возможность получения root-прав
		probeSu = new ProbeSU();
        
		// При создании сервиса запускаем асинхронную операцию,
		// проверяющую возможность получения root-прав в системе.
		probeSu.execute();
	}

	/**
	 * Очистка перед остановкой сервиса
	 */
	@Override
	public void onDestroy() {
		Log.d(TAG, "Shutdown Network Monitor Service");
		super.onDestroy();
	}	
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(TAG, "Network Monitor Service called onStartCommand()");

		try {
			// Перед началом операции убеждаемся, что root-права,
			// при необходимости, будут нами получены.
			if(probeSu.get()) {
				// Инициировать асинхронную задачу по исправлению
				// ошибки в firewall
				(new FixFirewall()).execute();
			}
		} catch (InterruptedException e) {
			// Операция была прервана
			e.printStackTrace();
			
			// В данном случае мы не можем выполнить необходимые действия
			// и сервис подлежит остановке
			stopSelf();
		} catch (ExecutionException e) {
			// Во время выполнения операции произошло исключение
			e.printStackTrace();
			
			// В данном случае мы не можем выполнить необходимые действия
			// и сервис подлежит остановке
			stopSelf();
		}
		
		// Если система по какой-либо причине сервис убила,
		// то она же его автоматически должна и перезапустить.
		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	/**
	 * Асинхронная задача по исправлению ошибки в firewall
	 * @author monster
	 *
	 */
	private class FixFirewall extends AsyncTask<Void, Void, Boolean> {
		/**
		 * Метод, выполняемый в служебной нити
		 */
		@Override
		protected Boolean doInBackground(Void... params) {
			// Выполняем исправление известных нам ошибок
			return fixBugFwOutput();
		}

		/**
		 * Метод, вызываемый в UI-нити после выполнения задачи
		 */
		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);
			
			if(result) {
				if(!silent) {
					// Сообщение об исправлении ошибки в firewall
					Toast.makeText(getApplicationContext(), 
							   R.string.message_fixed_firewall, 
							   Toast.LENGTH_SHORT).show();									
				}
			}
		}



		/**
		 * Попытка исправить ошибку в цепочке fw_OUTPUT
		 * @return
		 */
		private boolean fixBugFwOutput() {
			// Получаем набор правил firewall в месте, где
			// возможно находится известная нам ошибка
			final List<String> rules = 
					Shell.SU.run(cmdFetchSuspiciousFirewallRules);
			
			for(final String rule : rules) {
				// Разбиваем результат вывода на колонки
				final String[] columns = rule.split("\\s+");
				
				if((columns.length >= 8) &&
						columns[3].equalsIgnoreCase("REJECT") &&
						columns[4].equalsIgnoreCase("ALL") &&
						columns[6].equalsIgnoreCase("*") &&
						columns[7].equalsIgnoreCase("*")) {
					// Найдена строка с ошибкой, определяем ее номер
					final Integer strNum = Integer.valueOf(columns[0]);
					
					// Формируем команду для исправления
					final String fixCommand =
							String.format(Locale.ROOT,
										  cmdFixForwardFirewallRejectRule, 
										  strNum);
					
					// Выполняем команду исправления
					final List<String> fixCommandOutput = 
							Shell.SU.run(fixCommand);
					
					for(final String out : fixCommandOutput) {
						Log.d(TAG, 
							  "Fix firewall: " + out);
					}
					
					// Ошибка обнаружена и исправлена
					return true;
				}
			}
			
			// Ошибка не обнаружена/не исправлена
			return false;
		}
	}
	
    /**
     * Асинхронная задача для проверки наличия Root в системе
     * @author monster
     *
     */
	private class ProbeSU extends AsyncTask<Void, Void, Boolean> {
		private boolean foundSu;
		private boolean foundIptables;
		
		/**
		 * Конструктор по умолчанию
		 */
		ProbeSU() {
			super();
			
			this.foundSu = false;
			this.foundIptables = false;
		}
		
		/**
		 * Метод, выполняемый в служебной нити
		 */
		@Override
		protected Boolean doInBackground(Void... params) {
			// Операция проверки возможности получения root-прав
			// должна выполняться асинхронно.
			return Shell.SU.available() && 
					probeIptables();
		}

		/**
		 * Метод, выполняемый в UI-нити, перед началом работы над задачей
		 */
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			
			if(!silent) {
				Toast.makeText(getApplicationContext(), 
						   R.string.message_probe_su, 
						   Toast.LENGTH_SHORT).show();				
			}
		}

		/**
		 * Метод, выполняемый в UI-нити, после окончания работы над задачей
		 */
		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);
			
			if(result) {
				if(!silent) {
					Toast.makeText(getApplicationContext(), 
							   R.string.message_probe_su_complete, 
							   Toast.LENGTH_SHORT).show();				
				}
			} else {
				if(!silent) {
					if(!foundSu) {
						Toast.makeText(getApplicationContext(), 
								   R.string.error_root_is_required, 
								   Toast.LENGTH_LONG).show();										
					}
					
					if(!foundIptables) {
						Toast.makeText(getApplicationContext(), 
								   R.string.error_iptables_is_required, 
								   Toast.LENGTH_LONG).show();																
					}
				}
			}
		}
		
		/**
		 * Проверка наличия встроенного SU
		 * @return
		 */
		private boolean probeIptables() {
			// Тест на SU прошел успешно
			this.foundSu = true;
			
			final List<String> output = Shell.SH.run(cmdProbeIptables);
			
			if(output != null) {
				for(final String line : output) {
					if(line.contains("iptables")) {
						this.foundIptables = true;
						break;
					}
				}
			}
			
			return foundSu && 
					foundIptables;
		}
	}	
}
