package rccarcontroller.hackathon.umass.rccarcontroller;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
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
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MainActivity extends ActionBarActivity implements SensorEventListener{

    private static final int LEFT = 256;
    private static final int CENTER = 257;
    private static final int RIGHT = 258;

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int DELAY = 100;
    private static final int ACCELERATE_SPEED = 15;
    private static final int BREAK_SPEED = 10;

    private SensorManager sensorManager;
    private Sensor rotationVector;

    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket btSocket;
    ArrayAdapter<String> mArrayAdapter;

    Button forwardButton;
    Button backwardButton;

    private Runnable runningThread;
    private Handler handler;
    private int speed;

    private Queue<Integer> speedQueue;
    private Lock queueLock;
    private Condition speedMonitor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        forwardButton = (Button) findViewById(R.id.forward);
        backwardButton = (Button) findViewById(R.id.backward);

        runningThread = decelerate;
        handler = new Handler();
        handler.postDelayed(runningThread, DELAY);
        speed = 0;

        speedQueue = new LinkedList<>();
        queueLock = new ReentrantLock();
        speedMonitor = queueLock.newCondition();

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        mArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        sensorManager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_NORMAL);

        forwardButton.setOnTouchListener(new View.OnTouchListener() {

            @Override public boolean onTouch (View v, MotionEvent event){
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        handler.removeCallbacks(runningThread);
                        runningThread = drive;
                        handler.postDelayed(runningThread, DELAY);
                        return true;

                    case MotionEvent.ACTION_UP:
                        handler.removeCallbacks(runningThread);
                        runningThread = decelerate;
                        handler.postDelayed(runningThread, DELAY);
                        return true;
                }
                return false;
            }
        });

        backwardButton.setOnTouchListener(new View.OnTouchListener() {

            @Override public boolean onTouch (View v, MotionEvent event){
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        handler.removeCallbacks(runningThread);
                        runningThread = reverse;
                        handler.postDelayed(runningThread, DELAY);
                        return true;

                    case MotionEvent.ACTION_UP:
                        handler.removeCallbacks(runningThread);
                        runningThread = decelerate;
                        handler.postDelayed(runningThread, DELAY);
                        return true;
                }
                return false;
            }
        });
    }

    // region RUNNABLES

    Runnable drive = new Runnable() {
        @Override
        public void run() {
            speed += ACCELERATE_SPEED;
            if (speed > 124) speed = 124;

            handler.postDelayed(this, DELAY);
            addIntToQueue(speed+125);

            Log.d("drive",Integer.toString(speed));
        }
    };

    Runnable reverse = new Runnable() {
        @Override
        public void run() {
            speed -= ACCELERATE_SPEED;
            if (speed < -124) speed = -124;

            handler.postDelayed(this, DELAY);
            addIntToQueue(speed+125);

            Log.d("reverse",Integer.toString(speed));
        }
    };

    Runnable decelerate = new Runnable() {
        @Override
        public void run() {
            if (speed < 0){
                speed += BREAK_SPEED;
                if (speed > 0) speed = 0;
            }
            else if (speed > 0){
                speed -= BREAK_SPEED;
                if (speed < 0) speed = 0;
            }

            handler.postDelayed(this, DELAY);
            addIntToQueue(speed+125);

            Log.d("decelerate",Integer.toString(speed));
        }
    };

    private void addIntToQueue(int speed){
        queueLock.lock();
        speedQueue.add(speed);
        speedMonitor.signal();
        queueLock.unlock();
    }

    //endregion

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
        try {
            if(btSocket != null) {
                btSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        handler.removeCallbacks(runningThread);
        runningThread = null;
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume(){
        super.onResume();
        runningThread = decelerate;
        handler.postDelayed(runningThread, DELAY);
        sensorManager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_NORMAL);
    }

    // region BLUETOOTH

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
                            String macAddress = strName.split("\n")[1].trim();
                            Log.d("Mac Address", macAddress);
                            // connect to the device
                            new CreateBluetoothSocket().execute(Arrays.asList(macAddress));
                        }
                    });
            builderSingle.show();
        }
    }

    private class CreateBluetoothSocket extends AsyncTask<List<String>, Integer, Boolean> {

        @Override
        protected Boolean doInBackground(List<String>... params) {
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(params[0].get(0));
            Log.d("", "Connecting to ... " + device);
            mBluetoothAdapter.cancelDiscovery();

            try {
                btSocket = device.createRfcommSocketToServiceRecord(UUID.fromString(
                        "00001101-0000-1000-8000-00805F9B34FB"));
                btSocket.connect();

                Log.d("", "Connection made");
            } catch (IOException e) {
                try {
                    btSocket.close();
                } catch (IOException e2) {
                    Log.d("Socket did not close", e2.getLocalizedMessage());
                }
                Log.d("Socket creation failed", e.getLocalizedMessage());
                return false;
            }

            new Thread(writeSpeedToBluetooth).start();
            return true;
        }

    }

    private class sendInformation extends AsyncTask<List<String>, Integer, String> {

        @Override
        protected String doInBackground(List<String>... params) {
            OutputStream out = null;
            String message = "";
            if (btSocket != null) {
                try {
                    out = btSocket.getOutputStream();
                } catch (IOException e) {
                    Log.d("Write data", "Bug BEFORE data was sent");
                }
                message = params[0].get(0).toString();
                short data = Short.parseShort(message);

                try {
                    out.write(data);
                    Log.d("SentfromBAckground", "Success");
                } catch (IOException e) {
                    Log.d("Write data", "Bug AFTER data was sent");
                }

            }
            return message;
        }

        @Override
        protected void onPostExecute(String message){
            if("".equals(message)){
                Toast toast = Toast.makeText(getApplicationContext(),
                        "No Bluetooth Connection", Toast.LENGTH_SHORT);
                toast.setGravity(1,0,0);
                toast.show();
            }
            if(Integer.toString(LEFT).equals(message)){
                Toast toast = Toast.makeText(getApplicationContext(),
                        "You are going left", Toast.LENGTH_SHORT);
                toast.setGravity(1,0,0);
                toast.show();
            }

            if(Integer.toString(CENTER).equals(message)){
                Toast toast = Toast.makeText(getApplicationContext(),
                        "You are going straight", Toast.LENGTH_SHORT);
                toast.setGravity(1,0,0);
                toast.show();
            }

            if(Integer.toString(RIGHT).equals(message)){
                Toast toast = Toast.makeText(getApplicationContext(),
                        "You are going right", Toast.LENGTH_SHORT);
                toast.setGravity(1,0,0);
                toast.show();
            }
        }

    }

    private Runnable writeSpeedToBluetooth = new Runnable() {
        @Override
        public void run() {
            if (!btSocket.isConnected()){
                Log.d("socket is NALL", "You done goofed");
                System.exit(1);
            }

            OutputStream out = null;

            while(true){
                queueLock.lock();

                while(speedQueue.isEmpty()){
                    try {
                        speedMonitor.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                String stringData = Integer.toString(speedQueue.poll());
                short data = Short.valueOf(stringData);
                Log.d("outputting", Short.toString(data));
                queueLock.unlock();

                try {
                    out = btSocket.getOutputStream();
                } catch (IOException e) {
                    Log.d("Write data", "Bug BEFORE data was sent");
                }
                try {
                    out.write(data);
                    Log.d("SentfromBAckground", "Success");
                } catch (IOException e) {
                    Log.d("Write data", "Bug AFTER data was sent");
                }
            }
        }
    };

    // endregion

    //TODO
    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;

        if(sensor.getType() == Sensor.TYPE_ROTATION_VECTOR){

        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

}
