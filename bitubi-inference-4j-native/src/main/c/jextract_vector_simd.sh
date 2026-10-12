#!/bin/bash

gcc -fPIC -O3 -march=native -shared -o libjlama.so vector_simd.c

# Generate Java source code
/usr/local/jextract-20/bin/jextract --source \
  --output ../java20 \
  -t ao.bitubi.inference4j.tensor.operations.cnative \
  -I . \
  -l jlama \
  --header-class-name NativeSimd \
  vector_simd.h

/usr/local/jextract-21/bin/jextract --source \
  --output ../java21 \
  -t ao.bitubi.inference4j.tensor.operations.cnative \
  -I . \
  -l jlama \
  --header-class-name NativeSimd \
  vector_simd.h

/usr/local/jextract-22/bin/jextract \
  --output ../java22 \
  -t ao.bitubi.inference4j.tensor.operations.cnative \
  -I . \
  -l jlama \
  --header-class-name NativeSimd \
  vector_simd.h
