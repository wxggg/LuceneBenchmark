#!/bin/bash

taskfile=./wiki.1M.nostopwords.term.tasks
ip=127.0.0.1
port=7777
iter=5
# dir=res/nice10_LC_batch/
# dir=res/chrt10_LC_batch/
# dir=res/nice_10_LC_cgroup_for_batch/
# dir=res/test/
# dir=res/test_batch_dedicate/
dir=res/schedule/nice10/batch1/


rm -rf $dir
mkdir -p $dir

python2 sendTasks.py $taskfile $ip $port 200 1000000 200000 "/tmp/warmup-100-5" 5 "shuffle"

for ((i = 50; i <= 400; i += 50)); do
    python2 sendTasks.py $taskfile $ip $port $i 1000000 200000 "$dir/log-$i-$iter" $iter "shuffle"
done
