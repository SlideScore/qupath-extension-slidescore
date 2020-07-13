package qupath.lib.images.servers.slidescore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.extensions.Subcommand;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.hierarchy.DefaultTMAGrid;
import picocli.CommandLine.Command;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Command to import TMA cores' positions from a slide in Slide Score.
 *
 * @author Jan Hudecek
 *
 */
@Command(name = "slidescore-importcores", description = "Import positions of TMA cores on a TMA slide from the original Slide Score slide", sortOptions = false)
public class SlideScoreImportTMAsCommand implements Runnable, Subcommand {

    private final static Logger logger = LoggerFactory.getLogger(SlideScoreImportTMAsCommand.class);

    private QuPathGUI qupath;

    /**
     * Constructor.
     *
     * @param qupath current QuPath instance.
     */
    public SlideScoreImportTMAsCommand(final QuPathGUI qupath) {
        this.qupath = qupath;
    }

    public SlideScoreImportTMAsCommand() {
    }

    @Override
    public void run() {
        QuPathViewer viewer = qupath.getViewer();
        ImageData<BufferedImage> imageData = viewer.getImageData();
        if (imageData == null) {
            Dialogs.showNoImageError("Slide Score TMA Import");
            return;
        }
        ImageServer<BufferedImage> server = imageData.getServer();
        if (!(server instanceof SlideScoreImageServer)) {
            Dialogs.showErrorMessage("Slide Score TMA Import", "This command only works for Slide Score slides.");
            return;
        }
        var ssServer = (SlideScoreImageServer) server;
        try {
            var poss = ssServer.getTMAPositions();
            if (poss.cores.length == 0) {
                Dialogs.showInfoNotification("Slide Score TMA Import", "No TMA core positions found.");
                return;
            }
            var width =ssServer.getOriginalMetadata().getWidth();
            int height = ssServer.getOriginalMetadata().getHeight();
            int maxRow = poss.cores[poss.cores.length - 1].row + 1;
            int maxCol = 0;
            for (int i = 0; i < poss.cores.length; ++i) {
                if (poss.cores[i].col > maxCol)
                    maxCol = poss.cores[i].col;
            }
            //row and col are 0-based
            maxCol++;
            if (poss.rotate != 0) {
                var newCores = new SlideScoreTmaPosition[poss.cores.length];
                //90 -> i = h-y, j=x
                //180 -> i = w-x, j=h-y
                //270 -> i = y, j=w-x
                //i is the new column index, j new row index
                for (var i=0;i<maxRow;i++) {
                    for (var j=0;j<maxCol;j++) {
                        switch ((int)poss.rotate)
                        {
                            case 90:
                                newCores[j*maxRow + i] = poss.cores[i*maxCol + (maxCol - j - 1)];
                                break;
                            case 180:
                                newCores[j*maxRow + i] = poss.cores[poss.cores.length - (j*maxRow + i) - 1];
                                break;
                            case 270:
                                newCores[j*maxRow + i] = poss.cores[(maxRow - i - 1)*maxCol + j];
                                break;
                        }

                    }
                }
                poss.cores = newCores;
                if (poss.rotate == 90 || poss.rotate == 270) {
                    var temp = maxCol;
                    maxCol = maxRow;
                    maxRow = temp;
                }
            }
            int coreDiameterPX;
            if (poss.coreRadiusUM <= 0) {
                //radius not set, use 2% of width as default
                var maxpx = Math.max(width, height);
                coreDiameterPX = 2*(int)(0.02 * maxpx);
            } else {
                coreDiameterPX = 2*(int)(poss.coreRadiusUM * ssServer.getOriginalMetadata().getAveragedPixelSize());
            }

            List<TMACoreObject> cores = new ArrayList<>(maxRow * maxCol);
            for (int i = 0; i < poss.cores.length; ++i) {
                var c = poss.cores[i];
                //coordinates are x,y * 1000 for slides wider than taller. For tall images both coordinates need to be scaled by w/h.
                double maxpx = Math.max(width, height);
                c.x /=  1000.0 * width/maxpx;
                c.y /=  1000.0 * width/maxpx;

                var co = PathObjects.createTMACoreObject(c.x, c.y, coreDiameterPX, c.x <= 0);
                co.setName(c.name);
                cores.add(co);
            }
            var myTMAGrid = DefaultTMAGrid.create(cores, maxCol);
            imageData.getHierarchy().setTMAGrid(myTMAGrid);
        } catch (Exception ex) {
            Dialogs.showErrorMessage("Slide Score TMA Import", "Getting TMA cores positions failed, see log.");
            logger.error(ex.getLocalizedMessage());
        }
    }
}
