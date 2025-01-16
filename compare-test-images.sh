#!/bin/sh

# Use this script to compare current results against previously "blessed" results.

for x in app/src/androidTest/res/screenshots/*; do
  f=$(basename "$x")
  [ ! -e "app/build/outputs/managed_device_android_test_additional_output/debug/pixel9api35/${f}scaled.png" ] \
    && magick "app/build/outputs/managed_device_android_test_additional_output/debug/pixel9api35/$f" \
      -scale 25% "app/build/outputs/managed_device_android_test_additional_output/debug/pixel9api35/${f}scaled.png"
  echo -n "comparing $f.. "
  magick compare -metric mae "$x" \
    "app/build/outputs/managed_device_android_test_additional_output/debug/pixel9api35/${f}scaled.png" "/tmp/diff_$f"
  echo
done
