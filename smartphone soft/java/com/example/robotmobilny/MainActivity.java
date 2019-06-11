package com.example.robotmobilny;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQ_CODE_SPEECH_INPUT = 100;

    BleService service;
    boolean bound;

    private ImageButton microphone;
    private ImageView connectionStatus;

    private MyReceiver myReceiver;
    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder iBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            BleService.LocalBinder binder = (BleService.LocalBinder) iBinder;
            service = binder.getService();
            bound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            bound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        // Start BLE service using intent from previous activity which charges it with
        // bluetooth device that we will connect to.
        BluetoothDevice robotBt = getIntent().getParcelableExtra(AccessBleActivity.intentDeviceKey);
        Intent serviceIntent = new Intent(this, BleService.class);
        serviceIntent.putExtra(AccessBleActivity.intentDeviceKey, robotBt);
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);

        microphone = findViewById(R.id.microphone);
        connectionStatus = findViewById(R.id.connection_status_indicator);

        microphone.setOnClickListener(v -> startVoiceInput());
    }

    void updateConnectionIndicator(boolean state) {
        if (state) {
            connectionStatus.setImageDrawable(getDrawable(R.drawable.green_dot));
        } else {
            connectionStatus.setImageDrawable(getDrawable(R.drawable.red_dot));
        }
    }

    @Override
    protected void onStart() {
        myReceiver = new MyReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BleService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BleService.ACTION_DATA_AVAILABLE);
        registerReceiver(myReceiver, intentFilter);
        super.onStart();
    }

    @Override
    protected void onStop() {
        unregisterReceiver(myReceiver);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (bound) {
            unbindService(connection);
        }
        super.onDestroy();
    }

    private void setReadyForNewVoiceCommand(boolean flag) {
        //if (flag) {
        //    microphone.setVisibility(View.VISIBLE);
        //} else { microphone.setVisibility(View.GONE); }
    }

    private void startVoiceInput() {
        String instruction = this.getString(R.string.voice_extra_prompt);

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, instruction);
        startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_CODE_SPEECH_INPUT) {
            if (resultCode == RESULT_OK && null != data) {
                // let's create
                ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                String command = result.get(0);
                String query = createQuery(command);
                service.send(query);
                // Disable input until our toy responses job is done.
                //setReadyForNewVoiceCommand(false);
            }
        }
    }

    private String createQuery(String cmd) {
        String voice = cmd.toLowerCase();
        StringBuilder stringBuilder = new StringBuilder();

        char divider = ';';

        // 'M' at the beginning for ride
        stringBuilder.append('M');

        if (voice.contains("przodu") || voice.contains("prosto")) { stringBuilder.append('T'); }
        if (voice.contains("prawo") || voice.contains("prawej")) { stringBuilder.append('R'); }
        if (voice.contains("tyłu") || voice.contains("tył")) { stringBuilder.append('B'); }
        if (voice.contains("lewo") || voice.contains("lewej")) { stringBuilder.append('L'); }

        stringBuilder.append(divider);

        // Let's find distance now.
        int indexOfCm = voice.indexOf("cm");
        int indexOfDistance = voice.substring(0, indexOfCm - 1).lastIndexOf(' ');
        String distance = voice.substring(indexOfDistance + 1, indexOfCm - 1);

        stringBuilder.append(distance);
        stringBuilder.append(divider);

        int speed = 255; // PWM value
        stringBuilder.append(speed);
        stringBuilder.append(divider);

        stringBuilder.append('\n');

        return stringBuilder.toString();
    }

    class MyReceiver extends BroadcastReceiver {
        // Due to construction of the robot's bluetooth module and programming of micro controller,
        // it shares the same characteristic to read and write, so method below receives every
        // message that phone sends or receives through bluetooth.
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BleService.ACTION_GATT_CONNECTED.equals(action)) {
                updateConnectionIndicator(true);
            } else if (BleService.ACTION_GATT_DISCONNECTED.equals(action)) {
                updateConnectionIndicator(false);
            } else if (BleService.ACTION_DATA_AVAILABLE.equals(action)) {
                String data = intent.getStringExtra(BleService.EXTRA_DATA);
                if (data.startsWith("F")) {
                    //setReadyForNewVoiceCommand(true);
                } else {
                    Toast.makeText(getApplicationContext(), data, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}
