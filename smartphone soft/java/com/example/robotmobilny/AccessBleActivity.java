package com.example.robotmobilny;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collections;
import java.util.List;

import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_FIRST_MATCH;

// BT unique identifier: 00:15:86:13:df:f3
// credits: https://www.flaticon.com/free-icon/microphone_1540761

public class AccessBleActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 101;
    private static final int REQ_LOCATION_COARSE = 102;
    private static final String robotMAC = "00:15:86:13:DF:F3";

    public static final String intentDeviceKey = "DEVICE";

    private BluetoothAdapter bluetoothAdapter;
    private ScanCallback scanCallback;

    private TextView locationPermissionDenied;
    private RelativeLayout loadingPanel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.access_ble_activity);
        locationPermissionDenied = findViewById(R.id.no_location);
        loadingPanel = findViewById(R.id.loadingPanel);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_LONG).show();
            finish();
        }

        final boolean[] found = {false};
        // Define callback when something happens with BLE.
        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);

                if (found[0]) return;

                BluetoothDevice device = result.getDevice();
                Intent intent = new Intent(AccessBleActivity.this, MainActivity.class);
                intent.putExtra(intentDeviceKey, device);
                startActivity(intent);
                found[0] = true;
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Starts whole procedure from turing on location service to finding robot's bt.
        askForLocationPermission();
    }

    // handles asking for permissions for location in order to make BLE scan for devices.
    void askForLocationPermission() {
        // BLE needs permission to location, without it scanning for devices is impossible.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission to location is not granted, ask for it here,
            // but first check if user didn't press 'never' previous time.
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION)) {
                // Explain user why should you enable location.
                String explanation = this.getString(R.string.explanation_location_permissions);
                Toast.makeText(this, explanation, Toast.LENGTH_LONG).show();

                // tell to user that he needs to allow location permission for the app in order to use BLE
                locationPermissionDenied.setVisibility(View.VISIBLE);

            } else {
                // No explanation needed; request the permission
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        REQ_LOCATION_COARSE);
            }
        } else {
            // Location is granted, proceed with BLE.
            enableBT();
        }
    }

    void enableBT() {
        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            lookForDevice();
        }
    }

    private void lookForDevice() {
        // Enable loading panel so user can know something is happening.
        loadingPanel.setVisibility(View.VISIBLE);

        ScanFilter.Builder filterBuilder = new ScanFilter.Builder();
        filterBuilder.setDeviceAddress(robotMAC);

        ScanSettings.Builder settingsBuilder = new ScanSettings.Builder();
        settingsBuilder.setNumOfMatches(1);
        //settingsBuilder.setScanMode(ScanSettings.SCAN_MODE_BALANCED);

        ScanFilter filter = filterBuilder.build();
        ScanSettings settings = settingsBuilder.build();

        BtFinder finder = new BtFinder(bluetoothAdapter);
        finder.scanLeDevice(Collections.singletonList(filter), settings, scanCallback);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // If request is cancelled, the result arrays are empty.
        if (requestCode == REQ_LOCATION_COARSE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // permission was granted, yay! Enable BT and proceed.
                enableBT();
            } else {
                // permission denied, quit unless user marked "don't show again"
                // on location permissions prompt
                locationPermissionDenied.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                lookForDevice();
            } else {
                // User didn't want to turn bluetooth ON.
                String explanation = this.getString(R.string.explanation_bluetooth_enable);
                Toast.makeText(this, explanation, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    class BtFinder {

        // Stops scanning after 10 seconds.
        private static final long SCAN_PERIOD = 6000;

        private BluetoothLeScanner bluetoothLeScanner;
        private Handler handler;

        BtFinder(BluetoothAdapter adapter) {
            bluetoothLeScanner = adapter.getBluetoothLeScanner();
            handler = new Handler();
        }

        void scanLeDevice(List<ScanFilter> filters,
                          ScanSettings scanSettings,
                          ScanCallback leScanCallback) {

            // Stops scanning after a pre-defined scan period.
            handler.postDelayed(() -> {
                bluetoothLeScanner.stopScan(leScanCallback);
                runOnUiThread(() -> loadingPanel.setVisibility(View.GONE));
                }, SCAN_PERIOD);

            // Run scanning for BLE devices.
            bluetoothLeScanner.startScan(filters, scanSettings, leScanCallback);
        }

    }
}