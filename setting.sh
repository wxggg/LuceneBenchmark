#!/bin/bash

HOME="/run/media/wxg/Data/git"

LUCENE="${HOME}/lucene-solr/lucene"
LUCENE_BUILD="${LUCENE}/build"

LUCENEUTIL="${HOME}/luceneutil"
LUCENEUTIL_BUILD="${LUCENEUTIL}/build"

INDEX_DATA="${HOME}/data/enwiki-20120502-lines-1k.txt"
INDEX_DIR="${HOME}/data/indices"

SERVER_IP="server:127.0.0.1:7777"
LOG_FILE="${HOME}/data/logs"

SEARCH_THREAD="1"

JDK_PATH="/usr/lib/jvm/java-11-openjdk"
