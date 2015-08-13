package com.example.blesample2;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import android.os.Message;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.os.Handler;
import android.widget.TextView;
import android.view.View;
import java.util.UUID;
import android.graphics.Color;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

public class MainActivity extends Activity implements BluetoothAdapter.LeScanCallback {
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothManager mBluetoothManager;
    private Handler mHandler;
    private TextView mStatusText;
    private Handler mRcvDataHandler;
    private TextView mRcvDataText;
    private BleStatus mStatus = BleStatus.DISCONNECTED;
    static final int REQUEST_ENABLE_BT = 0;
    private static final long SCAN_PERIOD = 10000; /** BLE 機器検索のタイムアウト(ミリ秒) */
    private BluetoothGatt mBluetoothGatt;
    private boolean mIsBluetoothEnable = false;

    private static final String TAG = "BLE SAMPLE2";
    private static final String DEVICE_NAME = "BLE HomeSensor";
    private static final String DEVICE_SENSOR_SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"; /** 対象のサービスUUID(デバイスにより変える必要がある) */
    private static final String DEVICE_RX_CHARACTERISTIC_UUID = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"; /** 対象のキャラクタリスティックUUID(デバイスにより変える必要がある) */
    private static final String DEVICE_TX_CHARACTERISTIC_UUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"; /** 対象のキャラクタリスティックUUID(デバイスにより変える必要がある) */
    private static final String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb"; /** キャラクタリスティック設定UUID(これは固定のようだ) */


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        mIsBluetoothEnable = false;
        
        Log.d(TAG, "get adapter");
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = manager.getAdapter();
         
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Log.d(TAG, "no adapter");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        else {
            Log.d(TAG, "found adapter");
        }

