# Trilium Notes for Android

Unofficial port of [Trilium Notes](https://github.com/zadam/trilium) to Android.

## Working
- Sync with other Trilium instance (push + pull)
- Displaying/editing/sharing note content
- Displaying note attributes
- Attribute inheritance and templating
- Displaying note icon
- Displaying note paths
- Displaying/collapsing/expanding note tree
- Navigating using internal links
- Browsing external links
- Jump to note dialog
- Basic Scripting
- Upgrading to newer database versions / Trilium versions
- Support for Android 8.0+

## TODO
- Editing attributes
- #sorted attribute
- Search
- Share target (create note from received text)
- Folder view of child nodes
- Jump to note dialog: smart sort
- Modifying attributes, note title, note icon
- Editing note tree
- Advanced Scripting
- Note map
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

Please report bugs and missing features. I will soon setup a way to contribute translations. Any coding help is also welcome!

## Related issues

https://github.com/zadam/trilium/issues/3641

https://github.com/zadam/trilium/issues/3624

Fixes https://github.com/zadam/trilium/issues/3047 https://github.com/zadam/trilium/issues/2953 https://github.com/zadam/trilium/discussions/2751

https://github.com/zadam/trilium/discussions/2990

https://github.com/zadam/trilium/discussions/2939

https://github.com/zadam/trilium/discussions/3975

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

The AGPL 3.0 license applies for the parts of this program that are derived from zadam's [Trilium Notes](https://github.com/zadam/trilium/).  
The MIT License applies to the [boxicons](https://boxicons.com/) included in the project (see `boxicons_LICENSE.txt`).  
The GPL-3.0 license applies to the styles derived from [Simple-Commons](https://github.com/SimpleMobileTools/Simple-Commons).  
The MPL-2.0 license applies to the included [AztecEditor-Android](https://github.com/wordpress-mobile/AztecEditor-Android/).  
