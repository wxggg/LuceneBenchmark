#!/bin/bash
source $(dirname $0)/setting.sh

JARPATH=target/LuceneTool-1.0-SNAPSHOT.jar:target/dependency/lucene-core-8.3.0.jar:
JARPATH+=target/dependency/lucene-analyzers-common-8.3.0.jar:target/dependency/lucene-facet-8.3.0.jar:
JARPATH+=target/dependency/lucene-codecs-8.3.0.jar:target/dependency/lucene-grouping-8.3.0.jar:
JARPATH+=target/dependency/lucene-queryparser-8.3.0.jar:target/dependency/lucene-suggest-8.3.0.jar:
JARPATH+=target/dependency/lucene-highlighter-8.3.0.jar


# chrt -10
nice -n -10 java -server -XX:ParallelGCThreads=1 -Xms2g -Xmx2g -XX:-TieredCompilation \
    -XX:+HeapDumpOnOutOfMemoryError -Xbatch -Djava.library.path=./target/classes/perf:./target/classes/scheduler: \
    -classpath ${JARPATH} perf.SearchPerfTest \
    -dirImpl MMapDirectory \
    -indexPath ${INDEX_DIR} \
    -analyzer StandardAnalyzer \
    -taskSource ${SERVER_IP} \
    -searchThreadCount 0 \
    -field body \
    -staticSeed \
    -4 \
    -seed 0 \
    -similarity BM25Similarity \
    -commit multi \
    -hiliteImpl FastVectorHighlighter \
    -log ${LOG_FILE} \
    -topN 10 \
    -pk
