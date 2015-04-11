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
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;




public class MainActivity extends ActionBarActivity implements SensorEventListener{

    private enum Directions{FORWARD, BACKWARD, LEFT, RIGHT}


    private static final int REQUEST_ENABLE_BT = 1;

    private SensorManager sensorManager;
    private Sensor rotationVector;

    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket btSocket;
    ArrayAdapter<String> mArrayAdapter;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume(){
        super.onResume();
        sensorManager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_NORMAL);
    }

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

    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;

        if(sensor.getType() == Sensor.TYPE_ROTATION_VECTOR){

        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

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

            return true;
        }

    }

    public void forward(View view) {

        new sendInformation().execute(Arrays.asList(Directions.FORWARD.toString()));

    }


    public void backward(View view) {

        new sendInformation().execute(Arrays.asList(Directions.BACKWARD.toString()));

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
                //char sender = '1';

                byte[] msgBuffer = message.getBytes();
                try {
                    out.write(msgBuffer);
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
                Toast toast = Toast.makeText(getApplicationContext(), "No Bluetooth Connection", Toast.LENGTH_SHORT);
                toast.setGravity(1,0,0);
                toast.show();
            }
            if(Directions.FORWARD.toString().equals(message)){
                Toast toast = Toast.makeText(getApplicationContext(), "You are going forward", Toast.LENGTH_SHORT);
                toast.setGravity(1,0,0);
                toast.show();
            }

            if(Directions.BACKWARD.toString().equals(message)){
                Toast toast = Toast.makeText(getApplicationContext(), "You are going Backward", Toast.LENGTH_SHORT);
                toast.setGravity(1,0,0);
                toast.show();
            }
        }



    }

}
