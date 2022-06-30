# QuPath extension to support opening files from a Slide Score server

This is a plugin for the image analysis software [QuPath](https://qupath.github.io) (v0.3.0+). It allows directly opening images that are stored in the digital pathology slide management software [Slide Score](https://www.slidescore.com), downloading annotations from there, uploading annotations and detections back and working with TMAs.

# Installation

Just drag and drop the jar over a running QuPath instance.

# Building

Clone this repo into the qupath repo and include 

    project(':qupath-extension-slidescore').projectDir = "$rootDir/qupath-extension-slidescore" as File 
   
in ``settings.gradle``
