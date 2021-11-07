# SAB-Viewer #

This project is aimed to develop a viewer for large text files.
It should open and be usable quickly, even when opening text files that are multiple GBs in size.

## TODO ##
* Add status bar in GUI that shows "scanning...", updating count of line/characters, current position
* Support switching between Java and System look-and-feel
* Add scrollbars
* Support scrolling with mouse wheel (also shift + wheel to scroll left, right)
* Do we need to handle navigation special while scanning is still in progress? 
  E.g. Should we prohibit using CTRL+END or should it just jump to the latest scanned line?
* Add print-outs/logging, with statistics about performance: size of file, time to scan it, time to seek etc... Or maybe put it into GUI
* support specification of encoding to open file
* maybe support auto detection of encoding
* support simple search (jump to next, done in separate thread, can be canceled, if takes long)
* support more advanced search (make list, remember multiple search results etc.)
* handle tabs (\t) correctly in fixed width views (count them as multiple characters 
  - there is corresponding setting in JTextArea)
* support "wrap lines".
* improve error handling
* Make end of lines and end of file visible. Different approaches can be used.
* support multi-line selection for copy-paste for long lines... what about "very long" lines?
* possibly bundle it with JDK or build native image
* provide TUI (to make it usable via SSH).
* probably provide mode "with cursor"
* probably support work with binary files (e.g. hex view, search)
* test with very long lines (GB long)
* Test with very large files. Probably ~100Gb
* Do we need it?: Make "read" run async (think, how to do it in smart way, so that we don't oversubscribe both:
  "read" and "update" - skip intermediate request, which did not start yet).
* fix scrolling, so that it does not skip updates (seems to be not relevant
  for now with new reader... but probably it will be with bigger files)
* find a way to make scrolling work smoother even at the end of the file
* Do we need to persist any settings? Working dir, look-and-feel something else?
