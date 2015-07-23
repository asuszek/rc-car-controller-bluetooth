package rccarcontroller.hackathon.umass.rccarcontroller;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A class to handle the creation and transmission between the app and a Bluetooth receiver.
 */
public class BluetoothTransmitter {

    BluetoothSocket btSocket;
    private BluetoothAdapter mBluetoothAdapter;

    private Queue<Integer> speedQueue;
    private Lock queueLock;
    private Condition speedMonitor;

    public BluetoothTransmitter(BluetoothAdapter mBluetoothAdapter) {
        this.mBluetoothAdapter = mBluetoothAdapter;
        // Create a thread-safe queue for transmitting speed and direction to the Arduino.
        speedQueue = new LinkedList<>();
        queueLock = new ReentrantLock();
        speedMonitor = queueLock.newCondition();
    }

    public void createBluetoothSocket(List<String> params) {
        new CreateBluetoothSocket().execute(params);
    }

    public void closeBluetoothSocket() {
        try {
            if(btSocket != null) {
                addSpeedToQueue(252);
                btSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void transmitSpeed(int speed) {
        addSpeedToQueue(speed);
    }

    public void transmitDirection(int direction) {
        new SendDirection().execute(Arrays.asList(Integer.toString(direction)));
    }

    private void addSpeedToQueue(int speed){
        queueLock.lock();
        speedQueue.add(speed);
        speedMonitor.signal();
        queueLock.unlock();
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

}
