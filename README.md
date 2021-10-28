# SAB-Viewer #

This project is aimed to develop a viewer for large text files.

Developer of the initial version of the source code is Tom Herrmann.

## ToDos ##
* Add print-outs/logging, with statistics about performance: size of file, time to scan it, time to seek etc...
* abstract "viewer" into separate class, so that it can be used with different kinds of GUI
* fix scrolling, so that it does not skip updates
* find a way to make scrolling work smoother even at the end of the file
* test with very long lines (GB long)
* add goto
* support END and HOME and CTRL-END and CTRL-HOME
* support "open file"
* support specification of encoding to open file
* maybe support auto detection of encoding
* support weird kinds of line endings that nobody should use (Windows, MacOS classic)
* support simple search (jump to next, done in separate thread, can be canceled, if takes long)
* support more advanced search (make list, remember multiple search results etc.)
* handle tabs (\t) correctly in fixed with views (count them as multiple characters)
* support "wrap lines".
* improve error handling
* support multi-line selection for copy-paste for long lines... what about "very long" lines?
* possibly bundle it with JDK or build native image
* provide TUI (to make it usable via SSH).
* probably provide mode "with cursor"
* probably support work with binary files (e.g. hex view, search)
