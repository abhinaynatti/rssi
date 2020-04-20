package com.bignerdranch.android.bluetoothtestbed.client;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import com.bignerdranch.android.bluetoothtestbed.R;
import com.bignerdranch.android.bluetoothtestbed.databinding.ActivityClientBinding;
import com.bignerdranch.android.bluetoothtestbed.databinding.ViewGattServerBinding;
import com.bignerdranch.android.bluetoothtestbed.util.BluetoothUtils;
import com.bignerdranch.android.bluetoothtestbed.util.StringUtils;

import org.altbeacon.beacon.BeaconManager;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.bignerdranch.android.bluetoothtestbed.Constants.SCAN_PERIOD;
import static com.bignerdranch.android.bluetoothtestbed.Constants.SERVICE_UUID;

public class ClientActivity extends AppCompatActivity implements GattClientActionListener {

    private static final String TAG = "abhi";

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_FINE_LOCATION = 2;

    private ActivityClientBinding mBinding;

    private boolean mScanning;
    private Handler mHandler;
    private Handler mLogHandler;
    private Map<String, BluetoothDevice> mScanResults;
    private Map<String, Double> DeviceDistances;
    private String extra_data;
    private boolean mConnected;
    private boolean mTimeInitialized;
    private boolean mEchoInitialized;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private ScanCallback mScanCallback;
    private BluetoothGatt mGatt;
    int powerlvl;
    int rssi;
    double second_distance;
    private BeaconManager beaconManager;
    private int total_people;
    // Lifecycle


    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mLogHandler = new Handler(Looper.getMainLooper());

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        requestPermissions(new String[] {Manifest.permission.ACCESS_COARSE_LOCATION},1);
        requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION},2);

        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_client);
        startScan();
        @SuppressLint("HardwareIds")
        String deviceInfo = "Device Info"
                + "\nName: " + mBluetoothAdapter.getName()
                + "\nAddress: " + mBluetoothAdapter.getAddress();
        mBinding.clientDeviceInfoTextView.setText(deviceInfo);
        mBinding.startScanningButton.setOnClickListener(v -> startScan());
        mBinding.stopScanningButton.setOnClickListener(v -> stopScan());
        mBinding.sendMessageButton.setOnClickListener(v -> sendMessage());
        mBinding.disconnectButton.setOnClickListener(v -> disconnectGattServer());
        mBinding.viewClientLog.clearLogButton.setOnClickListener(v -> clearLogs());
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Check low energy support
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            // Get a newer device
            logError("No LE Support.");
            finish();
        }
    }

    // Scanning

    private void startScan() {
        if (!hasPermissions() || mScanning) {
            return;
        }

        disconnectGattServer();

        mBinding.serverListContainer.removeAllViews();

        mScanResults = new HashMap<>();
        DeviceDistances = new HashMap<>();
        mScanCallback = new BtleScanCallback(mScanResults,DeviceDistances);

        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

        // Note: Filtering does not work the same (or at all) on most devices. It also is unable to
        // search for a mask or anything less than a full UUID.
        // Unless the full UUID of the server is known, manual filtering may be necessary.
        // For example, when looking for a brand of device that contains a char sequence in the UUID


//        String uniqueId2 =  "f5e70cac-60ef-4e5c-be90-d4f0dcded9d5";
//        public static String SERVICE_STRING = "7D2EA28A-F7BD-485A-BD9D-92AD6ECFE93E";
 
        ScanFilter scanFilter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(SERVICE_UUID))
                .build();
        List<ScanFilter> filters = new ArrayList<>();
        filters.add(scanFilter);

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build();

        mBluetoothLeScanner.startScan(filters, settings, mScanCallback);

        mHandler = new Handler();
        mHandler.postDelayed(this::stopScan, SCAN_PERIOD);

        mScanning = true;
        log("Started scanning.");
    }

    private void stopScan() {
        if (mScanning && mBluetoothAdapter != null && mBluetoothAdapter.isEnabled() && mBluetoothLeScanner != null) {
            mBluetoothLeScanner.stopScan(mScanCallback);
            scanComplete();
        }

        mScanCallback = null;
        mScanning = false;
        mHandler = null;
        log("Stopped scanning.");
    }

    private void scanComplete() {
        if (mScanResults.isEmpty()) {
            return;
        }
        double first_distance = distanceMeasure(rssi,powerlvl);
//        Log.v(TAG,"rssi is "+ rssi);
//        Log.v(TAG,"POWERLVL IS "+ powerlvl);
//        Log.v(TAG,"distance is" + distanceMeasure(rssi,powerlvl));
//        Log.v(TAG,"second distance is "+ second_distance);
//        Toast.makeText(this,"distances are " + first_distance + "\t," + second_distance,Toast.LENGTH_LONG).show();
//        log("distances are " + first_distance + "\t" + second_distance);
        log("Distance  =" + first_distance);
        Toast.makeText(this,"distance = " + first_distance,Toast.LENGTH_LONG).show();

//        log("rssi and powerlvl are " + rssi + "\t" + powerlvl);
//        Toast.makeText(this,"rssi and powerlvl are " + rssi + "\t" + powerlvl,Toast.LENGTH_LONG).show();
//        Log.v("abhi","name is " + extra_data);


        if( first_distance <=3 ){
            Toast.makeText(this,"Not maintaining Social Distance Parameters. You are " + (int)first_distance + "m away from somebody",Toast.LENGTH_LONG).show();
        }

        if(first_distance >=3 && first_distance <= 6){
            Toast.makeText(this,"Not safe. A person is " + (int)first_distance + "m away",Toast.LENGTH_LONG).show();
        }

        if(first_distance >= 6){
            Toast.makeText(this,"Maintaining social distance parameters",Toast.LENGTH_LONG).show();
        }

        total_people = mScanResults.size();
        log("total people aroudn you  equals to" + total_people);
        for (String deviceAddress : mScanResults.keySet()) {
            Log.v("address",deviceAddress);
            String ServerAddress = "48:01:C5:9E:D8:1D";
            BluetoothDevice device = mScanResults.get(deviceAddress);


            if(deviceAddress.equals(ServerAddress)){
                Log.v("address", "yup it matches");
                connectDevice(device);
            }
            else {


                GattServerViewModel viewModel = new GattServerViewModel(device);
                ViewGattServerBinding binding = DataBindingUtil.inflate(LayoutInflater.from(this),
                        R.layout.view_gatt_server,
                        mBinding.serverListContainer,
                        true);
                binding.setViewModel(viewModel);
                binding.connectGattServerButton.setOnClickListener(v -> connectDevice(device));
            }
        }



        for (double dist : DeviceDistances.values()){
            log("distance = " + dist);
        }

    }

    private boolean hasPermissions() {
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            requestBluetoothEnable();
            return false;
        } else if (!hasLocationPermissions()) {
            requestLocationPermission();
            return false;
        }
        return true;
    }

    private void requestBluetoothEnable() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        log("Requested user enables Bluetooth. Try starting the scan again.");
    }

    private boolean hasLocationPermissions() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION);
        log("Requested user enable Location. Try starting the scan again.");
    }

    private class BtleScanCallback extends ScanCallback {

        private Map<String, BluetoothDevice> mScanResults;
        private Map<String, Double>  DeviceDistances;


        BtleScanCallback(Map<String, BluetoothDevice> scanResults, Map<String,Double> deviceDistances ) {
            mScanResults = scanResults;
            DeviceDistances = deviceDistances;
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
             addScanResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                addScanResult(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            logError("BLE Scan Failed with code " + errorCode);
        }

        private void addScanResult(ScanResult result) {
            ScanRecord mscanRecord = result.getScanRecord();
//            result.
            rssi = result.getRssi();
            powerlvl = mscanRecord.getTxPowerLevel();
            powerlvl = -63;
            Double distance_device = distanceMeasure(rssi,powerlvl);
            byte [] recordinbytes = mscanRecord.getBytes();

            BluetoothDevice device = result.getDevice();
            String deviceAddress = device.getAddress();
            mScanResults.put(deviceAddress, device);
            DeviceDistances.put(deviceAddress,distance_device);
        }
    }

    // Gatt connection


    private void connectDevice(BluetoothDevice device) {
        log("Connecting to " + device.getAddress());
        Toast.makeText(this, "Connecting to server", Toast.LENGTH_SHORT).show();
        GattClientCallback gattClientCallback = new GattClientCallback(this);
        mGatt = device.connectGatt(this, false, gattClientCallback);
    }

    // Messaging

    private void sendMessage() {
        if (!mConnected || !mEchoInitialized) {
            return;
        }

        BluetoothGattCharacteristic characteristic = BluetoothUtils.findEchoCharacteristic(mGatt);
        if (characteristic == null) {
            logError("Unable to find echo characteristic.");
            disconnectGattServer();
            return;
        }

        String message = mBinding.messageEditText.getText().toString();
        log("Sending message: " + message);

        byte[] messageBytes = StringUtils.bytesFromString(message);
        if (messageBytes.length == 0) {
            logError("Unable to convert message to bytes");
            return;
        }

        characteristic.setValue(messageBytes);
        boolean success = mGatt.writeCharacteristic(characteristic);
        if (success) {
//            log("Wrote: " + StringUtils.byteArrayInHexFormat(messageBytes));
        } else {
            logError("Failed to write data");
        }
    }

    private void requestTimestamp() {
        if (!mConnected || !mTimeInitialized) {
            return;
        }

        BluetoothGattCharacteristic characteristic = BluetoothUtils.findTimeCharacteristic(mGatt);
        if (characteristic == null) {
            logError("Unable to find time charactaristic");
            return;
        }

        mGatt.readCharacteristic(characteristic);
    }

    // Logging

    private void clearLogs() {
        mLogHandler.post(() -> mBinding.viewClientLog.logTextView.setText(""));
    }

    // Gat Client Action Listener

    @Override
    public void log(String msg) {
        Log.d(TAG, msg);
        mLogHandler.post(() -> {
            mBinding.viewClientLog.logTextView.append(msg + "\n");
            mBinding.viewClientLog.logScrollView.post(() -> mBinding.viewClientLog.logScrollView.fullScroll(View.FOCUS_DOWN));
        });
    }

    @Override
    public void logError(String msg) {
        log("Error: " + msg);
    }

    @Override
    public void setConnected(boolean connected) {
        mConnected = connected;
    }

    @Override
    public void initializeTime() {
        mTimeInitialized = true;
    }

    @Override
    public void initializeEcho() {
        mEchoInitialized = true;
    }

    @Override
    public void disconnectGattServer() {
        log("Closing Gatt connection");
        clearLogs();
        mConnected = false;
        mEchoInitialized = false;
        mTimeInitialized = false;
        if (mGatt != null) {
            mGatt.disconnect();
            mGatt.close();
        }
    }

    public double distanceMeasure(int rssi,int txpowerlvl){
        if ( rssi == 0) { ;
            return (double) -1.0;
        }

        double ratio = rssi*1.0/txpowerlvl;

        if (ratio < 1.0) {
            return Math.pow(ratio,10);
        }

        else {


            double distance = (0.89976)*Math.pow(ratio,7.7095) + 0.111;
            second_distance=0.42093*Math.pow(ratio,6.9476)+0.54992;

            return distance;

        }
    }
}
