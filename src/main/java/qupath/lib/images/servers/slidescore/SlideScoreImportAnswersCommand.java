package qupath.lib.images.servers.slidescore;

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import qupath.lib.geom.Point2;
import qupath.lib.gui.ExtensionClassLoader;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.extensions.Subcommand;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.PointsROI;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.awt.*;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;

/**
 * Command to import answers (results) from Slide Score for a slide.
 *
 * @author Jan Hudecek
 *
 */

@Command(name = "slidescore-importanswers", description = "Import answers (results) from Slide Score for a slide.", sortOptions = false)
public class SlideScoreImportAnswersCommand implements Runnable, Subcommand {

    private static final Logger logger = LoggerFactory.getLogger(SlideScoreImportTMAsCommand.class);

    private QuPathGUI qupath;

    /**
     * Constructor.
     *
     * @param qupath current QuPath instance.
     */
    public SlideScoreImportAnswersCommand(final QuPathGUI qupath) {
        this.qupath = qupath;
    }

    public SlideScoreImportAnswersCommand() {
    }

    private String presetQuestion;
    private String presetColor;
    private String presetEmail;
    private Boolean setNames = false;

    /**
     * Import only answers for this question. Leave empty to query the user with a list of questions
     *
     */
    public void setQuestion(String question) {
        presetQuestion = question;
    }


    /**
     * Import only answers from this account (email address). Leave empty to query the user with a list of accounts that answered the selected question(s).
     *
     */
    public void setEmail(String email) {
        presetEmail = email;
    }

    /**
     * Disable setting names on the annotations in the form of [question] by [account]
     *
     */
    public void disableNames() {
        setNames = false;
    }

    @Override
    public void run() {
        QuPathViewer viewer = qupath.getViewer();
        ImageData<BufferedImage> imageData = viewer.getImageData();
        run(imageData);
    }

    public void run(ImageData<BufferedImage> imageData) {
        QuPathViewer viewer = qupath.getViewer();
        if (imageData == null) {
            Dialogs.showNoImageError("Slide Score Answers Import");
            return;
        }
        try {
            ImageServer<BufferedImage> server = imageData.getServer();
            if (!(server instanceof SlideScoreImageServer)) {
                Dialogs.showErrorMessage("Slide Score Answers Import", "This command only works for Slide Score slides.");
                return;
            }
            var ssServer = (SlideScoreImageServer) server;
            try {
                var annoQs = ssServer.getAnnotationQuestions();
                if (presetQuestion == null && annoQs.length == 0) {
                    Dialogs.showErrorMessage("Slide Score annotation upload", "No annotation type questions found.");
                    return;
                }

                if (presetQuestion == null) {
                    String firstq = annoQs[0].toString();
                    if (annoQs.length > 1)
                        presetQuestion = Dialogs.showChoiceDialog("Slide Score importing annotations", "Select question to download the annotations from. Or press cancel to import all annotations", annoQs, firstq);
                    else
                        presetQuestion = firstq;
                }
                var answers = ssServer.getAnswers(presetQuestion, presetEmail);
                if (answers.length == 0) {
                    Dialogs.showInfoNotification("Slide Score Answers Import", "No answers found.");
                    return;
                }
                if (presetEmail == null) {
                    var emails = new HashSet<String>();
                    for (var i = 0; i < answers.length; i++)
                        emails.add(answers[i].email);
                    var emailsList = emails.toArray(new String[0]);
                    String firste = emailsList[0].toString();
                    if (emailsList.length > 1) {
                        presetEmail = Dialogs.showChoiceDialog("Slide Score importing annotations", "Select email from which to download the annotations. Or press cancel to import all annotations", emailsList, firste);
                        if (presetEmail != null) {
                            var newAnswers = new ArrayList<SlideScoreAnswer>();
                            for (var i = 0; i < answers.length; i++)
                                if (answers[i].email.compareToIgnoreCase(presetEmail) == 0)
                                    newAnswers.add(answers[i]);
                            answers = newAnswers.toArray(new SlideScoreAnswer[0]);
                        }
                    } else {
                        presetEmail = firste;
                    }
                }
                //go through the results, if you find a json array, parse it as annotation and add it to current annotations
                int count = 0;
                for (var i = 0; i < answers.length; i++) {
                    var a = answers[i];
                    if (a.value.startsWith("[{") && a.value.endsWith("}]")) {
                        try {
                            var json = JsonParser.parseString(a.value).getAsJsonArray();
                            var typeObject = new SlideScoreAnnotation[0];

                            var annotations = GsonTools.getInstance().fromJson(json, (Class<SlideScoreAnnotation[]>) typeObject.getClass());
                            importAnnotation(annotations, imageData, setNames ? a.question + " by " + a.email : null, a.color);
                            count++;
                        } catch (JsonSyntaxException ex) {
                            throw new IOException("Parsing of answers failed", ex);
                        }

                    }
                }
                if (count > 0)
                    Dialogs.showInfoNotification("Slide Score Answers Import", "Imported "+count+" annotations.");
                presetEmail = null;
                presetQuestion = null;

            } catch (Exception ex) {
                Dialogs.showErrorMessage("Slide Score Answers Import", "Getting answers failed, see log.");
                logger.error("Getting answers failed", ex);
            }
        } catch (java.lang.NoSuchMethodError ex) {
            Dialogs.showErrorMessage("Slide Score Answers Import", "It seems that multiple versions of the Slide Score plugin are loaded. Can you please remove older versions of the plugin from the extensions directory and leave only qupath-extension-slidescore-0.3.1.jar");
            try {
                Thread.sleep(3000);
                var extensionClassLoader = ExtensionClassLoader.getInstance();;
                var dir = extensionClassLoader.getExtensionsDirectory();
                Desktop.getDesktop().open(dir.toFile());
            } catch (Exception e) {
            }
        }
    }

