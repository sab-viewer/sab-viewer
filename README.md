# SAB-Viewer #

This project is aimed to develop a viewer for large text files.
It should open and be usable quickly, even when opening text files that are multiple GBs in size.

## Concept ##

Basic fault of many contemporary viewers for text files is that they try to buffer the entire file content.
For very large files, this simply fails (for various reasons, like running out of RAM). Also, they often only start to be usable after loading the
entire file. Opening large files is often not practical or even possible. Most viewers also have problem with very long lines.
There are likely also good reasons for this behavior, as most viewers are in fact editors and want to keep the editor-content independent form the file.

When limiting to simple viewing, a different approach can be used and is implemented here.
The entire buffering stays where it belongs and is handled by the Operating System.
The current view of the file is then reloaded from the file on demand (e.g. when the view is moving).

To facilitate this, the program is split into three (major) components (and threads):
 * UI Handling - on startup this thread just displays blank viewer 
 * File Scanner - this background thread scans the file to collect byte level offsets of reference points (like line-endings)
 * Controller - to manage and store the reference points

As the scanner walks the file, it sends the discovered reference points to the Controller.
Controller uses the reference points to figure out what parts of the file need to be reread when the view changes.
E.g. when the view moves, it selects relevant reference points (e.g. where displayed lines start), takes their byte level offsets,
seeks the file to each offset and rereads the content from there.
Then the updated lines are send to the UI to display.

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
* (Settings) maybe support auto-detection of encoding
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
