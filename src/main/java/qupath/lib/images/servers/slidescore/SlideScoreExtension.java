package qupath.lib.images.servers.slidescore;

import javafx.beans.binding.Bindings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.tools.MenuTools;
import qupath.lib.images.servers.ImageServer;

import java.awt.image.BufferedImage;
import java.util.concurrent.Callable;

/**
 * Extension that adds a command to import TMA cores' positions from a slide in Slide Score.
 *
 * @author Jan Hudecek
 *
 */
public class SlideScoreExtension implements QuPathExtension {

        private final static Logger logger = LoggerFactory.getLogger(SlideScoreExtension.class);

        @Override
        public void installExtension(QuPathGUI qupath) {

            var actionWriter = ActionTools.createAction(new SlideScoreImportTMAsCommand(qupath), "Import TMA Positions from Slide Score");
            actionWriter.setLongText("Import positions of TMA cores on a TMA slide from the original Slide Score slide");
            actionWriter.disabledProperty().bind(
                    Bindings.createObjectBinding(
                            new Callable<Boolean>() {
                                @Override
                                public Boolean call() throws Exception {
                                    var data = qupath.getImageData();
                                    if (data == null) return true;
                                    ImageServer<BufferedImage> server = data.getServer();
                                    return !(server instanceof SlideScoreImageServer);
                                    }
                            },
                            qupath.imageDataProperty()
                    ));
            MenuTools.addMenuItems(
                    qupath.getMenu("TMA", true),
                    actionWriter);

            var actionWriter2 = ActionTools.createAction(new SlideScoreUploadAnnotationsCommand(qupath), "Upload selected annotations to Slide Score");
            actionWriter2.setLongText("Upload selected annotations to an existing question on the original Slide Score slide");
            actionWriter2.disabledProperty().bind(
                    Bindings.createObjectBinding(
                            new Callable<Boolean>() {
                                @Override
                                public Boolean call() throws Exception {
                                    var data = qupath.getImageData();
                                    if (data == null) return true;
                                    ImageServer<BufferedImage> server = data.getServer();
                                    return !(server instanceof SlideScoreImageServer);
                                }
                            },
                            qupath.imageDataProperty()
                    ));
            MenuTools.addMenuItems(
                    qupath.getMenu("Objects", true),
                    actionWriter2);

            var actionWriter3 = ActionTools.createAction(new SlideScoreImportAnswersCommand(qupath), "Download annotations from Slide Score");
            actionWriter3.setLongText("Download annotations from the original Slide Score slide and create QuPath objects from them");
            actionWriter3.disabledProperty().bind(
                    Bindings.createObjectBinding(
                            new Callable<Boolean>() {
                                @Override
                                public Boolean call() throws Exception {
                                    var data = qupath.getImageData();
                                    if (data == null) return true;
                                    ImageServer<BufferedImage> server = data.getServer();
                                    return !(server instanceof SlideScoreImageServer);
                                }
                            },
                            qupath.imageDataProperty()
                    ));
            MenuTools.addMenuItems(
                    qupath.getMenu("Objects", true),
                    actionWriter3);

        }

        @Override
        public String getName() {
            return "Slide Score data connection";
        }

        @Override
        public String getDescription() {
            return "Allows downloading and uploading of annotations from the original Slide Score slide and working with TMA cores";
        }

    }

