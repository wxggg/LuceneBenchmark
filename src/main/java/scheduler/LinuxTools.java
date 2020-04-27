package scheduler;

public class LinuxTools {

    static {
        System.loadLibrary("linux_tools");
    }

    public static native void init();

    public static native void setAffinity(int cpu);

    public static native void createPerfEvents(String[] events);

    public static native void readEvents(long[] result);

}
