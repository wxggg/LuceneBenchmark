#include <fcntl.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/shm.h>

#define SENSOR_KEY 1234

unsigned long *sensor_addr = NULL;

JNIEXPORT void Java_scheduler_LuceneSensor_init(JNIEnv *env, jclass cls) {
  printf("sensor init\n");
  int shmid = shmget(SENSOR_KEY, 0, 0666);
  if (shmid < 0) {
    shmid = shmget(SENSOR_KEY, 4096, IPC_CREAT | IPC_EXCL | 0666);
    if (shmid < 0) {
      perror("shmget");
      exit(0);
    }
  }

  sensor_addr = (unsigned long *)shmat(shmid, 0, 0);
  if (sensor_addr == (void *)(-1)) {
    perror("shmat");
    exit(0);
  }
}

JNIEXPORT void Java_scheduler_LuceneSensor_post(JNIEnv *env, jclass cls, jlong length,
                                     jlong latency) {
  sensor_addr[0] = length;
  sensor_addr[1] = latency;
}