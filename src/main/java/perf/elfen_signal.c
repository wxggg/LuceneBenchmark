#ifndef _GNU_SOURCE
#define _GNU_SOURCE             /* See feature_test_macros(7) */
#endif
#include <sched.h>
#include <jni.h>
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <pthread.h>
#include <unistd.h>
#include <sys/syscall.h>   /* For SYS_xxx definitions */
#include <perfmon/pfmlib_perf_event.h>
#include <unistd.h>
#include <string.h>
#include <stdio.h>
#include <fcntl.h>
#include <stdlib.h>
#include <sys/mman.h>
#include <pthread.h>
#include <signal.h>
#include <sys/ioctl.h>
#include <stdint.h>
#include <sys/wait.h>
#include <stdlib.h>
#include <perfmon/pfmlib_perf_event.h>
#include <sched.h>
#include <err.h>
#include <sys/time.h>
#include <sys/resource.h>


#define debug_print(...) fprintf (stderr, __VA_ARGS__)
static unsigned long __inline__ rdtsc(void)
{
  unsigned int tickl, tickh;
  __asm__ __volatile__("rdtscp":"=a"(tickl),"=d"(tickh)::"%ecx");
  return ((uint64_t)tickh << 32)|tickl;
}
typedef struct {
  int index;
  int fd;
  struct perf_event_attr perf_attr;
  struct perf_event_mmap_page *buf;
  char * name;
}hw_event_t;

__thread hw_event_t *perf_events = NULL;
__thread int nr_perf_events = 0;
//__thread unsigned long temp_perf_buf[2];

static char *copy_name(char *name)
{
  char *dst = (char *)malloc(strlen(name) + 1);
  strncpy(dst, name, strlen(name) + 1);
  return dst;
}

static void create_hw_event(char *name, hw_event_t *e)
{
  struct perf_event_attr *pe = &(e->perf_attr);
  int ret = pfm_get_perf_event_encoding(name, PFM_PLM3, pe, NULL, NULL);
  if (ret != PFM_SUCCESS) {
    errx(1, "error creating event '%s': %s\n", name, pfm_strerror(ret));
  }
  //  pe->sample_type = PERF_SAMPLE_READ;
  e->fd = perf_event_open(pe, 0, -1, -1, 0);
  if (e->fd == -1) {
    err(1, "error in perf_event_open for event %s", name);
  }
  //mmap the fd to get the raw index
  e->buf = (struct perf_event_mmap_page *)mmap(NULL, sysconf(_SC_PAGESIZE), PROT_READ, MAP_SHARED, e->fd, 0);
  if (e->buf == MAP_FAILED) {
    err(1,"mmap on perf fd %d %s", e->fd, name);
  }

  e->name = copy_name(name);

  e->index = e->buf->index - 1;
  debug_print("Creat hardware event name:%s, fd:%d, index:%x\n",
	      name,
	      e->fd,
	      e->index);
}

static hw_event_t * shim_create_hw_events(int nr_hw_events, char **hw_event_names)
{
  //relase old perf events
  int i;

  hw_event_t * hw_events = (hw_event_t *) calloc(nr_hw_events, sizeof(hw_event_t));
  if (hw_events == NULL)
    return NULL;

  for (i=0; i<nr_hw_events; i++){
    create_hw_event(hw_event_names[i], hw_events + i);
  }
  for (i=0;i <nr_hw_events; i++){
    hw_event_t *e = hw_events + i;
    debug_print("updateindex event %s, fd %d, index %x\n", e->name, e->fd, e->buf->index - 1);
    e->index = e->buf->index - 1;
  }
  return hw_events;
}


JNIEXPORT void Java_perf_Affinity_setCPUAffinity(JNIEnv *env, jobject obj, jint cpu)
{
  cpu_set_t cpuset;
  CPU_ZERO(&cpuset);
  CPU_SET(cpu, &cpuset);
  pid_t mytid = syscall(SYS_gettid);
  /* struct sched_param new_param; */
  /* new_param.sched_priority = 98; */
  /* sched_setscheduler(mytid, SCHED_FIFO, &new_param); */

  if (pthread_setaffinity_np(pthread_self(), sizeof(cpuset), &cpuset) == -1){
    fprintf(stderr, "Failed set CPU affinity to cpu %d\n", cpu);
  }
  printf("bind thread %d to cpu %d with scheduling class %d\n", (int)mytid, cpu, sched_getscheduler(mytid));
}

JNIEXPORT void JNICALL
Java_perf_Affinity_initPerf(JNIEnv *env, jclass cls)
{
  int ret = pfm_initialize();
  if (ret != PFM_SUCCESS) {
    err(1,"pfm_initialize() is failed!");
    exit(-1);
  }
}

unsigned long *lucene_signal_base = NULL;
unsigned int *lucene_queue_signal = NULL;

struct lucene_signal {
  unsigned long timestamp;
  //  unsigned long nr_cycles;
  //  unsigned long nr_instruction;
  unsigned long val;
};
struct lucene_signal *lucene_signal_buf = NULL;


/*
 *lucene_signal format
 * [queue_length][eventT0][eventT1]......
 * queue_length: unsigned int
 */

