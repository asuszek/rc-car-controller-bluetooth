package rccarcontroller.hackathon.umass.rccarcontroller;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;

public class SpacialSensorListener implements SensorEventListener {

    /**
     * Classes that implement this interface will receive notifications whenever direction
     * has been updated.
     */
    public interface DirectionChangedListener {

        void onDirectionChanged(int updatedDirection);

    }

    // Possible directions to be sent to the bluetooth receiver.
    private static final int LEFT = 253;
    private static final int CENTER = 254;
    private static final int RIGHT = 255;

    private static final float PITCH_THRESHOLD = 20.f;
    private static final float ROLL_THRESHOLD = 20.f;
    private static final float VERTICAL_OFFSET = 10.f;

    private ForwardMotionExecutor forwardMotionExecutor;
    private DirectionChangedListener directionChangedListener;

    private float[] mRotationMatrix;
    private float[] mOrientationVector;
    private float[] mLastGrav;
    private float[] mLastMag;
    private Filter[] mFilters;
    private int currentDirection;
    private boolean gyroMode;

    public SpacialSensorListener(
            ForwardMotionExecutor forwardMotionExecutor,
            DirectionChangedListener directionChangedListener) {
        this.forwardMotionExecutor = forwardMotionExecutor;
        this.directionChangedListener = directionChangedListener;

        // Initialize matrices used to calculation orientation in space.
        mRotationMatrix = new float[16];
        mOrientationVector = new float[3];
        mLastGrav = new float[3];
        mLastMag = new float[3];
        mFilters = new Filter[2];
        for(int i=0; i<2; i++) mFilters[i] = new Filter();
        currentDirection = CENTER;
    }

    public void setGyroMode(boolean gyroMode) {
        this.gyroMode = gyroMode;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;

        if (sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            onGravityEvent(event);
        }
        if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD){
            onMagneticEvent(event);
        }
        if (sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            onStepEvent();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // no-op
    }

    private void onGravityEvent(SensorEvent event){
        // Just store the values and wait for a magnetic event.
        System.arraycopy(event.values, 0, mLastGrav, 0, 3);
    }

    private void onMagneticEvent(SensorEvent event){
        // Store values and derive an orientation if we have enough information.
        System.arraycopy(event.values, 0, mLastMag, 0, 3);
        if (mLastMag != null){
            computeOrientation();
        }
    }

    private void onStepEvent(){
        // Drive forward for half a second, then brake. Repeat for each step taken.
        forwardMotionExecutor.setMotion(ForwardMotionExecutor.MOTION_FORWARD);
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                forwardMotionExecutor.setMotion(ForwardMotionExecutor.MOTION_DECELERATE);
            }
        }, 500);
    }

    private void computeOrientation(){
        if (SensorManager.getRotationMatrix(mRotationMatrix, null, mLastGrav, mLastMag)) {
            SensorManager.getOrientation(mRotationMatrix, mOrientationVector);

            float pitch = mOrientationVector[1] * 57.2957795f;
            float roll = mOrientationVector[2] * 57.2957795f;

            float mLastPitch = mFilters[0].append(pitch);
            float mLastRoll = mFilters[1].append(roll);

            switch (currentDirection){
                case LEFT:{
                    if (mLastPitch < PITCH_THRESHOLD){
                        currentDirection = CENTER;
                        directionChangedListener.onDirectionChanged(currentDirection);
                    }
                    break;
                }
                case RIGHT:{
                    if (mLastPitch > -PITCH_THRESHOLD){
                        currentDirection = CENTER;
                        directionChangedListener.onDirectionChanged(currentDirection);
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
                    directionChangedListener.onDirectionChanged(currentDirection);
                }
            }

            if (gyroMode){
                if (mLastRoll > 10.f){
                    if (forwardMotionExecutor.getCurrentMotion()
                            != ForwardMotionExecutor.MOTION_DECELERATE){
                        forwardMotionExecutor.setMotion(ForwardMotionExecutor.MOTION_DECELERATE);
                        Log.d("SWITCHING", "Brake");
                    }
                }
                else if (mLastRoll > (-90.f + VERTICAL_OFFSET + ROLL_THRESHOLD)){
                    if (forwardMotionExecutor.getCurrentMotion()
                            != ForwardMotionExecutor.MOTION_FORWARD){
                        forwardMotionExecutor.setMotion(ForwardMotionExecutor.MOTION_FORWARD);
                        Log.d("SWITCHING", "Drive");
                    }
                }
                else if (mLastRoll < (-90.f + VERTICAL_OFFSET - ROLL_THRESHOLD)){
                    if (forwardMotionExecutor.getCurrentMotion()
                            != ForwardMotionExecutor.MOTION_REVERSE){
                        forwardMotionExecutor.setMotion(ForwardMotionExecutor.MOTION_REVERSE);
                        Log.d("SWITCHING", "Reverse");
                    }
                }
                else{
                    if (forwardMotionExecutor.getCurrentMotion()
                            != ForwardMotionExecutor.MOTION_DECELERATE){
                        forwardMotionExecutor.setMotion(ForwardMotionExecutor.MOTION_DECELERATE);
                        Log.d("SWITCHING", "Brake");
                    }
                }
            }
        }
    }

}