        findViewById(R.id.btn_connect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connect();
            }
        });
        findViewById(R.id.btn_disconnect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnect();
            }
        });

        findViewById(R.id.btn_send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send();
            }
        });

        mStatusText = (TextView)findViewById(R.id.text_status);
        mRcvDataText = (TextView)findViewById(R.id.rcv_data);

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                mStatusText.setText(((BleStatus) msg.obj).name());
            }
        };
        mRcvDataHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
            	mRcvDataText.setText((String)msg.obj);
            }
        };

    }

    /** BLE機器を検索する */
    private void connect() {
        Log.d(TAG, "start searching");
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // タイムアウト
                mBluetoothAdapter.stopLeScan(MainActivity.this);
            }
        }, SCAN_PERIOD);
     
        // スキャン開始
        mBluetoothAdapter.startLeScan(this);
    }

    /** BLE 機器との接続を解除する */
    private void disconnect() {
        Log.d(TAG, "start disconnecting");
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
            setStatus(BleStatus.CLOSED);
        }
        mIsBluetoothEnable = false;
    }

    private void send() {
        if (mIsBluetoothEnable) {
            Log.d(TAG, "send");
            BluetoothGattService myService = mBluetoothGatt.getService(UUID.fromString(DEVICE_SENSOR_SERVICE_UUID));
            BluetoothGattCharacteristic myChar = myService.getCharacteristic(UUID.fromString(DEVICE_TX_CHARACTERISTIC_UUID));
            myChar.setValue("ABC");
            mBluetoothGatt.writeCharacteristic(myChar); 
        }
    }

    private void setStatus(BleStatus status) {
        mStatus = status;
        mHandler.sendMessage(status.message());
    }

    private void setRcvDataText(String rcvString) {
    	Message msg = Message.obtain();
    	msg.obj = rcvString;
    	mRcvDataHandler.sendMessage(msg);
    }

    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
        Log.d(TAG, "device found: " + device.getName());
        if (DEVICE_NAME.equals(device.getName())) {
            Log.d(TAG, "match device name");
            // 機器名が "SensorTag" であるものを探す
     
            // 機器が見つかればすぐにスキャンを停止する
            mBluetoothAdapter.stopLeScan(this);
     
            // 機器への接続を試みる
            mBluetoothGatt = device.connectGatt(getApplicationContext(), false, mBluetoothGattCallback);
     
            // 接続の成否は mBluetoothGattCallback で受け取る
        }
    }

    // 接続完了や機器からのデータ受信はここで処理する
    private final BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        	// �@：connectGatt()の後でここに来る
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // GATTへ接続成功
                Log.d(TAG, "gatt connection success");
                // サービスを検索する
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // GATT通信から切断された
                setStatus(BleStatus.DISCONNECTED);
                mBluetoothGatt = null;
                mIsBluetoothEnable = false;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        	// �A：discoverServices()の後でここに来る
            Log.d(TAG, "check deiscovered");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "gatt success");

                /* test code
                for (BluetoothGattService service : gatt.getServices()) {
                	Log.d(TAG, "found uuid:" + service.getUuid().toString());
                }
                */

                BluetoothGattService service = gatt.getService(UUID.fromString(DEVICE_SENSOR_SERVICE_UUID));
                if (service == null) {
                    // サービスが見つからなかった
                    Log.d(TAG, "no service");
                    setStatus(BleStatus.SERVICE_NOT_FOUND);
                    mIsBluetoothEnable = false;
                } else {
                    // サービスを見つけた
                    Log.d(TAG, "service found");
                    setStatus(BleStatus.SERVICE_FOUND);

                    BluetoothGattCharacteristic characteristic =
                            service.getCharacteristic(UUID.fromString(DEVICE_RX_CHARACTERISTIC_UUID));

                    if (characteristic == null) {
                        // キャラクタリスティックが見つからなかった
                        Log.d(TAG, "characteristic not found");
                        setStatus(BleStatus.CHARACTERISTIC_NOT_FOUND);
                        mIsBluetoothEnable = false;
                    } else {
                        // キャラクタリスティックを見つけた
                        Log.d(TAG, "characteristic found");

                        // Notification を要求する
                        boolean registered = gatt.setCharacteristicNotification(characteristic, true);

                        // Characteristic の Notification 有効化
                        Log.d(TAG, "enable notification");
                        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                                UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                        Log.d(TAG, "set descriptor value");
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        Log.d(TAG, "write discriptor");
                        gatt.writeDescriptor(descriptor);

                        if (registered) {
                            // Characteristics通知設定完了
                            Log.d(TAG, "enable notification complete");
                            setStatus(BleStatus.NOTIFICATION_REGISTERED);
                            mIsBluetoothEnable = true;
                        } else {
                            Log.d(TAG, "enable notification incomplete");
                            setStatus(BleStatus.NOTIFICATION_REGISTER_FAILED);
                            mIsBluetoothEnable = false;
                        }
                    }
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
        	// �Bデータ到着時にここが実行される
            Log.d(TAG, "onCharacteristicRead: " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // READ成功
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            // Characteristicの値更新通知
            Log.d(TAG, "onCharacteristicChanged");
            if (DEVICE_RX_CHARACTERISTIC_UUID.equals(characteristic.getUuid().toString())) {
                final byte[] data = characteristic.getValue();
                String result = new String(data);
                Log.d(TAG, "Received Data:" + result);
                setRcvDataText(result);
                boolean left = (0 < (data[0] & 0x02));
                boolean right = (0 < (data[0] & 0x01));
                updateButtonState(left, right);
            }
        }
    };

    private void updateButtonState(final boolean left, final boolean right) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                View leftView = findViewById(R.id.left);
                View rightView = findViewById(R.id.right);
                leftView.setBackgroundColor( (left ? Color.BLUE : Color.TRANSPARENT) );
                rightView.setBackgroundColor( (right ? Color.BLUE : Color.TRANSPARENT) );
            }
        });
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private enum BleStatus {
        DISCONNECTED,
        SCANNING,
        SCAN_FAILED,
        DEVICE_FOUND,
        SERVICE_NOT_FOUND,
        SERVICE_FOUND,
        CHARACTERISTIC_NOT_FOUND,
        NOTIFICATION_REGISTERED,
        NOTIFICATION_REGISTER_FAILED,
        CLOSED
        ;
        public Message message() {
            Message message = new Message();
            message.obj = this;
            return message;
        }
    }
    
}
