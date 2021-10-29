# SAB-Viewer #

This project is aimed to develop a viewer for large text files.
It should open and be usable quickly, even when opening text files that are multiple GBs in size.

## ToDos ##
* Add print-outs/logging, with statistics about performance: size of file, time to scan it, time to seek etc...
* Add status bar in GUI that shows "scanning...", updating count of line/characters, current position
* fix scrolling, so that it does not skip updates
* find a way to make scrolling work smoother even at the end of the file
* test with very long lines (GB long)
* add goto
* support END and HOME and CTRL-END and CTRL-HOME
* support "open file"
* support specification of encoding to open file
* maybe support auto detection of encoding
* support weird kinds of line endings that nobody should use (Windows, MacOS classic)
* add scrollbars
* support resizing of the text area
* support simple search (jump to next, done in separate thread, can be canceled, if takes long)
* support more advanced search (make list, remember multiple search results etc.)
* handle tabs (\t) correctly in fixed width views (count them as multiple characters)
* support "wrap lines".
* improve error handling
* Make end of lines and end of file visible. Different approaches can be used.
* support multi-line selection for copy-paste for long lines... what about "very long" lines?
* possibly bundle it with JDK or build native image
* provide TUI (to make it usable via SSH).
* probably provide mode "with cursor"
* probably support work with binary files (e.g. hex view, search)
* maybe implement cashing of bigger area around the visible window
  (to make work with neighbour text smoother). If we do it, we probably
  need to implement pagination to be able to "learn"/"forget" parts of
  text in pieces.
* maybe keep Reader object, maintaining file cursor at the end of current visible window (or cache)
  to avoid need to "skip" from beginning of the file. Probably switch to FileChannel/SeekableByteChannel to allow forward?
  and backward movement of the file cursor.
