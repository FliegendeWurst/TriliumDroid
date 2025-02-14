# Trilium Notes for Android

<img align="right" src="./fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" />

Unofficial port of [Trilium Notes](https://github.com/TriliumNext/Notes) to Android. Related: https://github.com/TriliumNext/Notes/issues/72

[<img src="https://github.com/user-attachments/assets/38acb15c-dbe2-4bc1-9f8b-1539654d3641" width="170">](https://apt.izzysoft.de/fdroid/index/apk/eu.fliegendewurst.triliumdroid)

## Features

- Support for version 0.91.6, 0.90.12 and 0.63.7 of sync protocol + database schema
- Sync with other Trilium instance (push + pull)
- Displaying/editing/sharing note content
- Displaying/editing note attributes (labels and relations)
- Attribute inheritance and templating
- Displaying note icon
- Displaying note paths
- Displaying/collapsing/expanding note tree
- Navigating using internal links
- Browsing external links
- Jump to note dialog
- Local/global note map view
- Basic Scripting
- Upgrading to newer database versions / Trilium versions
- Sharing content to save as new note
- Support for Android 8.0+

## Usage

1. Download the app from [IzzyOnDroid](https://apt.izzysoft.de/fdroid/index/apk/eu.fliegendewurst.triliumdroid).
2. Install the APK. You need to enable installing apps from unknown sources.
3. Open the app and configure your sync server (hostname, port, password).
4. Go back, wait until sync is finished.

Then, you can:

- inspect and navigate the note tree by opening the left side bar
- view and modify note attributes by opening the right side bar
- jump to notes using the floating action button in the bottom right corner
- edit notes using the edit button in the top action bar

There is also some preliminary support for scripting, but it doesn't work exactly like the Trilium API at the moment.

## TODO
- #sorted attribute
- Search
- Folder view of child nodes
- Jump to note dialog: smart sort
- Modifying note icon
- Advanced Scripting
- Dark Theme
- Included notes
- Encrypted notes
- Undeleting content
- Erasing notes
- Support for Android 7.1, 7.0 (likely to come eventually [1](https://stackoverflow.com/questions/57203186/datetimeformatter-is-not-working-in-android-versions-lower-than-8))
- Support for Android <= 6.0 (very low priority)
- Special layout for tablet screens
- F-Droid compatible gradle repositories (AztecEditor is not in Maven Central)
- Encrypted local database (maybe): https://github.com/sqlcipher/android-database-sqlcipher

## Contribute

Please report bugs and missing features.

Translations are done via [Weblate](https://hosted.weblate.org/projects/triliumdroid/app/).

### Testing

- Gradle action `pixel9api35DebugAndroidTest`
- Scripts `copy-test-images.sh`, `compare-test-images.sh` in `app/test`

## License

Copyright © 2023 Arne Keller

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.

Parts of this program that are derived from zadam's [Trilium Notes](https://github.com/zadam/trilium/), the AGPL-3.0 applies.  
The MIT License applies to the [boxicons](https://boxicons.com/) included in the project (see `boxicons_LICENSE.txt`).  
The GPL-3.0 license applies to the styles derived from [Simple-Commons](https://github.com/SimpleMobileTools/Simple-Commons).  
The MPL-2.0 license applies to the included [AztecEditor-Android](https://github.com/wordpress-mobile/AztecEditor-Android/).  
For other included libraries, their respective license applies.  
