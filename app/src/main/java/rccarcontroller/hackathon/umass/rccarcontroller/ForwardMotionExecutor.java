package rccarcontroller.hackathon.umass.rccarcontroller;

import android.os.Handler;

/**
 * A class to contain the {@link java.lang.Runnable} threads that control
 * forward motion (driving forward, driving backward, and decelerating).
 */
public class ForwardMotionExecutor {

    /**
     * Classes that implement this interface will receive notifications whenever speed
     * has been updated.
     */
    public interface SpeedChangeListener {

        void onSpeedChanged(int updatedSpeed);

        void onDisplaySpeedChanged(String updatedSpeedString);

    }

    public static final int MOTION_REMOVE_ALL = 0;
    public static final int MOTION_FORWARD = 1;
    public static final int MOTION_REVERSE = 2;
    public static final int MOTION_DECELERATE = 3;

    private static final int THREAD_DELAY = 100; // 100ms
    // Speeds are parts per 256, updated every execution loop.
    private static final int ACCELERATE_SPEED = 11;
    private static final int BREAK_SPEED = 18;
    private static final int SPEED_STOPPED = 0;
    private static final int MAX_SPEED = 124;
    private static final int MIN_SPEED = -124;
    private static final double RC_FEET_PER_SEC = 5.27; // Calculated through experimentation.

    private Handler handler;
    private SpeedChangeListener speedChangeListener;
    private Runnable currentExecutor;

    private int currentMotion;
    private int speed;

    public ForwardMotionExecutor(Handler handler, SpeedChangeListener speedChangeListener) {
        this.handler = handler;
        this.speedChangeListener = speedChangeListener;
        speed = SPEED_STOPPED;
        // Begin execution.
        setMotion(MOTION_DECELERATE);
    }

    public void setMotion(int motion) {
        currentMotion = motion;
        if (currentExecutor != null) {
            handler.removeCallbacks(currentExecutor);
        }
        switch (motion) {
            case MOTION_REMOVE_ALL:
                currentExecutor = null;
            case MOTION_FORWARD:
                currentExecutor = drive;
            case MOTION_REVERSE:
                currentExecutor = reverse;
            case MOTION_DECELERATE:
            default:
                // On bad input, decelerate to be safe
                currentExecutor = decelerate;
        }
        handler.postDelayed(currentExecutor, THREAD_DELAY);
    }

    public int getCurrentMotion() {
        return currentMotion;
    }

    private String convertSpeedToDisplay(int speed) {
        String displaySpeed = Double.toString(
                Math.abs((speed * RC_FEET_PER_SEC) / MAX_SPEED + 1));
        displaySpeed = displaySpeed.substring(0,3);
        return displaySpeed;
    }


    private Runnable drive = new Runnable() {
        @Override
        public void run() {
            speed += ACCELERATE_SPEED;
            speed = Math.min(speed, MAX_SPEED);
            speedChangeListener.onSpeedChanged(speed + MAX_SPEED + 1);
            speedChangeListener.onDisplaySpeedChanged(convertSpeedToDisplay(speed));
            handler.postDelayed(this, THREAD_DELAY);
        }
    };

    private Runnable reverse = new Runnable() {
        @Override
        public void run() {
            speed -= ACCELERATE_SPEED;
            speed = Math.max(speed, MIN_SPEED);
            speedChangeListener.onSpeedChanged(speed + MAX_SPEED + 1);
            speedChangeListener.onDisplaySpeedChanged(convertSpeedToDisplay(speed));
            handler.postDelayed(this, THREAD_DELAY);
        }
    };

    private Runnable decelerate = new Runnable() {
        @Override
        public void run() {
            if (speed < SPEED_STOPPED){ // Currently in reverse.
                speed += BREAK_SPEED;
                speed = Math.min(speed, SPEED_STOPPED);
            } else if (speed > SPEED_STOPPED){ // Currently driving forward.
                speed -= BREAK_SPEED;
                speed = Math.max(speed, SPEED_STOPPED);
            }
            speedChangeListener.onSpeedChanged(speed + MAX_SPEED + 1);
            speedChangeListener.onDisplaySpeedChanged(convertSpeedToDisplay(speed));
            handler.postDelayed(this, THREAD_DELAY);
        }
    };

}
