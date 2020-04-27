#include <jni.h>
#include <perfmon/pfmlib_perf_event.h>
#include <pthread.h>
#include <sched.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/mman.h>

struct hw_event_t {
  int fd;
  int index;
  struct perf_event_attr attr;
  struct perf_event_mmap_page *page;
  char *name;
};

__thread struct hw_event_t *perf_events = NULL;
__thread int nr_perf_events = 0;

JNIEXPORT void JNICALL Java_scheduler_LinuxTools_init(JNIEnv *env, jclass cls) {
  printf("init linux tools\n");
  int ret = pfm_initialize();
  if (ret != PFM_SUCCESS) {
    err(1, "pfm_initialize failed!");
    exit(-1);
  }
}

JNIEXPORT void JNICALL Java_scheduler_LinuxTools_setAffinity(JNIEnv *env,
                                                             jclass cls,
                                                             jint cpu) {
  cpu_set_t cpuset;
  CPU_ZERO(&cpuset);
  CPU_SET(cpu, &cpuset);
  pthread_t tid = pthread_self();

  if (pthread_setaffinity_np(tid, sizeof(cpuset), &cpuset) == -1) {
    printf("failed to set cpu affinity to cpu %d\n", cpu);
  }
  printf("bind thread %d to cpu %d with scheduling sclass %d\n", tid, cpu,
         sched_getscheduler(tid));
}

JNIEXPORT void JNICALL Java_scheduler_LinuxTools_createPerfEvents(
    JNIEnv *env, jclass cls, jobjectArray events) {
  int n = (*env)->GetArrayLength(env, events);
  nr_perf_events = n;
  char *event_names[16];
  for (int i = 0; i < n && n < 16; i++) {
    jstring str = (jstring)(*env)->GetObjectArrayElement(env, events, i);
    event_names[i] = (char *)((*env)->GetStringUTFChars(env, str, 0));
    printf("create event %s\n", event_names[i]);
  }

  perf_events = (struct hw_event_t *)calloc(n, sizeof(struct hw_event_t));
  if (perf_events == NULL)
    exit(0);

  for (int i = 0; i < n; i++) {
    struct hw_event_t *e = &(perf_events[i]);
    struct perf_event_attr *attr = &(e->attr);

    int ret =
        pfm_get_perf_event_encoding(event_names[i], PFM_PLM3, attr, NULL, NULL);
    if (ret != PFM_SUCCESS)
      perror("error pfm_get_perf_event_encoding\n");

    e->fd = perf_event_open(attr, 0, -1, -1, 0);
    if (e->fd == -1)
      perror("error perf_event_open\n");

    e->page = (struct perf_event_mmap_page *)mmap(
        NULL, sysconf(_SC_PAGE_SIZE), PROT_READ, MAP_SHARED, e->fd, 0);

    if (e->page == MAP_FAILED)
      perror("error mmap");

    e->index = e->page->index - 1;
  }

  for (int i = 0; i < n; i++) {
    jstring str = (jstring)(*env)->GetObjectArrayElement(env, events, i);
    (*env)->ReleaseStringUTFChars(env, str, event_names[i]);
  }
}

JNIEXPORT void JNICALL Java_scheduler_LinuxTools_readEvents(JNIEnv *env,
                                                            jclass cls,
                                                            jlongArray result) {
  jlong *res = (*env)->GetLongArrayElements(env, result, NULL);
  for (int i = 0; i < nr_perf_events; i++) {
    res[i] = __builtin_ia32_rdpmc(perf_events[i].index);
  }
  (*env)->ReleaseLongArrayElements(env, result, res, 0);
}