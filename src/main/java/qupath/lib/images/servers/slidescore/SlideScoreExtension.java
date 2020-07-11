package qupath.lib.images.servers.slidescore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.tools.MenuTools;

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
            actionWriter.disabledProperty().bind(qupath.imageDataProperty().isNull());
            MenuTools.addMenuItems(
                    qupath.getMenu("TMA", true),
                    actionWriter);

        }

        @Override
        public String getName() {
            return "Slide Score import TMA Cores";
        }

        @Override
        public String getDescription() {
            return "Allows import of positions of TMA cores on a TMA slide from the original Slide Score slide";
        }

    }

