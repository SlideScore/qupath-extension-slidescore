package qupath.lib.images.servers.slidescore;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.extensions.Subcommand;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathROIObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.roi.*;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Command to import TMA cores' positions from a slide in Slide Score.
 *
 * @author Jan Hudecek
 *
 */
@Command(name = "slidescore-uploadannotations", description = "Upload selected annotations to an existing question on the original Slide Score slide", sortOptions = false)
public class SlideScoreUploadAnnotationsCommand implements Runnable, Subcommand {

    public interface TmaGetter{
        public String getAnswer(Collection<PathObject> annotations, TMACoreObject tma);
    }

    public static class TmaAnnoGetter {
        public String getAnswer( Collection<PathObject> annotations, TMACoreObject tma) {
            var geom = tma.getROI().getGeometry();
            var objects =  annotations.stream().filter(obj -> geom.contains(obj.getROI().getGeometry())).collect(Collectors.toList());
            return annotationsToJson(objects);
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(SlideScoreUploadAnnotationsCommand.class);

    private QuPathGUI qupath;

    private String presetQuestion;

    /**
     * Constructor.
     *
     * @param qupath current QuPath instance.
     */
    public SlideScoreUploadAnnotationsCommand(final QuPathGUI qupath) {
        this.qupath = qupath;
    }

    public SlideScoreUploadAnnotationsCommand() {
    }

    public void setQuestion(String question) {
        presetQuestion = question;
    }

    @Override
    public void run() {
        QuPathViewer viewer = qupath.getViewer();
        ImageData<BufferedImage> imageData = viewer.getImageData();
        run(imageData);
    }

    private static String serializePoint(double x, double y) {
        return "{\"x\":"+ (int)x + ", \"y\":" + (int)y+"}";
    }

    /**
     * Submit answer to a question for the whole slide
     *
     * @param question
     * @param answer
     * @return
     */
    public boolean submitSlideAnswer(String question, String answer) {
        QuPathViewer viewer = qupath.getViewer();
        return submitAnswer(viewer.getImageData(), question,  answer, null);
    }

    /**
     * Submit answer to a question for each core on a TMA slide
     *
     * @param question
     * @param fGetAnswer callback that returns answer for core
     * @return
     */
    public boolean submitTMAAnswer(String question, TmaGetter fGetAnswer) {
        QuPathViewer viewer = qupath.getViewer();
        return submitAnswer(viewer.getImageData(), question,  null, fGetAnswer);
    }

    /**
     * Submit answer to a question for each core on a TMA slide
     *
     * @param imageData
     * @param question
     * @param fGetAnswer callback that returns answer for core
     * @return
     */
    public static boolean submitTMAAnswer(ImageData<BufferedImage> imageData, String question, TmaGetter fGetAnswer)
    {
        return submitAnswer(imageData, question,  null, fGetAnswer);
    }

    /**
     * Submit answer to a question for the whole slide
     *
     * @param imageData
     * @param question
     * @param answer
     * @return
     */
    public static boolean submitSlideAnswer(ImageData<BufferedImage> imageData, String question, String answer)
    {
        return submitAnswer(imageData, question,  answer, null);
    }

    private static boolean submitAnswer(ImageData<BufferedImage> imageData, String question, String answer, TmaGetter fGetAnswer) {
        if (imageData == null) {
            Dialogs.showNoImageError("Slide Score answer upload");
            return false;
        }
        ImageServer<BufferedImage> server = imageData.getServer();
        if (!(server instanceof SlideScoreImageServer)) {
            Dialogs.showErrorMessage("Slide Score answer upload", "This command only works for Slide Score slides.");
            return false;
        }
        var ssServer = (SlideScoreImageServer) server;
        try {
            if (answer != null) {
                ssServer.postAnnotation(question, answer);
            } else {
                var tmagrid = imageData.getHierarchy().getTMAGrid();
                if (tmagrid != null) {
                    //start answer with TMAs: \n<coords>\nanswer
                    String json = "TMAs:";
                    for (var r = 0; r < tmagrid.getGridHeight(); r++) {
                        for (var c = 0; c < tmagrid.getGridWidth(); c++) {
                            var tma = tmagrid.getTMACore(r, c);
                            var tmaAnnosJson = fGetAnswer.getAnswer(null, tma);
                            if (tmaAnnosJson.equals(""))
                                continue;
                            json += getTmaCoords(tma, tmagrid, r, c) + tmaAnnosJson;
                        }
                    }
                    ssServer.postAnnotation(question, json);
                }
            }
            logger.info("Successfully uploaded answer");
            return true;
        } catch (Exception ex) {
            Dialogs.showErrorMessage("Slide Score answer upload", "Answer upload failed, see log.");
            logger.error(ex.getLocalizedMessage());
        }
        return false;
    }


    private static String getTmaCoords(TMACoreObject tma, TMAGrid tmagrid, int r, int c) {
        String json = "";
        if (tma.getMetadataKeys().contains("rotate")) {
            var rotate = Integer.parseInt(tma.getMetadataValue("rotate").toString());
            //90 -> i = h-y, j=x
            //180 -> i = w-x, j=h-y
            //270 -> i = y, j=w-x
            switch (rotate)
            {
                case 90:
                    json += "\n" + c + "," + (tmagrid.getGridHeight() - r - 1) + "\n";
                    break;
                case 180:
                    json += "\n" + (tmagrid.getGridHeight() - r - 1) + "," + (tmagrid.getGridWidth() - c - 1) + "\n";
                    break;
                case 270:
                    json += "\n" + (tmagrid.getGridWidth() - c - 1) + "," + r + "\n";
                    break;
            }
        } else {
            json += "\n" + r + "," + c + "\n";
        }
        return json;
    }

    public void run(ImageData<BufferedImage> imageData) {
        QuPathViewer viewer = qupath.getViewer();
        var annotations = viewer.getAllSelectedObjects();
        if (annotations.size() == 0)
        {
            Dialogs.showErrorMessage("Slide Score annotation upload", "You need to select annotations before uploading them. Go to the 'Annotations' tab and click an annotation from the list to select it, ctrl+click or shift+click to add it to selection.");
            return;
        }
        if (imageData == null) {
            Dialogs.showNoImageError("Slide Score annotation upload");
            return;
        }
        submitAnnotations(imageData, annotations, presetQuestion);
    }

    /**
     * Submit annotations as an answer under your account to a particular question in Slide Score. On TMAs submits each annotation to the TMA core that envelopes it
     *
     * @param imageData
     * @param annotations
     * @param question
     */
    public static void submitAnnotations(ImageData<BufferedImage> imageData, Collection<PathObject> annotations, String question) {
        ImageServer<BufferedImage> server = imageData.getServer();
        if (!(server instanceof SlideScoreImageServer)) {
            Dialogs.showErrorMessage("Slide Score annotation upload", "This command only works for Slide Score slides.");
            return;
        }
        var ssServer = (SlideScoreImageServer) server;
        try {
            var annoQs = ssServer.getAnnotationShapeQuestions();
            if (question == null && annoQs.length == 0) {
                Dialogs.showErrorMessage("Slide Score annotation upload", "No annotation type questions found.");
                return;
            }
            boolean hasPoints = false;
            boolean hasNonPoints = false;
            for (PathObject obj : annotations) {
                if (!(obj instanceof PathROIObject))
                    continue;
                var anno = (PathROIObject)obj;
                var roi = anno.getROI();
                if (roi instanceof PointsROI) {
                    hasPoints = true;
                } else {
                    hasNonPoints = true;
                }
            }
            if (hasPoints && hasNonPoints) {
                Dialogs.showErrorMessage("Slide Score annotation upload", "Cannot upload points annotations and other types together. Select only the points and upload it separately.");
                return;
            }
            String q;
            if (question == null) {
                String firstq = annoQs[0].toString();
                q = Dialogs.showChoiceDialog("Slide Score annotation upload", "Select question to upload the annotations to, your current answer will be overwritten.", annoQs, firstq);
            } else
                q = question;
            String json;
            var tmagrid = imageData.getHierarchy().getTMAGrid();
            if (tmagrid != null) {
                var resGetter = new TmaAnnoGetter();
                //start answer with TMAs: \n<coords>\nanswer
                json = "TMAs:";
                for (var r=0; r < tmagrid.getGridHeight(); r++) {
                    for (var c=0; c < tmagrid.getGridWidth(); c++) {
                        var tma = tmagrid.getTMACore(r, c);
                        var tmaAnnosJson = resGetter.getAnswer(annotations, tma);
                        if (tmaAnnosJson.equals("[]")) {
                            //logger.info("No results for TMA Core row "+Integer.toString(r) +" col: "+Integer.toString(c) +" skipping");
                            continue;
                        }
                        json += getTmaCoords(tma, tmagrid, r, c) + tmaAnnosJson;
                    }
                }
            } else {
                json = annotationsToJson(annotations);
            }
            if (tmagrid == null && json.length() > 100000)
                ssServer.postLargeAnnotation(q, json);
            else
                ssServer.postAnnotation(q, json);
            logger.info("Successfully uploaded annotations");
        } catch (Exception ex) {
            Dialogs.showErrorMessage("Slide Score annotation upload", "Annotation upload failed, see log.");
            logger.error(ex.getLocalizedMessage());
        }
    }

    /**
     * Submit annotations as an answer under your account to a particular question in Slide Score for a particular TMA core
     *
     * @param imageData
     * @param annotations
     * @param question
     * @param core
     */
    public static void submitTMAAnnotations(ImageData<BufferedImage> imageData, Collection<PathObject> annotations, String question, TMACoreObject core) {
        ImageServer<BufferedImage> server = imageData.getServer();
        if (!(server instanceof SlideScoreImageServer)) {
            Dialogs.showErrorMessage("Slide Score annotation upload", "This command only works for Slide Score slides.");
            return;
        }
        var ssServer = (SlideScoreImageServer) server;
        try {
            var qs = ssServer.getQuestions();
            var annoQs = new ArrayList<String>();
            for(var i=0;i<qs.length;i++) {
                var terms = qs[i].split(";");
                if (terms[1].equals("AnnoShapes"))
                    annoQs.add(terms[0]);
            }
            if (question == null && annoQs.size() == 0) {
                Dialogs.showErrorMessage("Slide Score annotation upload", "No annotation type questions found.");
                return;
            }
            boolean hasPoints = false;
            boolean hasNonPoints = false;
            for (PathObject obj : annotations) {
                if (!(obj instanceof PathROIObject))
                    continue;
                var anno = (PathROIObject)obj;
                var roi = anno.getROI();
                if (roi instanceof PointsROI) {
                    hasPoints = true;
                } else {
                    hasNonPoints = true;
                }
            }
            if (hasPoints && hasNonPoints) {
                Dialogs.showErrorMessage("Slide Score annotation upload", "Cannot upload points annotations and other types together. Select only the points and upload it separately.");
                return;
            }
            String q;
            if (question == null) {
                String[] annoqs =  annoQs.toArray(new String[0]);
                String firstq = annoQs.get(0).toString();
                q = Dialogs.showChoiceDialog("Slide Score annotation upload", "Select question to upload the annotations to, your current answer will be overwritten.", annoqs, firstq);
            } else
                q = question;
            String json;
            var tmagrid = imageData.getHierarchy().getTMAGrid();
            if (tmagrid != null) {
                var resGetter = new TmaAnnoGetter();
                //start answer with TMAs: \n<coords>\nanswer
                json = "TMAs:";
                for (var r=0; r < tmagrid.getGridHeight(); r++) {
                    for (var c=0; c < tmagrid.getGridWidth(); c++) {
                        var tma = tmagrid.getTMACore(r, c);
                        if (tma.getID() != core.getID()) continue;
                        var tmaAnnosJson = resGetter.getAnswer(annotations, tma);
                        if (tmaAnnosJson.equals("[]")) {
                            //logger.info("No results for TMA Core row "+Integer.toString(r) +" col: "+Integer.toString(c) +" skipping");
                            continue;
                        }
                        json += getTmaCoords(tma, tmagrid, r, c) + tmaAnnosJson;
                    }
                }
            } else {
                json = annotationsToJson(annotations);
            }
            ssServer.postAnnotation(q, json);
            if (json.length() > 0)
                Dialogs.showInfoNotification("Slide Score Annotation Upload", "Uploaded annotations.");

            logger.info("Successfully uploaded annotations");
        } catch (Exception ex) {
            Dialogs.showErrorMessage("Slide Score Annotation Upload", "Annotation upload failed, see log.");
            logger.error("Annotation upload failed", ex);
        }
    }


    private static String annotationsToJson(Collection<PathObject> objects) {
        boolean hasGeometry = false;
        StringJoiner mainsj = new StringJoiner(",");
        for (PathObject obj : objects) {
            if (!(obj instanceof PathROIObject))
                continue;
            var anno = (PathROIObject) obj;
            var roi = anno.getROI();
            if (roi instanceof EllipseROI) {
                EllipseROI ellipse = (EllipseROI) roi;
                mainsj.add("{ \"type\": \"ellipse\", \"center\": " +
                    serializePoint(ellipse.getCentroidX(), ellipse.getCentroidY()) +
                    ", \"size\": " + //subtract x2-x and y2-y
                     serializePoint(ellipse.getBoundsWidth() / 2, ellipse.getBoundsHeight() / 2) +
                    "}");
            } else if (roi instanceof PointsROI) {
                var points = (PointsROI)roi;
                for(var p :points.getAllPoints()) {
                    mainsj.add("{ \"type\": \"ellipse\", \"center\": " +
                            serializePoint(p.getX(), p.getY()) +
                            ", \"size\": " + //subtract x2-x and y2-y
                            serializePoint(10, 10) +
                            "}");
                }
            } else if (roi instanceof PolygonROI) {
                mainsj.add("{ \"type\": \"polygon\", \"points\": [" +
                        String.join(",", obj.getROI().getAllPoints().stream().map(c -> serializePoint(c.getX(), c.getY())).collect(Collectors.toList())) +
                        "]}");
            } else if (roi instanceof PolylineROI) {
                mainsj.add("{ \"type\": \"polyline\", \"points\": [" +
                        String.join(",", obj.getROI().getAllPoints().stream().map(c -> serializePoint(c.getX(), c.getY())).collect(Collectors.toList())) +
                        "]}");
            } else if (roi instanceof RectangleROI) {
                RectangleROI rect = (RectangleROI) roi;
                mainsj.add("{ \"type\": \"rect\", \"corner\": " +
                    serializePoint(rect.getConvexHull().getBoundsX(), rect.getConvexHull().getBoundsY()) +
                    ", \"size\": " +
                    serializePoint(rect.getConvexHull().getBoundsWidth(), rect.getConvexHull().getBoundsHeight()) +
                    "}");
            }
            else if (roi instanceof GeometryROI) {
                hasGeometry = true;
            }
        }
        if (hasGeometry) {
            ArrayList<Coordinate[]> positives = new ArrayList<Coordinate[]>();
            ArrayList<Coordinate[]> negatives = new ArrayList<Coordinate[]>();
            for (PathObject obj : objects) {
                if (!(obj instanceof PathROIObject))
                    continue;
                var anno = (PathROIObject) obj;
                var roi = anno.getROI();
                if (roi instanceof GeometryROI) {
                    GeometryROI geo = (GeometryROI) roi;
                    if (geo.getGeometry() instanceof Polygon) {
                        Polygon polygon = (Polygon) geo.getGeometry();
                        positives.add(polygon.getExteriorRing().getCoordinates());
                        for (int i=0;i<polygon.getNumInteriorRing();i++) {
                            negatives.add(polygon.getInteriorRingN(i).getCoordinates());
                        }
                    }
                }
            }
            StringJoiner sj = new StringJoiner(",");
            String brushJson = "{ \"type\": \"brush\", \"positivePolygons\": [";
            for (Coordinate[] coords: positives) {
                sj.add("[" +
                        String.join(",", Arrays.stream(coords).map(c -> serializePoint(c.getX(), c.getY())).collect(Collectors.toList())) +
                        "]");
            }
            brushJson += sj.toString();
            brushJson += "], \"negativePolygons\": [";
            sj = new StringJoiner(",");
            for (Coordinate[] coords: negatives) {
                sj.add("[" +
                    String.join(",", Arrays.stream(coords).map(c -> serializePoint(c.getX(), c.getY())).collect(Collectors.toList())) +
                "]");
            }
            brushJson += sj.toString();
            brushJson += "]}";
            mainsj.add(brushJson);
        }
        return "[" + mainsj.toString() + "]";
    }
}
