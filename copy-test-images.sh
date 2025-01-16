#!/bin/sh

# Use this script to "bless" the current screenshot results.

rm app/test/screenshots/* 2>/dev/null
cp app/build/outputs/managed_device_android_test_additional_output/debug/pixel9api35/* app/test/screenshots/
for x in app/test/screenshots/*; do
  magick "$x" -scale 25% "${x}scaled.png"
  mv "${x}scaled.png" "$x"
done
rm app/build/outputs/managed_device_android_test_additional_output/debug/pixel9api35/* 2> /dev/null
