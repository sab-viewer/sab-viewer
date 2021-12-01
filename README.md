# SAB-Viewer #

This project is aimed to develop a viewer for large text files.
It should open and be usable quickly, even when opening text files that are multiple GBs in size.

## TODO ##
* improve simple search (wrap around, allow to cancel, select result, navigate with result in the middle instead of in top left corner).
* handle tabs (\t) correctly in fixed width views (count them as multiple characters 
  - there is corresponding setting in JTextArea).
* Add settings dialog (some settings are listed below).
* (Settings) Support switching between Java and System look-and-feel
* (Settings) support specification of encoding to open file
* (File-Statistics dialog) Add print-outs/logging, with statistics about performance: size of file, time to scan it, time to seek etc... Or maybe put it into GUI
* Add scrollbars
* Support scrolling with mouse wheel (also shift + wheel to scroll left, right)
* Add "About" dialog.
* (Settings) maybe support auto detection of encoding
* support multi-line selection for copy-paste for long lines... what about "very long" lines?
* support more advanced search (make list, remember multiple search results etc.)
* support "wrap lines" (or maybe not).
* improve error handling
* Make end of lines and end of file visible. Different approaches can be used.
* possibly bundle it with JDK or build native image
* provide TUI (to make it usable via SSH).
* probably provide mode "with cursor"
* probably support work with binary files (e.g. hex view, search)
* find a way to make scrolling work smoother
* (Settings) Do we need to persist any settings? Working dir, look-and-feel something else?
* test with very long lines (GB long)
* Test with very large files. Probably ~100Gb
* Add maven profiles to build with newer java versions (just trying to use JDK newer than 1.8 leads to errors for now).
* Fix maven warnings - I believe there were some
