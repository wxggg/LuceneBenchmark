#!/bin/bash

taskfile=./wiki.1M.nostopwords.term.tasks
ip=127.0.0.1
port=7777
# dir=res/nice10_LC_batch/
# dir=res/chrt10_LC_batch/
# dir=res/nice_10_LC_cgroup_for_batch/
# dir=res/test/
# dir=res/test_batch_dedicate/
# dir=res/schedule/nice10/batch1/

# dir=res/motivation/smt/LC/
# dir=res/motivation/smt/LC_batch/

# dir=res/febrary/1/smt/lc/

# dir=res/febrary/28/basic/
# dir=res/febrary/28/schedule/
# dir=res/febrary/28/batch/
# dir=res/febrary/28/batch2/
# dir=res/march/1/batch/
# dir=res/march/1/batch2/
# dir=res/march/1/batch3/
# dir=res/march/1/batch4/
# dir=res/march/1/batch4p2/
# dir=res/march/1/batch4p3/
# dir=res/march/1/nobatch/
# dir=res/march/1/nobatchp3/
# dir=res/march/1/lsalone/
# dir=res/test/

# dir=res/march/1/newbatch/

# dir=res/march/2/batch/
# dir=res/march/2/lsalone/

# dir=res/march/14/lsalone/

dir=res/test/

rm -rf $dir
mkdir -p $dir

iter=20
iter=2
python2 sendTasks.py $taskfile $ip $port 200 1000000 200000 "/tmp/warmup-100-5" 5 "shuffle"

# for ((i = 50; i <= 400; i += 50)); do
# for ((i = 10; i <= 100; i += 10)); do
for ((i = 200; i <= 400; i += 50)); do
    python2 sendTasks.py $taskfile $ip $port $i 1000000 200000 "$dir/log-$i-$iter" $iter "shuffle"
done
