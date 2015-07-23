package rccarcontroller.hackathon.umass.rccarcontroller;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import java.util.Arrays;
import java.util.Set;

public class MainActivity extends ActionBarActivity implements
        ForwardMotionExecutor.SpeedChangeListener,
        SpacialSensorListener.DirectionChangedListener{

    private static final int REQUEST_ENABLE_BT = 1;

    private SensorManager sensorManager;
    private Sensor magneticField;
    private Sensor gravField;
    private Sensor stepSensor;

    private BluetoothAdapter mBluetoothAdapter;
    private ArrayAdapter<String> mArrayAdapter;
    private String macAddress;

    Button forwardButton;
    Button backwardButton;
    Button toggleButton;
    Button stepButton;
    TextView speedometer;

    private boolean gyroMode;
    private boolean stepMode;

    private ForwardMotionExecutor forwardMotionExecutor;
    private SpacialSensorListener spacialSensorListener;
    private BluetoothTransmitter bluetoothTransmitter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        forwardButton = (Button) findViewById(R.id.forward);
        backwardButton = (Button) findViewById(R.id.backward);
        toggleButton = (Button) findViewById(R.id.toggleMode);
        stepButton = (Button) findViewById(R.id.stepMode);
        speedometer = (TextView) findViewById(R.id.speedometer);

        forwardMotionExecutor = new ForwardMotionExecutor(new Handler(), this);
        spacialSensorListener = new SpacialSensorListener(forwardMotionExecutor, this);

        // Default to begin in "Drive" mode.
        gyroMode = false;
        spacialSensorListener.setGyroMode(gyroMode);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth.
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        mArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        magneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        gravField = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);

        sensorManager.registerListener(
                spacialSensorListener, magneticField, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(
                spacialSensorListener, gravField, SensorManager.SENSOR_DELAY_GAME);

        forwardButton.setOnTouchListener(new View.OnTouchListener() {

            @Override public boolean onTouch (View v, MotionEvent event){
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        forwardMotionExecutor.setMotion(ForwardMotionExecutor.MOTION_FORWARD);
                        return true;

                    case MotionEvent.ACTION_UP:
                        forwardMotionExecutor.setMotion(ForwardMotionExecutor.MOTION_DECELERATE);
                        return true;
                }
                return false;
            }
        });

        backwardButton.setOnTouchListener(new View.OnTouchListener() {

            @Override public boolean onTouch (View v, MotionEvent event){
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        forwardMotionExecutor.setMotion(ForwardMotionExecutor.MOTION_REVERSE);
                        return true;

                    case MotionEvent.ACTION_UP:
                        forwardMotionExecutor.setMotion(ForwardMotionExecutor.MOTION_DECELERATE);
                        return true;
                }
                return false;
            }
        });
    }

    public void toggleDriveMode(View view){
        if(!gyroMode) {
            forwardButton.setVisibility(View.INVISIBLE);
            backwardButton.setVisibility(View.INVISIBLE);
            gyroMode = true;
            toggleButton.setText("Switch to Buttons");
        }else{
            forwardButton.setVisibility(View.VISIBLE);
            backwardButton.setVisibility(View.VISIBLE);
            gyroMode = false;
            toggleButton.setText("Switch to Gyro Mode");
        }
        spacialSensorListener.setGyroMode(gyroMode);
        forwardMotionExecutor.setMotion(ForwardMotionExecutor.MOTION_DECELERATE);
    }

    public void toggleStepMode(View view){
        if(!stepMode){
            forwardButton.setVisibility(View.INVISIBLE);
            backwardButton.setVisibility(View.INVISIBLE);
            toggleButton.setVisibility(View.INVISIBLE);
            stepButton.setText("Switch to Dive Mode");

            forwardMotionExecutor.setMotion(ForwardMotionExecutor.MOTION_DECELERATE);

            sensorManager.unregisterListener(spacialSensorListener, gravField);
            sensorManager.unregisterListener(spacialSensorListener, magneticField);
            sensorManager.registerListener(
                    spacialSensorListener, stepSensor, SensorManager.SENSOR_DELAY_GAME);
        }
        else{
            forwardButton.setVisibility(View.VISIBLE);
            backwardButton.setVisibility(View.VISIBLE);
            toggleButton.setVisibility(View.VISIBLE);
            stepButton.setText("Switch to Step Mode");

            stepMode = false;
            forwardMotionExecutor.setMotion(ForwardMotionExecutor.MOTION_DECELERATE);

            sensorManager.unregisterListener(spacialSensorListener, stepSensor);
            sensorManager.registerListener(
                    spacialSensorListener, gravField, SensorManager.SENSOR_DELAY_GAME);
            sensorManager.registerListener(
                    spacialSensorListener, magneticField, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause(){
        super.onPause();

        bluetoothTransmitter.closeBluetoothSocket();
        forwardMotionExecutor.setMotion(ForwardMotionExecutor.MOTION_REMOVE_ALL);
        sensorManager.unregisterListener(spacialSensorListener);
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();

        bluetoothTransmitter.closeBluetoothSocket();
        forwardMotionExecutor.setMotion(ForwardMotionExecutor.MOTION_REMOVE_ALL);
        sensorManager.unregisterListener(spacialSensorListener);
        unregisterReceiver(mReceiver);
    }

    @Override
    protected void onResume(){
        super.onResume();
        if (macAddress != null) {
            bluetoothTransmitter.createBluetoothSocket(Arrays.asList(macAddress));
        }

        forwardMotionExecutor.setMotion(ForwardMotionExecutor.MOTION_DECELERATE);

        if (stepMode) {
            sensorManager.registerListener(
                    spacialSensorListener, stepSensor, SensorManager.SENSOR_DELAY_GAME);
        } else{
            sensorManager.registerListener(
                    spacialSensorListener, magneticField, SensorManager.SENSOR_DELAY_GAME);
            sensorManager.registerListener(
                    spacialSensorListener, gravField, SensorManager.SENSOR_DELAY_GAME);
        }

    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String name = "";
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                if(name == null){
                    mArrayAdapter.add(device.getAddress());
                }else{
                    mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                }

            }
        }
    };

    public void pairedBluetooth(View view){
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        // If there are paired devices
        if (pairedDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {
                // Add the name and address to an array adapter to show in a ListView
                mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        }

        AlertDialog.Builder builderSingle = new AlertDialog.Builder(
                MainActivity.this);
        builderSingle.setTitle("Select One Name:-");

        builderSingle.setNegativeButton("cancel",
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mArrayAdapter.clear();
                        dialog.dismiss();
                    }
                });

        builderSingle.setAdapter(mArrayAdapter,
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String strName = mArrayAdapter.getItem(which);
                        AlertDialog.Builder builderInner = new AlertDialog.Builder(
                                MainActivity.this);
                        builderInner.setMessage(strName);
                        builderInner.setTitle("Your Selected Item is");
                        builderInner.setPositiveButton("Ok",
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(
                                            DialogInterface dialog,
                                            int which) {
                                        dialog.dismiss();
                                    }
                                });
                        builderInner.show();
                    }
                });
        builderSingle.show();

    }

    public void discoverDevices(View view){
        boolean success = mBluetoothAdapter.startDiscovery();

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy

        if(success){
            AlertDialog.Builder builderSingle = new AlertDialog.Builder(
                    MainActivity.this);
            builderSingle.setTitle("Select One Name:-");

            builderSingle.setNegativeButton("cancel",
                    new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mArrayAdapter.clear();
                            dialog.dismiss();
                        }
                    });

            builderSingle.setAdapter(mArrayAdapter,
                    new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // cell name in the form "Name\nAddress"
                            String strName = mArrayAdapter.getItem(which);
                            // retrieve the Mac Address from the string
                            macAddress = strName.split("\n")[1].trim();
                            Log.d("Mac Address", macAddress);
                            // connect to the device
                            bluetoothTransmitter.createBluetoothSocket(Arrays.asList(macAddress));
                        }
                    });
            builderSingle.show();
        }
    }

    @Override
    public void onSpeedChanged(int updatedSpeed) {
        bluetoothTransmitter.transmitSpeed(updatedSpeed);
    }

    @Override
    public void onDisplaySpeedChanged(String updatedSpeedString) {
        speedometer.setText(updatedSpeedString);
    }

    @Override
    public void onDirectionChanged(int updatedDirection) {
        Log.d("SENDING", Integer.toString(updatedDirection));
        bluetoothTransmitter.transmitDirection(updatedDirection);
    }

}
