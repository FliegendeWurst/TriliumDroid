#!/usr/bin/env bash

# Use this script to compare current results against previously "blessed" results.

fail=0

for x in app/build/outputs/managed_device_android_test_additional_output/debug/pixel9api35/*{1,2,3}.png; do
  f=$(basename "$x")
  [ ! -e "app/build/outputs/managed_device_android_test_additional_output/debug/pixel9api35/${f}scaled.png" ] \
    && magick "app/build/outputs/managed_device_android_test_additional_output/debug/pixel9api35/$f" \
      -scale 25% "app/build/outputs/managed_device_android_test_additional_output/debug/pixel9api35/${f}scaled.png"
done

for x in app/test/screenshots/*; do
  f=$(basename "$x")
  echo -n "comparing $f.. "
  magick compare -metric mae "$x" -subimage-search -dissimilarity-threshold 0.01 \
    "app/build/outputs/managed_device_android_test_additional_output/debug/pixel9api35/${f}scaled.png" "/tmp/diff_$f"
  [ $? -gt 1 ] && fail=1
  echo
done

exit $fail
