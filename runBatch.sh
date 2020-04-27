#!/bin/sh


echo $$ > /sys/fs/cgroup/cpuset/scheduler/tasks 

stress -c 8