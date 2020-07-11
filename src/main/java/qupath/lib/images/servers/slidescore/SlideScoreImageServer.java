package qupath.lib.images.servers.slidescore;

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.servers.*;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectReader;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * ImageServer implementation using Slide Score.
 * <p>
 * Note that this does not provide access to the raw data, but rather RGB tiles only in the manner of a web viewer. 
 * Consequently, only RGB images are supported and some small changes in pixel values can be expected due to compression.
 *
 * @author Jan Hudecek
 *
 */
public class SlideScoreImageServer extends AbstractTileableImageServer implements PathObjectReader {

	private static final Logger logger = LoggerFactory.getLogger(SlideScoreImageServer.class);
	//only log it once
	private boolean HasRequestBeenLogged = false;
	private ImageServerMetadata originalMetadata;
	private double[] downsamples;
	private Color backgroundColor;
	private URI uri;
	private String[] args;



	/**
	 * Instantiate a Slide Score server.
	 *
	 * @param path
	 * @param args
	 * @throws IOException
	 */
	public SlideScoreImageServer(String path, String...args) throws IOException, URISyntaxException {
		super();

		URL url = new URL(path);
		uri = new URI(path);
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		try {
			con.setRequestMethod("GET");
			BufferedReader in = new BufferedReader(
					new InputStreamReader(con.getInputStream()));
			try {
				String inputLine;
				StringBuffer content = new StringBuffer();
				while ((inputLine = in.readLine()) != null) {
					content.append(inputLine);
				}
				try {
					var json = JsonParser.parseString(content.toString()).getAsJsonObject();
					/*
						public long Level0TileWidth;
						public long Level0TileHeight;
						public int OSDTileSize;
						public double MppX;
						public double MppY;
						public double ObjectivePower;
						public string BackgroundColor;
						public int LevelCount;
						public long Level0Width;
						public long Level0Height;
						public double[] Downsamples;
					*/
					// Try to get a background color
					try {
						String bg = json.get("BackgroundColor").getAsString();
						if (bg != null) {
							if (!bg.startsWith("#"))
								bg = "#" + bg;
							backgroundColor = Color.decode(bg);
						}
					} catch (Exception e) {
						backgroundColor = null;
						logger.debug("Unable to find background color: {}", e.getLocalizedMessage());
					}

					var level0TileWidth = json.get("Level0TileWidth").getAsInt();
					var levelCount = json.get("LevelCount").getAsInt();
					var resolutionBuilder = new ImageServerMetadata.ImageResolutionLevel.Builder(json.get("Level0Width").getAsInt(), json.get("Level0Height").getAsInt());
					var xJson = json.get("LevelWidths").getAsJsonArray();
					var yJson = json.get("LevelHeights").getAsJsonArray();
					for (var i=0;i<levelCount;i++) {
						var w = xJson.get(i).getAsInt();
						var h = yJson.get(i).getAsInt();
						resolutionBuilder.addLevel(w, h);
					}
					var levels = resolutionBuilder.build();
					originalMetadata = new ImageServerMetadata.Builder(getClass(),
							path, json.get("Level0Width").getAsInt(), json.get("Level0Height").getAsInt()).
							channels(ImageChannel.getDefaultRGBChannels()). // Assume 3 channels (RGB)
							name(json.get("FileName").getAsString()).
							rgb(true).
							pixelType(PixelType.UINT8).
							preferredTileSize(json.get("Level0TileWidth").getAsInt(), json.get("Level0TileHeight").getAsInt()).
							pixelSizeMicrons(json.get("MppX").getAsDouble(), json.get("MppY").getAsDouble()).
							magnification(json.get("ObjectivePower").getAsDouble()).
							levels(levels).
							build();
					logger.info("created metadata with level0tilewidth "+level0TileWidth);
				}
				catch (JsonSyntaxException ex) {
					throw new IOException("Parsing of metadata failed", ex);
				}
			}
			finally {
				in.close();
			}
		}
		finally {
			con.disconnect();
		}


	}

	public SlideScoreTmaPositions getTMAPositions() throws IOException {
		var url = new URL(uri.toString().replace("SlideScoreMetadata", "TMAPositions"));
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		try {
			con.setRequestMethod("GET");
			BufferedReader in = new BufferedReader(
					new InputStreamReader(con.getInputStream()));
			try {
				String inputLine;
				StringBuffer content = new StringBuffer();
				while ((inputLine = in.readLine()) != null) {
					content.append(inputLine);
				}
				try {
					var json = JsonParser.parseString(content.toString()).getAsJsonObject();
					var positions = GsonTools.getInstance().fromJson(json, SlideScoreTmaPositions.class);
					return positions;
				}
				catch (JsonSyntaxException ex) {
					throw new IOException("Parsing of TMA positions failed", ex);
				}
			}
			finally {
				in.close();
			}
		}
		finally {
			con.disconnect();
		}
	}

	@Override
	protected String createID() {
		return getClass().getName() + ": " + uri.toString();
	}

	@Override
	public Collection<URI> getURIs() {
		return Collections.singletonList(uri);
	}

	
	/**
	 * Retrieve any ROIs stored with this image as annotation objects.
	 * <p>
	 * Warning: This method is subject to change in the future.
	 * 
	 * @return
	 * @throws IOException
	 */
	@Override
	public Collection<PathObject> readPathObjects() throws IOException {
		return new ArrayList<>();
	}
	
	
	@Override
	public String getServerType() {
		return "Slide Score server";
	}

	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return originalMetadata;
	}
	
	int getPreferredTileWidth() {
		return getMetadata().getPreferredTileWidth();
	}

	int getPreferredTileHeight() {
		return getMetadata().getPreferredTileHeight();
	}
	


	@Override
	protected BufferedImage readTile(TileRequest tileRequest) throws IOException {
		int tileX = tileRequest.getImageX();
		int tileY = tileRequest.getImageY();
		int tileWidth = tileRequest.getTileWidth();
		int tileHeight = tileRequest.getTileHeight();

		try {
			String path = uri.toString().replace("SlideScoreMetadata.json","");
			//'/<ID>raw/<int:level>/<int:x>_<int:y>/<int:width>_<int:height>.<format>'
			path +=  "raw/"+tileRequest.getLevel()+"/"+tileX+"_"+tileY+"/"+tileWidth+"_"+tileHeight+".jpeg";
			if (!HasRequestBeenLogged) {
				logger.info("Requesting path "+path);
				HasRequestBeenLogged = true;
			}

			BufferedImage img = ImageIO.read(new URL(path));
			BufferedImage img2 = new BufferedImage(tileWidth, tileHeight, BufferedImage.TYPE_INT_RGB);
			Graphics2D g2d = img2.createGraphics();
			if (backgroundColor != null) {
				g2d.setColor(backgroundColor);
				g2d.fillRect(0, 0, tileWidth, tileHeight);
			}
			g2d.drawImage(img, 0, 0, tileWidth, tileHeight, null);
			g2d.dispose();
			return img2;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	
	@Override
	protected ServerBuilder<BufferedImage> createServerBuilder() {
		return ImageServerBuilder.DefaultImageServerBuilder.createInstance(
				SlideScoreImageServerBuilder.class,
				getMetadata(),
				uri,
				args);
	}

}