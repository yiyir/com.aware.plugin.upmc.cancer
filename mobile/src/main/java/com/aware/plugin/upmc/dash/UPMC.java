package com.aware.plugin.upmc.dash;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.aware.Applications;
import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.ui.PermissionsHandler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class UPMC extends AppCompatActivity {

    int[] morningTime = {-1, -1};
    int[] nightTime = {-1, -1};
    public boolean isWatchAround = false;
    private boolean timeInvalid = false;
    private boolean permissions_ok = true;
    private List<Integer> ratingList;
    private BroadcastReceiver mNotifBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra(Constants.COMM_KEY_UPMC)) {
                Log.d(Constants.TAG, "UPMC:BR:wearStopMessageReceived, killing application");
                finish();
            }
        }
    };
    private static final String CASE1 = "<7";
    private static final String CASE2 = ">=7";

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("DASH", "UPMC:onDestroy");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mNotifBroadcastReceiver);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d("DASH", "UPMC:onCreate");
        LocalBroadcastManager.getInstance(this).registerReceiver(mNotifBroadcastReceiver, new IntentFilter(Constants.NOTIFICATION_MESSAGE_INTENT_FILTER));

        ArrayList<String> REQUIRED_PERMISSIONS = new ArrayList<>();
        REQUIRED_PERMISSIONS.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);


        for (String p : REQUIRED_PERMISSIONS) {
            if (PermissionChecker.checkSelfPermission(this, p) != PermissionChecker.PERMISSION_GRANTED) {
                permissions_ok = false;
                break;
            }
        }

        if (!permissions_ok) {
            Intent permissions = new Intent(this, PermissionsHandler.class);
            permissions.putExtra(PermissionsHandler.EXTRA_REQUIRED_PERMISSIONS, REQUIRED_PERMISSIONS);
            permissions.putExtra(PermissionsHandler.EXTRA_REDIRECT_ACTIVITY, getPackageName() + "/" + getClass().getName());
            permissions.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(permissions);
            finish();
        } else {
            if (!Aware.IS_CORE_RUNNING) {
                //This initialises the core framework, assigns Device ID if it doesn't exist yet, etc.
                Intent aware = new Intent(getApplicationContext(), Aware.class);
                startService(aware);
            }
        }
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        assert manager != null;
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }


    public void writeTimePref(int morning_hour, int morning_minute, int night_hour, int night_minute) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt(Constants.MORNING_HOUR, morning_hour);
        editor.putInt(Constants.MORNING_MINUTE, morning_minute);
        editor.putInt(Constants.NIGHT_HOUR, night_hour);
        editor.putInt(Constants.NIGHT_MINUTE, night_minute);
        editor.apply();
    }

    public void readTimePref() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        int morning_hour = sharedPref.getInt(Constants.MORNING_HOUR, -1);
        int morning_minute = sharedPref.getInt(Constants.MORNING_MINUTE, -1);
        this.morningTime[0] = morning_hour;
        this.morningTime[1] = morning_minute;
        int night_hour = sharedPref.getInt(Constants.NIGHT_HOUR, -1);
        int night_minute = sharedPref.getInt(Constants.NIGHT_MINUTE, -1);
        this.nightTime[0] = night_hour;
        this.nightTime[1] = night_minute;
        Log.d(Constants.TAG, "UPMC:readTimePref: " + morning_minute + " " + morning_minute + " " + night_hour + " " + night_minute);
    }

    public int[] getTime() {
        readTimePref();
        if ((this.morningTime[0] == -1) || (this.nightTime[0] == -1)) {
            setTimeInitilaized(false);
        } else {
            setTimeInitilaized(true);
        }
        return this.morningTime;
    }

    public boolean isTimeInitialized() {
        getTime();
        return this.timeInvalid;
    }

    public void setTimeInitilaized(boolean isinit) {
        this.timeInvalid = isinit;
    }

    private TimePicker morning_timer;
    private TimePicker night_timer;


    private void loadSchedule(final boolean firstRun) {
        setContentView(R.layout.settings_upmc_dash);
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        final Button saveSchedule = findViewById(R.id.save_button);

        morning_timer = findViewById(R.id.morning_start_time);
        night_timer = findViewById(R.id.night_sleep_time);
        Log.d(Constants.TAG, "UPMC:loadSchedule:firstRun" + firstRun);

        if (firstRun) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                morning_timer.setHour(9);
                morning_timer.setMinute(0);
                night_timer.setHour(21);
                night_timer.setMinute(0);

            } else {

                morning_timer.setCurrentHour(9);
                morning_timer.setCurrentMinute(0);
                night_timer.setCurrentHour(21);
                night_timer.setCurrentMinute(0);

            }

            saveSchedule.setOnClickListener(new View.OnClickListener() {
                @SuppressLint("SetTextI18n")
                @Override
                public void onClick(View v) {
                    saveTimeSchedule();
                    final ProgressBar progressBar = findViewById(R.id.progress_bar_schedule);
                    progressBar.setVisibility(View.VISIBLE);
                    saveSchedule.setEnabled(false);
                    saveSchedule.setText("Saving Schedule....");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                        writeTimePref(morning_timer.getHour(), morning_timer.getMinute(), night_timer.getHour(), night_timer.getMinute());
                    else
                        writeTimePref(morning_timer.getCurrentHour(), morning_timer.getCurrentMinute(), night_timer.getCurrentHour(), night_timer.getCurrentMinute());
                    setTimeInitilaized(true);

                    if (!Aware.isStudy(getApplicationContext())) {
                        new AsyncJoin().execute();
                    }
                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!Aware.isStudy(getApplicationContext()))
                                Toast.makeText(getApplicationContext(), "Study failed to join, please try again!", Toast.LENGTH_LONG).show();

                        }
                    }, 10000);
                }
            });
        } else {

            int morning_hour = Integer.parseInt(Aware.getSetting(this, Settings.PLUGIN_UPMC_CANCER_MORNING_HOUR));
            int morning_minute = Integer.parseInt(Aware.getSetting(this, Settings.PLUGIN_UPMC_CANCER_MORNING_MINUTE));
            int night_hour = Integer.parseInt(Aware.getSetting(this, Settings.PLUGIN_UPMC_CANCER_NIGHT_HOUR));
            int night_minute = Integer.parseInt(Aware.getSetting(this, Settings.PLUGIN_UPMC_CANCER_NIGHT_MINUTE));
            Log.d(Constants.TAG, "UPMC:loadSchedule:savedTimes:" + morning_hour + "" + morning_minute + "" + night_hour + "" + night_minute);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                morning_timer.setHour(morning_hour);
                morning_timer.setMinute(morning_minute);
                night_timer.setHour(night_hour);
                night_timer.setMinute(night_minute);
            } else {
                morning_timer.setCurrentHour(morning_hour);
                morning_timer.setCurrentMinute(morning_minute);
                night_timer.setCurrentHour(night_hour);
                night_timer.setCurrentMinute(night_minute);
            }


            final Context context = getApplicationContext();

            saveSchedule.setOnClickListener(new View.OnClickListener() {
                @SuppressLint("SetTextI18n")
                @Override
                public void onClick(View v) {
                    saveTimeSchedule();
                    //if(menu.getItem(0).getTitle().equals("Demo Watch")) {
                    Log.d(Constants.TAG, "thread started");
                    isWatchAround = false;
                    LocalBroadcastManager.getInstance(context).registerReceiver(vicinityCheckBroadcastReceiver, new IntentFilter(Constants.VICINITY_CHECK_INTENT_FILTER));
                    LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(Constants.SETTING_INTENT_FILTER).putExtra(Constants.SETTINGS_EXTRA_KEY, Constants.VICINITY_CHECK));
                    final ProgressBar progressBar = findViewById(R.id.progress_bar_schedule);
                    progressBar.setVisibility(View.VISIBLE);
                    saveSchedule.setEnabled(false);
                    saveSchedule.setText("Saving Schedule....");
                    Handler handler = new Handler();


                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            new Thread(new Runnable() {
                                @Override
                                public void run() {


//                                    if (!isWatchAround && isTimeInitialized()) {
//                                        runOnUiThread(new Runnable() {
//                                            @Override
//                                            public void run() {
//                                                saveSchedule.setText("Save Answers");
//                                                Toast.makeText(context, "Failed! Please make sure watch is in range", Toast.LENGTH_SHORT).show();
//                                                progressBar.setVisibility(View.GONE);
//                                                saveSchedule.setEnabled(true);
//                                            }
//                                        });
//                                    } else {
                                    // start MessageService
                                    if (!isTimeInitialized()) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                                            writeTimePref(morning_timer.getHour(), morning_timer.getMinute(), night_timer.getHour(), night_timer.getMinute());
                                        else
                                            writeTimePref(morning_timer.getCurrentHour(), morning_timer.getCurrentMinute(), night_timer.getCurrentHour(), night_timer.getCurrentMinute());
                                        readTimePref();
//                                            if (!isMyServiceRunning(MessageService.class)) {
//                                                startService(new Intent(getApplicationContext(), MessageService.class));
//                                                Log.d(Constants.TAG, "UPMC: Started Message Service");
//                                            } else
//                                                Log.d(Constants.TAG, "UPMC: Message Service already running");
                                        setTimeInitilaized(true);

                                    } else {
                                        Log.d(Constants.TAG, "UPMC: Sending Settings Changed Broadcast");
                                        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(new Intent(Constants.SETTING_INTENT_FILTER).putExtra(Constants.SETTINGS_EXTRA_KEY, Constants.SETTINGS_CHANGED));
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                                            writeTimePref(morning_timer.getHour(), morning_timer.getMinute(), night_timer.getHour(), night_timer.getMinute());
                                        else
                                            writeTimePref(morning_timer.getCurrentHour(), morning_timer.getCurrentMinute(), night_timer.getCurrentHour(), night_timer.getCurrentMinute());
                                    }

//                                        if (!Aware.isStudy(getApplicationContext())) {
//                                            new AsyncJoin().execute();
//                                        }
//                                    }
                                    LocalBroadcastManager.getInstance(context).unregisterReceiver(vicinityCheckBroadcastReceiver);

                                }
                            }).start();
                        }
                    }, 7000);
                }
            });

        }


    }

    private String zeroPad(Integer i) {
        StringBuilder sb = new StringBuilder();
        if (i < 10) {
            sb.append(0).append(i);
            return sb.toString();
        }
        return i.toString();
    }

    private void saveTimeSchedule() {
        // Yiyi's code here....
        String timeData = null;
        StringBuilder sb = new StringBuilder();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            sb.append(zeroPad(morning_timer.getHour()));
            sb.append(zeroPad(morning_timer.getMinute()));
            sb.append(zeroPad(night_timer.getHour()));
            sb.append(zeroPad(night_timer.getMinute()));
            Log.d(Constants.TAG, "total time is: " + sb.toString());
        } else {
            sb.append(zeroPad(morning_timer.getCurrentHour()));
            sb.append(zeroPad(morning_timer.getCurrentMinute()));
            sb.append(zeroPad(night_timer.getCurrentHour()));
            sb.append(zeroPad(night_timer.getCurrentMinute()));
        }
        try {
            timeData = URLEncoder.encode("time", "UTF-8")
                    + "=" + URLEncoder.encode(sb.toString(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        Log.d(Constants.TAG, "time schedule is: " + timeData);
        new PostData().execute("http://localhost:8080/saveTimeSchedule.php", timeData);
    }

    private class AsyncJoin extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            //UPMC Dash
            Aware.joinStudy(getApplicationContext(), "https://r2d2.hcii.cs.cmu.edu/aware/dashboard/index.php/webservice/index/81/Rhi4Q8PqLASf");

            Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_SIGNIFICANT_MOTION, true);

            Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_ACCELEROMETER, true);
            Aware.setSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_ACCELEROMETER, 200000);
            Aware.setSetting(getApplicationContext(), com.aware.plugin.google.activity_recognition.Settings.STATUS_PLUGIN_GOOGLE_ACTIVITY_RECOGNITION, true);
            Aware.setSetting(getApplicationContext(), com.aware.plugin.google.activity_recognition.Settings.FREQUENCY_PLUGIN_GOOGLE_ACTIVITY_RECOGNITION, 300);
            Aware.startPlugin(getApplicationContext(), "com.aware.plugin.google.activity_recognition");

            Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_ESM, true);

            Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_LIGHT, true);
            Aware.setSetting(getApplicationContext(), Aware_Preferences.THRESHOLD_LIGHT, 5);

            Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_BATTERY, true);
            Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_SCREEN, true);

            Aware.setSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_WIFI_ONLY, true);
            Aware.setSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_FALLBACK_NETWORK, 6);
            Aware.setSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_WEBSERVICE, 30);
            Aware.setSetting(getApplicationContext(), Aware_Preferences.DEBUG_FLAG, true);

            Aware.setSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_CLEAN_OLD_DATA, 1);
            Aware.setSetting(getApplicationContext(), Aware_Preferences.WEBSERVICE_SILENT, true);


            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Aware.setSetting(getApplicationContext(), Settings.PLUGIN_UPMC_CANCER_MORNING_HOUR, morning_timer.getHour());
                Aware.setSetting(getApplicationContext(), Settings.PLUGIN_UPMC_CANCER_MORNING_MINUTE, morning_timer.getMinute());
                Aware.setSetting(getApplicationContext(), Settings.PLUGIN_UPMC_CANCER_NIGHT_HOUR, night_timer.getHour());
                Aware.setSetting(getApplicationContext(), Settings.PLUGIN_UPMC_CANCER_NIGHT_MINUTE, night_timer.getMinute());
            } else {
                Aware.setSetting(getApplicationContext(), Settings.PLUGIN_UPMC_CANCER_MORNING_HOUR, morning_timer.getCurrentHour());
                Aware.setSetting(getApplicationContext(), Settings.PLUGIN_UPMC_CANCER_MORNING_MINUTE, morning_timer.getCurrentMinute());
                Aware.setSetting(getApplicationContext(), Settings.PLUGIN_UPMC_CANCER_NIGHT_HOUR, night_timer.getCurrentHour());
                Aware.setSetting(getApplicationContext(), Settings.PLUGIN_UPMC_CANCER_NIGHT_MINUTE, night_timer.getCurrentMinute());
            }


            Aware.startPlugin(getApplicationContext(), "com.aware.plugin.upmc.dash");

            //Ask accessibility to be activated
            Applications.isAccessibilityServiceActive(getApplicationContext());
            Aware.isBatteryOptimizationIgnored(getApplicationContext(), "com.aware.plugin.upmc.dash");
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            Intent applySchedule = new Intent(getApplicationContext(), Plugin.class);
            applySchedule.putExtra("schedule", true);
            Log.d(Constants.TAG, "" + Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            Log.d(Constants.TAG, "" + Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_LABEL));
            Toast.makeText(getApplicationContext(), "" + Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID), Toast.LENGTH_SHORT).show();
            Toast.makeText(getApplicationContext(), "" + Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_LABEL), Toast.LENGTH_SHORT).show();
            startService(applySchedule);
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();


        Aware.setSetting(this, Aware_Preferences.DEBUG_FLAG, false);
        //NOTE: needed for demo to participants
        Aware.setSetting(this, Aware_Preferences.STATUS_ESM, true);
        //Aware.startESM(this);
        if (Aware.getSetting(getApplicationContext(), Settings.PLUGIN_UPMC_CANCER_MORNING_HOUR).length() == 0) {
            loadSchedule(true);
            return;
        }


        ratingList = new ArrayList<>(12);
        for (int i = 0; i < 12; i++) {
            ratingList.add(i, -1);
        }
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(System.currentTimeMillis());
        setContentView(R.layout.activity_upmc_dash);
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        final LinearLayout morning_questions = findViewById(R.id.morning_questions);
        final TimePicker to_bed = findViewById(R.id.bed_time);
        final TimePicker from_bed = findViewById(R.id.woke_time);
        final RadioGroup qos_sleep = findViewById(R.id.qos_sleep);
        if (cal.get(Calendar.HOUR_OF_DAY) >= Integer.parseInt(Aware.getSetting(this, Settings.PLUGIN_UPMC_CANCER_MORNING_HOUR)) && cal.get(Calendar.HOUR_OF_DAY) <= 12) {
            morning_questions.setVisibility(View.VISIBLE);

            Calendar today = Calendar.getInstance();
            today.setTimeInMillis(System.currentTimeMillis());
            today.set(Calendar.HOUR_OF_DAY, 1);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            Cursor already_answered = getContentResolver().query(Provider.Symptom_Data.CONTENT_URI, null, Provider.Symptom_Data.TIMESTAMP + " > " + today.getTimeInMillis() + " AND (" + Provider.Symptom_Data.TO_BED + " != '' OR " + Provider.Symptom_Data.FROM_BED + " !='')", null, null);
            if (already_answered != null && already_answered.getCount() > 0) {
                morning_questions.setVisibility(View.GONE);
            }
            if (already_answered != null && !already_answered.isClosed())
                already_answered.close();
        }
        final TextView pain_rating = findViewById(R.id.pain_rating);
        pain_rating.setText("?");
        SeekBar pain = findViewById(R.id.rate_pain);
        pain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                pain_rating.setText(String.valueOf(i));
                ratingList.set(0, i);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        final TextView fatigue_rating = findViewById(R.id.fatigue_rating);
        fatigue_rating.setText("?");
        SeekBar fatigue = findViewById(R.id.rate_fatigue);
        fatigue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                fatigue_rating.setText(String.valueOf(i));
                ratingList.set(1, i);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        final TextView concentrating_rating = findViewById(R.id.concentrating_rating);
        concentrating_rating.setText("?");
        SeekBar concentrating = findViewById(R.id.rate_concentrating);
        concentrating.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                concentrating_rating.setText(String.valueOf(i));
                ratingList.set(2, i);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        final TextView sad_rating = findViewById(R.id.sad_rating);
        sad_rating.setText("?");
        SeekBar sad = findViewById(R.id.rate_sad);
        sad.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                sad_rating.setText(String.valueOf(i));
                ratingList.set(3, i);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        final TextView anxious_rating = findViewById(R.id.anxious_rating);
        anxious_rating.setText("?");
        SeekBar anxious = findViewById(R.id.rate_anxious);
        anxious.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                anxious_rating.setText(String.valueOf(i));
                ratingList.set(4, i);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        final TextView breath_rating = findViewById(R.id.breath_rating);
        breath_rating.setText("?");
        SeekBar breath = findViewById(R.id.rate_breath);
        breath.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                breath_rating.setText(String.valueOf(i));
                ratingList.set(5, i);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        final TextView numb_rating = findViewById(R.id.numb_rating);
        numb_rating.setText("?");
        SeekBar numb = findViewById(R.id.rate_numb);
        numb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                numb_rating.setText(String.valueOf(i));
                ratingList.set(6, i);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        final TextView nausea_rating = findViewById(R.id.nausea_rating);
        nausea_rating.setText("?");
        SeekBar nausea = findViewById(R.id.rate_nausea);
        nausea.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                nausea_rating.setText(String.valueOf(i));
                ratingList.set(7, i);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        final TextView sleep_disturb_rating = findViewById(R.id.sleep_disturbance_rating);
        sleep_disturb_rating.setText("?");
        SeekBar sleep_disturb = findViewById(R.id.rate_sleep_disturbance);
        sleep_disturb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                sleep_disturb_rating.setText(String.valueOf(i));
                ratingList.set(8, i);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        final TextView diarrhea_rating = findViewById(R.id.diarrhea_rating);
        diarrhea_rating.setText("?");
        SeekBar diarrhea = findViewById(R.id.rate_diarrhea);
        diarrhea.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                diarrhea_rating.setText(String.valueOf(i));
                ratingList.set(9, i);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        final TextView other_rating = findViewById(R.id.other_rating);
        other_rating.setText("?");
        final TextView other_label = findViewById(R.id.lbl_other);
        other_label.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View v) {
                final Dialog other_labeler = new Dialog(UPMC.this);
                other_labeler.setTitle("Can you be more specific, please?");
                other_labeler.getWindow().setGravity(Gravity.TOP);
                other_labeler.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);

                LinearLayout editor = new LinearLayout(UPMC.this);
                editor.setOrientation(LinearLayout.VERTICAL);
                other_labeler.setContentView(editor);
                other_labeler.show();

                final EditText label = new EditText(UPMC.this);
                label.setHint("Can you be more specific, please?");
                editor.addView(label);
                label.requestFocus();
                other_labeler.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

                Button confirm = new Button(UPMC.this);
                confirm.setText("OK");
                confirm.setOnClickListener(new View.OnClickListener() {
                    @SuppressLint("SetTextI18n")
                    @Override
                    public void onClick(View v) {
                        if (label.getText().length() == 0) label.setText("Other");
                        other_label.setText(label.getText().toString());
                        other_labeler.dismiss();
                    }
                });

                editor.addView(confirm);
            }
        });

        SeekBar other = findViewById(R.id.rate_other);
        other.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                other_rating.setText(String.valueOf(i));
                ratingList.set(10, i);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @SuppressLint("SetTextI18n")
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (other_label.getText().equals("Other")) {
                    final Dialog other_labeler = new Dialog(UPMC.this);
                    other_labeler.getWindow().setGravity(Gravity.TOP);
                    other_labeler.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);

                    LinearLayout editor = new LinearLayout(UPMC.this);
                    editor.setOrientation(LinearLayout.VERTICAL);
                    other_labeler.setContentView(editor);
                    other_labeler.show();

                    final EditText label = new EditText(UPMC.this);
                    label.setHint("Can you be more specific, please?");
                    editor.addView(label);
                    label.requestFocus();
                    other_labeler.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

                    Button confirm = new Button(UPMC.this);
                    confirm.setText("OK");
                    confirm.setOnClickListener(new View.OnClickListener() {

                        @SuppressLint("SetTextI18n")
                        @Override
                        public void onClick(View v) {
                            if (label.getText().length() == 0) label.setText("Other");
                            other_label.setText(label.getText().toString());
                            other_labeler.dismiss();
                        }
                    });

                    editor.addView(confirm);
                }
            }
        });

        final Button answer_questions = findViewById(R.id.answer_questionnaire);
        final ProgressBar progressBar = findViewById(R.id.progress_bar_syms);
        final Context context = this;


        answer_questions.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View v) {
                answer_questions.setEnabled(false);
                answer_questions.setText("Saving answers..");
                Log.d(Constants.TAG, "Trig::Questionnaire");
                progressBar.setVisibility(View.VISIBLE);


                Log.d(Constants.TAG, "UPMC: Progress&Vicinity Thread starts");
                LocalBroadcastManager.getInstance(context).registerReceiver(vicinityCheckBroadcastReceiver, new IntentFilter(Constants.VICINITY_CHECK_INTENT_FILTER));
                LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(Constants.SETTING_INTENT_FILTER).putExtra(Constants.SETTINGS_EXTRA_KEY, Constants.VICINITY_CHECK));

                Handler handler = new Handler();

                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(Constants.TAG, "UPMC:: Handler is running");
