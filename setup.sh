#!/bin/bash
source $(dirname $0)/setting.sh

gcc -O2 -g -D_GNU_SOURCE -fPIC -shared -std=c99 -fPIC \
    ./src/main/java/perf/elfen_signal.c \
    -o target/classes/perf/libelfen_signal.so \
    $@ \
    -I"${JDK_PATH}"/include \
    -I"${JDK_PATH}"/include/linux/ \
    -pthread -lpfm
