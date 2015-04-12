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

    private static final int LEFT = 253;
    private static final int CENTER = 254;
    private static final int RIGHT = 255;

    private static final float PITCH_THRESHOLD = 20.f;
    private static final float ROLL_THRESHOLD = 20.f;
    private static final float VERTICAL_OFFSET = 10.f;

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int DELAY = 100;
    private static final int ACCELERATE_SPEED = 15;
    private static final int BREAK_SPEED = 10;


    private SensorManager sensorManager;
    private Sensor magneticField;
    private Sensor gravField;
    private Sensor stepSensor;

    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket btSocket;
    ArrayAdapter<String> mArrayAdapter;

    Button forwardButton;
    Button backwardButton;
    Button toggleButton;

    private boolean gyroMode;
    private boolean stepMode;

    private Runnable runningThread;
    private Handler handler;
    private int speed;

    private Queue<Integer> speedQueue;
    private Lock queueLock;
    private Condition speedMonitor;

    private float[] mRotationMatrix;
    private float[] mOrientationVector;
    private float[] mLastGrav;
    private float[] mLastMag;
    private Filter[] mFilters;
    private float mLastPitch;
    private float mLastRoll;
    private int currentDirection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        forwardButton = (Button) findViewById(R.id.forward);
        backwardButton = (Button) findViewById(R.id.backward);
        toggleButton = (Button) findViewById(R.id.toggleMode);

        gyroMode = false;

        runningThread = decelerate;
        handler = new Handler();
        handler.postDelayed(runningThread, DELAY);
        speed = 0;

        speedQueue = new LinkedList<>();
        queueLock = new ReentrantLock();
        speedMonitor = queueLock.newCondition();

        mRotationMatrix = new float[16];
        mOrientationVector = new float[3];
        mLastGrav = new float[3];
        mLastMag = new float[3];
        mFilters = new Filter[2];
        for(int i=0; i<2; i++) mFilters[i] = new Filter();
        currentDirection = CENTER;

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
        magneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        gravField = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);

        sensorManager.registerListener(this, magneticField, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, gravField, SensorManager.SENSOR_DELAY_GAME);

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
            addIntToQueue(speed + 125);

            Log.d("reverse", Integer.toString(speed));
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

            //Log.d("decelerate",Integer.toString(speed));
        }
    };

    private void addIntToQueue(int speed){
        queueLock.lock();
        speedQueue.add(speed);
        speedMonitor.signal();
        queueLock.unlock();
    }

    //endregion

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


        handler.removeCallbacks(runningThread);
        runningThread = decelerate;
        handler.postDelayed(runningThread, DELAY);
    }

    public void toggleStepMode(View view){
        if(!stepMode){
            forwardButton.setVisibility(View.INVISIBLE);
            backwardButton.setVisibility(View.INVISIBLE);
            stepMode = true;
            handler.removeCallbacks(runningThread);
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_GAME);
        }
        else{
            stepMode = false;
            sensorManager.unregisterListener(this, stepSensor);
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

        sensorManager.registerListener(this, magneticField, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, gravField, SensorManager.SENSOR_DELAY_GAME);
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

    private class SendDirection extends AsyncTask<List<String>, Integer, String> {

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
                message = params[0].get(0);
                //Log.d("SENDING", message);

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
                //Log.d("outputting", Short.toString(data));
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

    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;

        if (sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            grav(event);
        }
        if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD){
            magnetic(event);
        }
        if (sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            step(event);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void grav(SensorEvent event){
        System.arraycopy(event.values, 0, mLastGrav, 0, 3);
    }

    private void magnetic(SensorEvent event){
        System.arraycopy(event.values, 0, mLastMag, 0, 3);

        if (mLastMag != null){
            computeOrientation();
        }
    }

    private void step(SensorEvent event){
        
    }

    private void computeOrientation(){
        if (SensorManager.getRotationMatrix(mRotationMatrix, null, mLastGrav, mLastMag)){
            SensorManager.getOrientation(mRotationMatrix, mOrientationVector);

            float pitch = mOrientationVector[1] * 57.2957795f;
            float roll = mOrientationVector[2] * 57.2957795f;

            mLastPitch = mFilters[0].append(pitch);
            mLastRoll = mFilters[1].append(roll);

            //Log.d("PITCH", Float.toString(mLastPitch));
            //Log.d("ROLL", Float.toString(mLastRoll));

            switch (currentDirection){
                case LEFT:{
                    if (mLastPitch < PITCH_THRESHOLD){
                        currentDirection = CENTER;
                        Log.d("SENDING", Integer.toString(currentDirection));
                        (new SendDirection()).execute(
                                Arrays.asList(Integer.toString(currentDirection)));
                    }
                    break;
                }
                case RIGHT:{
                    if (mLastPitch > -PITCH_THRESHOLD){
                        currentDirection = CENTER;
                        Log.d("SENDING", Integer.toString(currentDirection));
                        (new SendDirection()).execute(
                                Arrays.asList(Integer.toString(currentDirection)));
                    }
                    break;
                }
                case CENTER:{
                    if (mLastPitch > PITCH_THRESHOLD){
                        currentDirection = LEFT;
                    }
                    else{
                        if (mLastPitch < -PITCH_THRESHOLD){
                            currentDirection = RIGHT;
                        }
                        else break;
                    }
                    Log.d("SENDING", Integer.toString(currentDirection));
                    (new SendDirection()).execute(
                            Arrays.asList(Integer.toString(currentDirection)));
                }
            }

            if (gyroMode){
                if (mLastRoll > (-90.f + VERTICAL_OFFSET + ROLL_THRESHOLD)){
                    //Log.d("DRIVE", Float.toString(mLastRoll));
                    if (runningThread != drive){
                        handler.removeCallbacks(runningThread);
                        runningThread = drive;
                        handler.postDelayed(runningThread, DELAY);
                        Log.d("SWITCHING", "Drive");
                    }
                }
                else if (mLastRoll < (-90.f + VERTICAL_OFFSET - ROLL_THRESHOLD)){
                    //Log.d("REVERSE", Float.toString(mLastRoll));
                    if (runningThread != reverse){
                        handler.removeCallbacks(runningThread);
                        runningThread = reverse;
                        handler.postDelayed(runningThread, DELAY);
                        Log.d("SWITCHING", "Reverse");
                    }
                }
                else{
                    //Log.d("BRAKE", Float.toString(mLastRoll));
                    if (runningThread != decelerate){
                        handler.removeCallbacks(runningThread);
                        runningThread = decelerate;
                        handler.postDelayed(runningThread, DELAY);
                        Log.d("SWITCHING", "Brake");
                    }
                }
            }
        }
    }

    private class Filter {
        static final int AVERAGE_BUFFER = 2;
        float []m_arr = new float[AVERAGE_BUFFER];
        int m_idx = 0;

        public float append(float val) {
            m_arr[m_idx] = val;
            m_idx++;
            if (m_idx == AVERAGE_BUFFER)
                m_idx = 0;
            return avg();
        }
        public float avg() {
            float sum = 0;
            for (float x: m_arr)
                sum += x;
            return sum / AVERAGE_BUFFER;
        }
    }

}
