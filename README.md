
Lucene Performance Benchmark Tool

# Introduction

This repository is used to bench the performance of lucene.

Some codes come from https://github.com/mikemccand and https://github.com/yangxi , for which all rights reserved to them.

# Background

This project aims to solve the problem when latency-critical service works together with batch tasks in the same server machine, a huge tail latency increment occurs.

So in this project a new method is raised to colocation latency-critical service and batch tasks, while respond back quickly.

# Usage

```
git clone git@github.com:wxggg/LuceneBenchmark.git

cd LuceneBenchmark

mvn clean

./setup.sh

./compile.sh
```

