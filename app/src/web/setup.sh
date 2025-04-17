#!/usr/bin/env bash

#
# How to use this script:
#
# 0. Remove the catch-all request blocker for esm.sh in NoteFragment, to temporarily allow internet access.
# 1. Run the app and open an excalidraw note.
# 2. Copy logcat output (include all lines with "intercept: esm.sh").
# 3. Save as intercepts.txt in project root.
# 4. Install curl, ripgrep and 7zip (coreutils and findutils are likely pre-installed), then run this script.
#

set -eo pipefail
rm *.zip https* || true
cat ../../../intercepts.txt | rg '(https://esm.sh/.+) ' --only-matching -r '$1' | sort | uniq | xargs -n 1 bash -c 'curl -L "$0" > "$(echo $0 | tr / _)"'
7z a -mm=Deflate -mfb=258 -mpass=15 -r web.zip https*
cp web.zip ../main/assets/