    private void importAnnotation(SlideScoreAnnotation[] annotations, ImageData<BufferedImage> imageData, String name, Integer color) {
		if (annotations.length > 0 && annotations[0].type == null) {
            var points = new ArrayList<Point2>();
			for (var i=0;i<annotations.length;i++) {
				points.add(new Point2(annotations[i].x, annotations[i].y));
			}
            var roi = ROIs.createPointsROI(points, ImagePlane.getDefaultPlane());
			var annotation = PathObjects.createAnnotationObject(roi, PathClassFactory.getPathClass("PathAnnotationObject"));
			if (setNames)
				annotation.setName(name);
            if (color != null)
                annotation.setColorRGB(color);
			imageData.getHierarchy().addPathObject(annotation);
			return;
		}

        for (var i=0;i<annotations.length;i++) {
            var a = annotations[i];
            ROI roi = null;
            switch (a.type.toLowerCase()) {
                case "rect": {
                    // Create a new Rectangle ROI
                    roi = ROIs.createRectangleROI(a.corner.getX(), a.corner.getY(), a.size.getX(), a.size.getY(), ImagePlane.getDefaultPlane());
                    break;
                }
                case "ellipse": {
                    // Create a new ROI
                    roi = ROIs.createEllipseROI(a.center.getX() - a.size.getX(), a.center.getY() - a.size.getY(), a.size.getX() * 2, a.size.getY() * 2, ImagePlane.getDefaultPlane());

                    break;
                }
                case "polygon": {
                    // Create a new ROI
                    roi = ROIs.createPolygonROI(Arrays.asList(a.points), ImagePlane.getDefaultPlane());
                    break;
                }
                case "brush": {
                    var area = new Area();
                    for (var j = 0; j < a.positivePolygons.length; j++) {
                        // Create a new ROI
                        var roiPartial = ROIs.createPolygonROI(Arrays.asList(a.positivePolygons[j]), ImagePlane.getDefaultPlane());
                        area.add(new Area(roiPartial.getShape()));
                    }
                    for (var j = 0; j < a.negativePolygons.length; j++) {
                        var roiPartial = ROIs.createPolygonROI(Arrays.asList(a.negativePolygons[j]), ImagePlane.getDefaultPlane());
                        area.subtract(new Area(roiPartial.getShape()));
                    }
                    roi = ROIs.createAreaROI(area, ImagePlane.getDefaultPlane());
                    break;
                }
                default: {
                    logger.warn("Encountered unknown annotation type "+a.type);
                    break;
                }
            }
            if (roi != null) {
                // Create & new annotation & add it to the object hierarchy
                var annotation = PathObjects.createAnnotationObject(roi, PathClassFactory.getPathClass("PathAnnotationObject"));
                if (setNames)
                    annotation.setName(name);
                if (color != null)
                    annotation.setColorRGB(color);

                imageData.getHierarchy().addPathObject(annotation);
            }
        }
    }
}