//                        if (!isWatchAround) {
//                            Toast.makeText(context, "Failed to save settings. Please try again with watch around!", Toast.LENGTH_LONG).show();
//                            LocalBroadcastManager.getInstance(context).unregisterReceiver(vicinityCheckBroadcastReceiver);
//                            progressBar.setVisibility(View.GONE);
//                            answer_questions.setText("Save Answers");
//                            answer_questions.setEnabled(true);
//                        } else {
                        ContentValues answer = new ContentValues();
                        answer.put(Provider.Symptom_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                        answer.put(Provider.Symptom_Data.TIMESTAMP, System.currentTimeMillis());
                        if (morning_questions != null && morning_questions.getVisibility() == View.VISIBLE) {
                            answer.put(Provider.Symptom_Data.TO_BED, (to_bed != null) ? to_bed.getCurrentHour() + "h" + to_bed.getCurrentMinute() : "");
                            answer.put(Provider.Symptom_Data.FROM_BED, (from_bed != null) ? from_bed.getCurrentHour() + "h" + from_bed.getCurrentMinute() : "");
                            answer.put(Provider.Symptom_Data.SCORE_SLEEP, (qos_sleep != null && qos_sleep.getCheckedRadioButtonId() != -1) ? (String) ((RadioButton) findViewById(qos_sleep.getCheckedRadioButtonId())).getText() : "");
                        }
                        answer.put(Provider.Symptom_Data.SCORE_PAIN, pain_rating.getText().toString());
                        answer.put(Provider.Symptom_Data.SCORE_FATIGUE, fatigue_rating.getText().toString());
                        answer.put(Provider.Symptom_Data.SCORE_SLEEP_DISTURBANCE, sleep_disturb_rating.getText().toString());
                        answer.put(Provider.Symptom_Data.SCORE_CONCENTRATING, concentrating_rating.getText().toString());
                        answer.put(Provider.Symptom_Data.SCORE_SAD, sad_rating.getText().toString());
                        answer.put(Provider.Symptom_Data.SCORE_ANXIOUS, anxious_rating.getText().toString());
                        answer.put(Provider.Symptom_Data.SCORE_SHORT_BREATH, breath_rating.getText().toString());
                        answer.put(Provider.Symptom_Data.SCORE_NUMBNESS, numb_rating.getText().toString());
                        answer.put(Provider.Symptom_Data.SCORE_NAUSEA, nausea_rating.getText().toString());
                        answer.put(Provider.Symptom_Data.SCORE_DIARRHEA, diarrhea_rating.getText().toString());
                        answer.put(Provider.Symptom_Data.SCORE_OTHER, other_rating.getText().toString());
                        answer.put(Provider.Symptom_Data.OTHER_LABEL, other_label.getText().toString());
                        Log.d(Constants.TAG, "Trig::Questionnaire" + Integer.parseInt(pain_rating.getText().toString()));
                        getContentResolver().insert(Provider.Symptom_Data.CONTENT_URI, answer);
                        try {
                            checkSymptoms();
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                        Log.d("UPMC", "Answers:" + answer.toString());
                        Toast.makeText(getApplicationContext(), "Saved successfully.", Toast.LENGTH_LONG).show();
                        LocalBroadcastManager.getInstance(context).unregisterReceiver(vicinityCheckBroadcastReceiver);
                        finish();
//                        }
                    }
                }, 7000);
            }
        });
    }

    public BroadcastReceiver vicinityCheckBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(Constants.TAG, "UPMC:: vicinitycheck received from MessageService: " + intent.getIntExtra(Constants.VICINITY_RESULT_KEY, -1));
            if (intent.hasExtra(Constants.VICINITY_RESULT_KEY)) {
                if ((intent.getIntExtra(Constants.VICINITY_RESULT_KEY, -1) == Constants.WEAR_VICINITY_CHECK_FAILED)
                        || (intent.getIntExtra(Constants.VICINITY_RESULT_KEY, -1) == Constants.WEAR_NOT_IN_RANGE)) {
                    isWatchAround = false;
                } else if (intent.getIntExtra(Constants.VICINITY_RESULT_KEY, -1) == Constants.WEAR_IN_RANGE) {
                    isWatchAround = true;
                }
            }

        }
    };

    public void checkSymptoms() throws UnsupportedEncodingException {
        boolean badSymps = false;
        for (Integer i : ratingList) {
            if (i >= 7) {
                badSymps = true;
                break;
            }
        }
        String data = null;
        if (badSymps) {
            data = URLEncoder.encode("result", "UTF-8")
                    + "=" + URLEncoder.encode(CASE2, "UTF-8");
            Log.d(Constants.TAG, "Bad Symptoms");
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Constants.SYMPTOMS_INTENT_FILTER).putExtra(Constants.SYMPTOMS_KEY, Constants.SYMPTOMS_1));
        } else {
            data = URLEncoder.encode("result", "UTF-8")
                    + "=" + URLEncoder.encode(CASE1, "UTF-8");
            Log.d(Constants.TAG, "Good Symptoms");
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Constants.SYMPTOMS_INTENT_FILTER).putExtra(Constants.SYMPTOMS_KEY, Constants.SYMPTOMS_0));
        }
        new PostData().execute("http://localhost:8080/storeData.php", data);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_upmc, menu);

        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item.getTitle().toString().equalsIgnoreCase("Sync") && !Aware.isStudy(getApplicationContext())) {
                item.setVisible(false);
            }
        }

        return true;
    }

    @SuppressLint("SetTextI18n")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onResume();
            return true;
        }

        String title = item.getTitle().toString();
        if (title.equalsIgnoreCase("Settings")) {
            loadSchedule(false);
            return true;
        } else if (title.equalsIgnoreCase("Participant")) {

            @SuppressLint("InflateParams") View participantInfo = getLayoutInflater().inflate(R.layout.participant_info, null);

            TextView uuid = participantInfo.findViewById(R.id.device_id);
            uuid.setText("UUID: " + Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));

            final EditText device_label = participantInfo.findViewById(R.id.device_label);
            device_label.setText(Aware.getSetting(this, Aware_Preferences.DEVICE_LABEL));

            AlertDialog.Builder mBuilder = new AlertDialog.Builder(this);
            mBuilder.setTitle("UPMC Participant");
            mBuilder.setView(participantInfo);
            mBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (device_label.getText().length() > 0 && !device_label.getText().toString().equals(Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_LABEL))) {
                        Aware.setSetting(getApplicationContext(), Aware_Preferences.DEVICE_LABEL, device_label.getText().toString());
                    }
                    dialog.dismiss();
                }
            });
            mBuilder.create().show();

            return true;
        } else if (!isTimeInitialized()) {
            if (title.equalsIgnoreCase("Demo Watch")) {
//            Intent walking = new Intent(this, UPMC_Motivation.class);
//            walking.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            startActivity(walking);
                Log.d(Constants.TAG, "UPMC:Demo Watch happened");
                final Context context = this;
                final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
                final AlertDialog.Builder dialogBuilder1 = new AlertDialog.Builder(context);
                final View view = new ProgressBar(UPMC.this);
                dialogBuilder1.setView(view);
                dialogBuilder1.setCancelable(false);
                item.setTitle("Stop Demo");
                dialogBuilder.setTitle("Demo")
                        .setMessage("This is a walkthrough of the prompts that you will receive, when you are inactive")
                        .setCancelable(false)
                        .setPositiveButton("Proceed", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                final AlertDialog diag = dialogBuilder1.create();
                                diag.show();
                                Handler handler = new Handler();
                                if (!isMyServiceRunning(MessageService.class)) {
                                    startService(new Intent(getApplicationContext(), MessageService.class).putExtra(Constants.COMM_KEY_MSGSERVICE, Constants.DEMO_MODE));
                                    Log.d(Constants.TAG, "UPMC: Starting demo Message Service");
                                } else
                                    Log.d(Constants.TAG, "UPMC: Starting demo Service already running");
                                handler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        LocalBroadcastManager.getInstance(context).registerReceiver(vicinityCheckBroadcastReceiver, new IntentFilter(Constants.VICINITY_CHECK_INTENT_FILTER));
                                        LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(Constants.SETTING_INTENT_FILTER).putExtra(Constants.SETTINGS_EXTRA_KEY, Constants.VICINITY_CHECK));
                                    }
                                }, 5000);
                                handler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (!isWatchAround) {
                                            Toast.makeText(getApplicationContext(), "Failed: Could not find your watch ", Toast.LENGTH_LONG).show();
                                            diag.dismiss();
                                        } else {
                                            Toast.makeText(getApplicationContext(), "Success: Demo will start in 5 seconds ", Toast.LENGTH_LONG).show();
                                            Toast.makeText(getApplicationContext(), "Save Answers will be disabled during demo. Please cancel demo to enable", Toast.LENGTH_SHORT).show();
                                            LocalBroadcastManager.getInstance(context).unregisterReceiver(vicinityCheckBroadcastReceiver);
                                            diag.dismiss();
                                        }
                                    }
                                }, 17000);
                            }
                        })
                        .create();
                dialogBuilder.create().show();
                dialogBuilder1.setTitle("Preparing Demo...")
                        .setView(view);
                return true;
            } else if (title.equalsIgnoreCase("Stop Demo")) {
                LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Constants.SETTING_INTENT_FILTER).putExtra(Constants.SETTINGS_EXTRA_KEY, Constants.KILL_DEMO));
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (isMyServiceRunning(MessageService.class)) {
                            Intent intent = new Intent(getApplicationContext(), MessageService.class);
                            stopService(intent);
                        } else {
                            Toast.makeText(getApplicationContext(), "Demo has been stopped", Toast.LENGTH_LONG).show();
                        }
                    }
                }, 2000);

                item.setTitle("Demo Watch");
                item.setEnabled(false);
            } else if (title.equalsIgnoreCase("Sync")) {
                sendBroadcast(new Intent(Aware.ACTION_AWARE_SYNC_DATA));
                Log.d(Constants.TAG, "UPMC:Sync happened");
                return true;
            } else if (title.equalsIgnoreCase("Demo ESM")) {
                Log.d(Constants.TAG, "UPMC:DemoESM happened");
                Intent intent = new Intent(this, DemoESM.class);
                startActivity(intent);
                item.setEnabled(false);
                return true;

            }
        } else {
            Toast.makeText(getApplicationContext(), "Unable to start during study", Toast.LENGTH_LONG).show();
        }

        return super.onOptionsItemSelected(item);
    }

    private class PostData extends AsyncTask<String, Void, Void> {


        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
        }

        @Override
        protected Void doInBackground(String... strings) {
            BufferedReader reader = null;
            String text = "";
            // Send data
            try {
                // Defined URL  where to send data
                URL url = new URL(strings[0]);
                // Send POST data request
                URLConnection conn = url.openConnection();
                conn.setDoOutput(true);
                OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
                wr.write(strings[1]);
                wr.flush();
                // Get the server response
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                // Read Server Response
                while ((line = reader.readLine()) != null) {
                    // Append server response in string
                    sb.append(line + "\n");
                }
                text = sb.toString();
                Log.d(Constants.TAG, "Server response: " + text);
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                try {
                    reader.close();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }


            return null;

        }
    }
}
