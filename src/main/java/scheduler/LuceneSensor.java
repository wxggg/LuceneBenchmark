package scheduler;

/**
 * Sensor
 */
public class LuceneSensor {

    static {
        System.loadLibrary("lucene_sensor");
    }

    public static native void init();

    public static native void post(long queueLength, long currentLatency);
}