package com.example.robotmobilny;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.UUID;

// service to write and read to: 0000ffe0-0000-1000-8000-00805f9b34fb
// characteristic to write and read to: 0000ffe1-0000-1000-8000-00805f9b34fb

public class BleService extends Service {

    private final static String TAG = BleService.class.getSimpleName();

    public final static String ACTION_GATT_CONNECTED =
            "com.example.robotmobilny.bluetooth.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.robotmobilny.bluetooth.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.robotmobilny.bluetooth.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.robotmobilny.bluetooth.EXTRA_DATA";

    // Binder given to clients
    private final IBinder binder = new LocalBinder();

    private BluetoothGatt bluetoothGatt;
    BluetoothGattCharacteristic characteristic;

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                Log.i(TAG, "Attempting to start service discovery:" +
                        bluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            characteristic = gatt
                    .getService(UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"))
                    .getCharacteristic(UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"));
            bluetoothGatt.setCharacteristicNotification(characteristic, true);
        }

        /*@Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(characteristic);
            }
        }*/

        @Override
        // Characteristic notification
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(characteristic);
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(BleService.ACTION_DATA_AVAILABLE);
        final byte[] data = characteristic.getValue();
        if (data != null && data.length > 0) {
            final StringBuilder stringBuilder = new StringBuilder(data.length);
            for(byte byteChar : data)
                stringBuilder.append(byteChar);

            String message = new String(data);
            intent.putExtra(EXTRA_DATA, message);
        }
        sendBroadcast(intent);
    }

    void send(String query) {
        characteristic.setValue(query);
        boolean status = bluetoothGatt.writeCharacteristic(characteristic);
        Log.i(TAG, String.valueOf(status));
    }

    @Override
    public IBinder onBind(Intent intent) {
        BluetoothDevice bluetoothDevice = intent.getParcelableExtra(AccessBleActivity.intentDeviceKey);
        bluetoothGatt = bluetoothDevice.connectGatt(this, false, gattCallback);
        return binder;
    }

    @Override
    public void onDestroy() {
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        super.onDestroy();
    }

    class LocalBinder extends Binder {
        BleService getService() {
            // Return this instance of LocalService so clients can call public methods
            return BleService.this;
        }
    }
}
