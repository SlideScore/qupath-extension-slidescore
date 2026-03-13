# QuPath extension to support opening files from a Slide Score server

This is a plugin for the image analysis software [QuPath](https://qupath.github.io) (v0.6.0+). It allows directly opening images that are stored in the digital pathology slide management software [Slide Score](https://www.slidescore.com), downloading annotations from there, uploading annotations and detections back and working with TMAs.

# Installation

Just drag and drop the jar over a running QuPath instance.

# Usage example

    import qupath.lib.gui.QuPathGUI
    import qupath.lib.scripting.QP
    import qupath.lib.roi.interfaces.ROI
    import qupath.lib.images.servers.slidescore.SlideScoreUploadAnnotationsCommand
    import qupath.lib.images.servers.slidescore.SlideScoreImportTMAsCommand
    import qupath.lib.images.servers.slidescore.SlideScoreImportAnswersCommand

    //TODO: specify annotation questions to use as source and destination
    def sourceQuestion = "Mark interesting area"
    def destinationQuestion = "Positive_Cells"

    //TODO: specify account under which the ROI annotation was made
    def sourceAccount = 'demo@example.com'

    qupathGUI = QuPathGUI.getInstance()
    def slideScoreImportTMAsCommand = new SlideScoreImportTMAsCommand(qupathGUI)

    def project = QP.getProject()

    // go over all images in the project
    for (projectImageEntry in project.getImageList()) { 
        def imageData = projectImageEntry.readImageData()
        def server = imageData.getServer()

        // load TMA core locations from Slide Score
        slideScoreImportTMAsCommand.run(imageData)

     
        def slideScoreImportAnswersCommand = new SlideScoreImportAnswersCommand(qupathGUI)
        slideScoreImportAnswersCommand.setQuestion(sourceQuestion)
        //optionally get only answers from a user
        slideScoreImportAnswersCommand.setEmail(sourceAccount)    
        //optionally disable automatic adding of annotations to the hierarchy
        slideScoreImportAnswersCommand.dontAddAnnotations = true;
        slideScoreImportAnswersCommand.run(imageData)
        var logger = LoggerFactory.getLogger("testscript")
        //print answers
        for (answer in slideScoreImportAnswersCommand.answers) {
             logger.info('value:' + answer.value);   
        }
        //or use
        //slideScoreImportAnswersCommand.pathObjects
        // for QuPath's PathObject shapes
        //or use 
        //slideScoreImportAnswersCommand.annotations
        // for Slide Score format:
        /*
            public class SlideScoreAnnotation {
                public class Point
                {
                    public int x,y;
                }
     
                public class Label
                {
                    public int x,y, fontSize;
                    public String label, whenToShow;
                }
     
                public int x,y;
                public Point2 center, size, corner;
                public Point2[][] positivePolygons, negativePolygons;
                public Point2[] points;
                public String area, type, modifiedOn;
                public Label label;
            }*/

        //TODO: perform analysis, detect cells, ...

        // Send Point Annotation to Slide Score
        def hierarchy = QP.getCurrentHierarchy()
        def annotations = hierarchy.getAnnotationObjects()
        def pointAnnotation = annotations.find() {it.getROI().getRoiType() == ROI.RoiType.POINT}
        def sideScoreUploadAnnotationsCommand = new SlideScoreUploadAnnotationsCommand()
        sideScoreUploadAnnotationsCommand.submitAnnotations(imageData, [pointAnnotation], destinationQuestion)
    }

# Building

Clone this repo into the qupath 0.6.0 repo and add to ``settings.gradle.kts``:

* `include("qupath-extension-slidescore")` below `include("qupath-extension-openslide")`
* `mavenCentral()` below `maven("https://maven.scijava.org/content/groups/public/")`
