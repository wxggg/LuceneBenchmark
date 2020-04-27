package perf;

import java.io.FileWriter;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import scheduler.*;

public class TaskThreads {

	private final TaskThread[] threads;
	final CountDownLatch startLatch = new CountDownLatch(1);
	final CountDownLatch stopLatch;
	final AtomicBoolean stop;
	final Scheduler scheduler;

	public TaskThreads(final TaskSource tasks, final IndexState indexState, int numThreads) {
		LuceneSensor.init();
		LinuxTools.init();
		// numThreads = 4;
		// numThreads = 2;
		scheduler = new Scheduler();
		int processors = scheduler.getHalfAvailableProcessors();
		// int lanes = processors * 2;
		int lanes = processors;
		// lanes = 1; // test for lucene alone
		threads = new TaskThread[lanes];
		stopLatch = new CountDownLatch(lanes);
		stop = new AtomicBoolean(false);
		for (int cpuIdX = 0; cpuIdX < lanes; cpuIdX++) {
			threads[cpuIdX] = new TaskThread(startLatch, stopLatch, stop, tasks, indexState, cpuIdX, scheduler);
			threads[cpuIdX].start();
		}
	}

	public void start() {
		startLatch.countDown();
	}

	public void finish() throws InterruptedException {
		stopLatch.await();
	}

	public void stop() throws InterruptedException {
		stop.getAndSet(true);
		for (final Thread t : threads) {
			t.join();
		}
	}

	/**
	 * Scheduler
	 */

	private static class SchedulerBackup {

		private final Lock lock;
		private final Condition condition;

		private final int maxCpu;
		private final HashMap<Integer, Integer> cpuStatus;

		private final String batchCpusetCpus = "/sys/fs/cgroup/cpuset/test/cpuset.cpus";
		private String currentCpus;

		private int idleFactor;

		public SchedulerBackup(final int maxCpu) {
			this.lock = new ReentrantLock();
			this.condition = lock.newCondition();
			this.maxCpu = maxCpu;
			this.cpuStatus = new HashMap<>();

			this.cpuStatus.put(0, 1);
			for (int i = 1; i < this.maxCpu; i++) {
				this.cpuStatus.put(i, 0);
			}

			setBatchCpuset("1-3,5-7");

		}

		private void setBatchCpuset(final String cpus) {
			if (cpus == currentCpus) {
				return;
			}

			try {
				final FileWriter writer = new FileWriter(this.batchCpusetCpus);
				writer.write(cpus);
				writer.flush();
				writer.close();
			} catch (final IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			currentCpus = cpus;
		}

		private void slowDown() {
			String cpus = "";

			cpus = "1-3,5-7";

			setBatchCpuset(cpus);
		}

		void schedule(final int cpuId, final int queueSize) {
			System.out.println("schedule thread on cpu=" + cpuId + " with queueSize=" + queueSize);
			if (queueSize > 3) {
				lock.lock();
				condition.signalAll();
				lock.unlock();
				setBatchCpuset("7");
				idleFactor = 0;
				cpuStatus.put(cpuId, 1);
			}

			if (queueSize == 0) {
				idleFactor++;

				if (idleFactor > 10) {
					slowDown();
					if (cpuId != 0) {
						cpuStatus.put(cpuId, 0);
					}
				}
			}

			if (cpuStatus.get(cpuId) == 1) {
				return;
			}

			// other threads need to be scheduled when necessary
			// for lazy threads
			try {
				lock.lock();
				if (queueSize == 0) {
					System.out.println("queueSize=" + queueSize + " Thread " + cpuId + " is awaiting....");
					condition.await();
				}

				System.out.println("queueSize=" + queueSize + " Thread " + cpuId + " is working....");

			} catch (final InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} finally {
				lock.unlock();
			}

		}
	}

	private static class TaskThread extends Thread {
		private final CountDownLatch startLatch;
		private final CountDownLatch stopLatch;
		private final AtomicBoolean stop;
		private final TaskSource tasks;
		private final IndexState indexState;
		private final int cpuId;

		private final Scheduler scheduler;

		public TaskThread(final CountDownLatch startLatch, final CountDownLatch stopLatch, final AtomicBoolean stop,
				final TaskSource tasks, final IndexState indexState, final int cpuId, final Scheduler scheduler) {
			this.startLatch = startLatch;
			this.stopLatch = stopLatch;
			this.stop = stop;
			this.tasks = tasks;
			this.indexState = indexState;
			this.cpuId = cpuId;
			this.scheduler = scheduler;
		}

		@Override
		public void run() {
			// System.out.println("TaskThread " + cpuId + " set to CPU " + cpuId);
			LinuxTools.setAffinity(cpuId);
			final String[] events = { "INSTRUCTION_RETIRED:u:k", "UNHALTED_CORE_CYCLES:u:k" };
			LinuxTools.createPerfEvents(events);

			final long[] eventBeginVals = new long[3];
			final long[] eventEndVals = new long[3];

			try {
				startLatch.await();
			} catch (final InterruptedException ie) {
				Thread.currentThread().interrupt();
				return;
			}

			try {
				while (!stop.get()) {

					scheduler.schedule(cpuId, tasks.taskSize());

					final Task task = tasks.nextTask();
					if (task == null) {
						// Done
						break;
					}
					task.cpuId = cpuId;
					task.processTimeNS = System.nanoTime();
					LinuxTools.readEvents(eventBeginVals);
					try {
						task.go(indexState);
					} catch (final IOException ioe) {
						throw new RuntimeException(ioe);
					}
					task.finishTimeNS = System.nanoTime();
					LinuxTools.readEvents(eventEndVals);

					task.instructions = eventEndVals[0] - eventBeginVals[0];
					task.cycles = eventEndVals[1] - eventBeginVals[1];

					// Sensor.post(task.queSize, task.finishTimeNS - task.recvTimeNS);

					try {
						tasks.taskDone(task);
					} catch (final Exception e) {
						System.out.println(Thread.currentThread().getName() + ": ignoring exc:");
						e.printStackTrace();
					}
				}
			} catch (final Exception e) {
				throw new RuntimeException(e);
			} finally {
				stopLatch.countDown();
			}
		}
	}
}
