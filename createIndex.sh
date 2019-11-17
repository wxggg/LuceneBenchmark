source $(dirname $0)/setting.sh

if [ -d "${INDEX_DIR}" ]; then
    echo "Index ${INDEX_DIR} exists."
    exit 0
fi

echo "Create index directory ${INDEX_DIR}. It may take hours!"

JARPATH=target/LuceneTool-1.0-SNAPSHOT.jar:target/dependency/lucene-core-8.3.0.jar:
JARPATH+=target/dependency/lucene-analyzers-common-8.3.0.jar:target/dependency/lucene-facet-8.3.0.jar:
JARPATH+=target/dependency/lucene-codecs-8.3.0.jar

java -classpath ${JARPATH} perf.Indexer \
    -dirImpl MMapDirectory \
    -indexPath ${INDEX_DIR} \
    -analyzer StandardAnalyzer \
    -lineDocsFile ${INDEX_DATA} \
    -docCountLimit 10000000 \
    -threadCount 4 \
    -maxConcurrentMerges 1 \
    -ramBufferMB 1024 \
    -maxBufferedDocs 1024 \
    -postingsFormat Lucene50 \
    -waitForCommit \
    -waitForMerges \
    -mergePolicy LogDocMergePolicy \
    -idFieldPostingsFormat Lucene50 \
    -grouping
