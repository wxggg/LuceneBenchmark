
package scheduler;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Scheduler
 */
public class Scheduler {

    /**
     * cpuset used for scheduler, to isolate batch tasks
     */
    private final String cpusetPathDir = "/sys/fs/cgroup/cpuset/scheduler/";
    private final String cpusetPathFile = "/sys/fs/cgroup/cpuset/scheduler/cpuset.cpus";
    private String currentCpuset;

    private final Lock lock;
    private final Condition condition;

    private int availableProcessors;
    private int halfAvailableProcessors;

    private int idleFactor;

    // 0: free
    // 1: running latency-sensitive load
    // 2: running batch
    private Integer cpuStatus[];

    public Scheduler() {
        initCpuset();

        this.lock = new ReentrantLock();
        this.condition = lock.newCondition();
        this.availableProcessors = Runtime.getRuntime().availableProcessors();
        this.halfAvailableProcessors = this.availableProcessors / 2;
        this.idleFactor = 0;
        this.cpuStatus = new Integer[this.availableProcessors];

        bestForWorkers();
    }

    /**
     * @return the halfAvailableProcessors
     */
    public int getHalfAvailableProcessors() {
        return halfAvailableProcessors;
    }

    private void initCpuset() {
        File dir = new File(cpusetPathDir);
        if (!dir.exists()) {
            dir.mkdir();
            System.out.println("[initCpuset] mkdir:" + cpusetPathDir);
        }
    }

    private void writeCpuset(final String cpuset) {
        if (cpuset == currentCpuset) {
            return;
        }

        try {
            final FileWriter writer = new FileWriter(this.cpusetPathFile);
            writer.write(cpuset);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            // TODO: handle exception
            e.printStackTrace();
        }

        currentCpuset = cpuset;
    }

    private String calculateBatchCpuset() {
        for (int i = 0; i < this.availableProcessors; i++) {
            System.out.print(cpuStatus[i]);
        }
        System.out.println();
        return cpusetPathDir;
    }

    void bestForWorkers() {
        for (int i = 0; i < this.halfAvailableProcessors; i++) {
            cpuStatus[i] = 1;
            cpuStatus[i + this.halfAvailableProcessors] = 0;
        }
        writeCpuset("7");
    }

    void bestForBatchs() {
        cpuStatus[0] = 1;
        cpuStatus[0 + this.halfAvailableProcessors] = 0;

        for (int i = 1; i < this.halfAvailableProcessors; i++) {
            this.cpuStatus[i] = 2;
            this.cpuStatus[i + this.halfAvailableProcessors] = 2;
        }
        writeCpuset("1-3,5-7");
    }

    public void schedule(final int cpuId, final int queueSize) {
        // System.out.println("[scheduler] " + cpuId);

        // when request begin to block wake up workers on other cpu and isolate
        // related batch tasks
        if (queueSize > 3) {
            lock.lock();
            condition.signalAll();
            lock.unlock();
            bestForWorkers();
            idleFactor = 0;
            return;
        }

        // this means load is low, batch tasks is possible to run
        if (queueSize == 0) {
            idleFactor++;

            if (idleFactor > 10) {
                bestForBatchs();
            }
        }

        // thread on cpu 0 should keep running for sudden request
        // if (cpuId == 0 || cpuId == 1 || cpuId == 2) {
        if (cpuId == 0) {
            return;
        }

        /**
         * threads on other cpu should sleep, leave hardware resources for batch tasks
         */
        try {
            lock.lock();
            if (queueSize == 0) {
                condition.await();
            }
        } catch (InterruptedException e) {
            // TODO: handle exception
            e.printStackTrace();
        } finally {
            lock.unlock();
        }

    }

}