#!/bin/sh

# Use this script to "bless" the current screenshot results.

rm app/src/androidTest/res/screenshots/* 2>/dev/null
cp app/build/outputs/managed_device_android_test_additional_output/debug/pixel9api35/* app/src/androidTest/res/screenshots/
for x in app/src/androidTest/res/screenshots/*; do
  magick "$x" -scale 25% "${x}scaled.png"
  mv "${x}scaled.png" "$x"
done
