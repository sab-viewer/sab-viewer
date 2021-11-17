# SAB-Viewer #

This project is aimed to develop a viewer for large text files.
It should open and be usable quickly, even when opening text files that are multiple GBs in size.

## TODO ##
* Update GUI during scanning using polling from GUI side. This is supposed to fix redraw smoothness problems on Windows while scanning.
* Add settings dialog (some settings are listed below).
* (Settings) Support switching between Java and System look-and-feel
* (Settings) support specification of encoding to open file
* (File-Statistics dialog) Add print-outs/logging, with statistics about performance: size of file, time to scan it, time to seek etc... Or maybe put it into GUI
* Add scrollbars
* Support scrolling with mouse wheel (also shift + wheel to scroll left, right)
* Add "About" dialog.
* (Settings) maybe support auto detection of encoding
* support simple search (jump to next, done in separate thread, can be canceled, if takes long)
* support multi-line selection for copy-paste for long lines... what about "very long" lines?
* Do we need to handle navigation special while scanning is still in progress?
  E.g. Should we prohibit using CTRL+END or should it just jump to the latest scanned line?
* support more advanced search (make list, remember multiple search results etc.)
* handle tabs (\t) correctly in fixed width views (count them as multiple characters 
  - there is corresponding setting in JTextArea)
* support "wrap lines".
* improve error handling
* Make end of lines and end of file visible. Different approaches can be used.
* possibly bundle it with JDK or build native image
* provide TUI (to make it usable via SSH).
* probably provide mode "with cursor"
* probably support work with binary files (e.g. hex view, search)
* test with very long lines (GB long)
* Test with very large files. Probably ~100Gb
* find a way to make scrolling work smoother
* Do we need to persist any settings? Working dir, look-and-feel something else?
