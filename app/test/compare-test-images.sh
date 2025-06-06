#!/usr/bin/env bash

# Use this script to compare current results against previously "blessed" results.

shopt -s nullglob

rm app/build/outputs/managed_device_android_test_additional_output/debug/pixel9api35/*scaled* 2>/dev/null

fail=0

for x in app/build/outputs/managed_device_android_test_additional_output/debug/pixel9api35/*.png; do
  f=$(basename "$x")
  [ ! -e "app/build/outputs/managed_device_android_test_additional_output/debug/pixel9api35/${f}scaled.png" ] \
    && magick "app/build/outputs/managed_device_android_test_additional_output/debug/pixel9api35/$f" \
      -scale 25% "app/build/outputs/managed_device_android_test_additional_output/debug/pixel9api35/${f}scaled.png"
  [[ ! -e "app/test/screenshots/$f" ]] && echo "WARNING: no reference screenshot for $f"
done

for x in app/test/screenshots/*; do
  f=$(basename "$x")
  echo -n "comparing $f.. "
  score=$(magick compare -metric mae "$x" \
    "app/build/outputs/managed_device_android_test_additional_output/debug/pixel9api35/${f}scaled.png" "/tmp/diff_$f" \
    2>&1 | cut -d\( -f2 | tr -d '\n' | tr -d ')')
  echo -n "$score. "
  awk "BEGIN {exit !($score >= 0.01)}" && echo -en "\033[31;1mFAIL!\033[0m" && fail=1
  echo
done

exit $fail