// JNIEXPORT void JNICALL
// Java_perf_Affinity_initSignal(JNIEnv *env, jclass cls)
// {
//   int fd = open("./lucene_signal", O_RDWR);
//   int i;
//   if (fd == -1){
//     err(1, "Can't open ./lucene_signal\n");
//     exit(1);
//   }
//   lucene_signal_base = (unsigned long *)mmap(0, 0x1024, PROT_READ|PROT_WRITE, MAP_SHARED, fd, 0);
//   if (lucene_signal_base == MAP_FAILED){
//     err(1, "Can't mmap ./lucene_signal\n");
//     exit(1);
//   }
//   lucene_queue_signal = (unsigned int *)lucene_signal_base;
//   lucene_signal_base[0] = 0;
//   lucene_signal_buf = (struct lucene_signal *)(lucene_signal_base + 1);
//   for (i=0; i<16; i++) {
//     memset(&lucene_signal_buf[i], 0, sizeof(struct lucene_signal));
//   }
// }

struct rusage stats[2];
struct timeval clocks[2];

// stage: -1 (waiting for new connection)
//      :  1 (enqueing task @id)
//      :  2 (dequeing task @id)
//      :  3 (finishing task @id)

// JNIEXPORT void JNICALL
// Java_perf_Affinity_postSignal(JNIEnv *env, jclass cls, jint stage, jint id, jint cpu)
// {
//   struct lucene_signal *s = lucene_signal_buf + cpu;

//   if (stage == 1)
//     __sync_fetch_and_add(lucene_queue_signal, 1);
//   else if (stage == 2)
//     __sync_fetch_and_sub(lucene_queue_signal, 1);

//   s->timestamp = rdtsc();
//   s->val = ((unsigned long)stage << 32) | (unsigned long)id;

// }
  /* if (taskID == 0 && stage == 1){ */
  /*   printf("Perf start\n"); */
  /*   getrusage(RUSAGE_SELF, &(stats[0])); */
  /*   gettimeofday(clocks, NULL); */
  /* } */

  /* if (taskID == 5704 && stage == 2){ */
  /*   printf("Perf end\n"); */
  /*   getrusage(RUSAGE_SELF, &(stats[1])); */
  /*   gettimeofday(clocks+1,NULL); */
  /*   unsigned long wall_clock = (clocks[1].tv_sec * 1000000 + clocks[1].tv_usec) - (clocks[0].tv_sec * 1000000 + clocks[0].tv_usec); */
  /*   unsigned long user_time = (stats[1].ru_utime.tv_sec * 1000000 + stats[1].ru_utime.tv_usec) - (stats[0].ru_utime.tv_sec * 1000000 + stats[0].ru_utime.tv_usec); */
  /*   unsigned long kernel_time = (stats[1].ru_stime.tv_sec * 1000000 + stats[1].ru_stime.tv_usec) - (stats[0].ru_stime.tv_sec * 1000000 + stats[0].ru_stime.tv_usec); */
  /*   printf("cpuutil:%.3f, %lu,%lu,%lu\n", (double)(user_time + kernel_time)/wall_clock, wall_clock, user_time, kernel_time); */
  /* } */


/* JNIEXPORT void JNICALL */
/* Java_perf_Affinity_postDequeSignal(JNIEnv *env, jclass cls) */
/* { */
/*   __sync_fetch_and_sub(lucene_queue_signal, 1); */
/* } */

/* JNIEXPORT void JNICALL */
/* Java_perf_Affinity_postEnqueSignal(JNIEnv *env, jclass cls, jint taskID) */
/* { */
/*   __sync_fetch_and_add(lucene_queue_signal, 1); */
/* } */


#define MAX_HW_COUNTERS (10)
JNIEXPORT void JNICALL
Java_perf_Affinity_createEvents(JNIEnv *env, jclass cls, jobjectArray event_strings)
{
  int nr_events = (*env)->GetArrayLength(env, event_strings);
  char *event_names[10];
  for (int i=0; i<nr_events; i++) {
    jstring string = (jstring) (*env)->GetObjectArrayElement(env, event_strings, i);
    event_names[i] = (char *)((*env)->GetStringUTFChars(env, string, 0));
  }
  if (perf_events == NULL){
    debug_print("Create perf_events for thread %ld\n", syscall(SYS_gettid));
  } else {
    debug_print("Warnning: create perf_events again for thread %ld\n", syscall(SYS_gettid));
  }
  perf_events = shim_create_hw_events(nr_events, event_names);
  nr_perf_events = nr_events;
  for (int i=0; i<nr_events; i++){
    jstring string = (jstring) (*env)->GetObjectArrayElement(env, event_strings, i);
    (*env)->ReleaseStringUTFChars(env, string, event_names[i]);
  }
  //  memset(temp_perf_buf, 0, sizeof(temp_perf_buf));
}

JNIEXPORT void JNICALL
Java_perf_Affinity_readEvents(JNIEnv *env, jclass cls, jlongArray result)
{
  jlong* r = (*env)->GetLongArrayElements(env,result, NULL);
  for (int i=0; i<nr_perf_events; i++){
    r[i] = __builtin_ia32_rdpmc(perf_events[i].index);
  }
  (*env)->ReleaseLongArrayElements(env,result, r, 0);
}