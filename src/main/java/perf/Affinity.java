package perf;

public class Affinity {
  static {
    System.loadLibrary("elfen_signal");
  }

  public static native void setCPUAffinity(int cpu);

  public static native void initPerf();

  public static native void createEvents(String[] eventNames);

  public static native void readEvents(long[] result);

  // public static native void initSignal();
  // public static native void postSignal(int stage, int id, int cpu);
  // public static native void postEnqueSignal();
  // public static native void postDequeSignal();
}