package qupath.lib.images.servers.slidescore;

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import javafx.application.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathApp;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.servers.*;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectReader;
import qupath.lib.projects.Project;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static qupath.lib.scripting.QP.getProject;

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
		Project<BufferedImage> project = QuPathGUI.getInstance().getProject();
		if (project != null) {
			long createdOn = project.getCreationTimestamp();
			if (new Date(createdOn*1000).toInstant().atZone(ZoneId.systemDefault()).toLocalDate().plusYears(1).compareTo(LocalDate.now()) < 0) {
				tokenExpired(path);
				return;
			}
		}
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
		catch (IOException ex) {
			if (ex.getMessage().indexOf("503") != -1) {
				Dialogs.showMessageDialog("Unable to open the slide", "We can't open this slide on the server. Either it's not there, or the link you've used has expired. Try requesting a new link by opening the study in your browser and clicking the Open in QuPath button.");
				return;
			}
			throw ex;
		}
		finally {
			con.disconnect();
		}


	}

	private void tokenExpired(String path) throws IOException, URISyntaxException {
		Dialogs.showMessageDialog("Slide Score access token expired", "This project file contains slide links that have expired access control tokens. We will open a page where you can upload the project file and get it back with renewed access tokens so that you can keep using the project file. QuPath will close now.");
		String server = path.substring(0, path.indexOf("/i/"));
		Desktop.getDesktop().browse(new URL(server + "/Studies/RenewProject").toURI());
		System.exit(0);
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

	public SlideScoreAnswer[] getAnswers() throws IOException {
		return getAnswers(null, null);
	}
	public SlideScoreAnswer[] getAnswers(String question) throws IOException {
		return getAnswers(null, null);
	}

	public SlideScoreAnswer[] getAnswers(String question, String email) throws IOException {
		var url = new URL(uri.toString().replace("SlideScoreMetadata", "Answers"));
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		try {
			con.setRequestMethod("GET");
			BufferedReader in = new BufferedReader(
					new InputStreamReader(con.getInputStream()));
			try {
				String inputLine;
				ArrayList<SlideScoreAnswer> ret = new ArrayList<>();
				while ((inputLine = in.readLine()) != null) {
					var terms = inputLine.split(";");
					var answer = new SlideScoreAnswer();
					answer.question = terms[0];
					answer.email = terms[1];
					answer.value = terms[2];
					if (question != null && answer.question.compareToIgnoreCase(question) != 0)
						continue;
					if (email != null && answer.email.compareToIgnoreCase(email) != 0)
						continue;
					ret.add(answer);
				}
				return ret.toArray(new SlideScoreAnswer[0]);
			}
			finally {
				in.close();
			}
		}
		finally {
			con.disconnect();
		}
	}


	public String[] getQuestions() throws IOException {
		var url = new URL(uri.toString().replace("SlideScoreMetadata", "Questions"));
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		try {
			con.setRequestMethod("GET");
			BufferedReader in = new BufferedReader(
					new InputStreamReader(con.getInputStream()));
			try {
				String inputLine;
				StringBuffer content = new StringBuffer();
				ArrayList<String> qs = new ArrayList();
				while ((inputLine = in.readLine()) != null) {
					qs.add(inputLine);
				}
				return qs.toArray(new String[qs.size()]);
			}
			finally {
				in.close();
			}
		}
		finally {
			con.disconnect();
		}
	}

	public String[] getAnnotationQuestions() throws IOException {
		var qs = getQuestions();
		var annoQs = new ArrayList<String>();
		for(var i=0;i<qs.length;i++) {
			var terms = qs[i].split(";");
			if (terms[1].equals("AnnoShapes"))
				annoQs.add(terms[0]);
		}
		return annoQs.toArray(new String[0]);
	}


	public String postAnnotation(String question, String answer) throws IOException {
		var url = new URL(uri.toString().replace("SlideScoreMetadata", "AnnoAnswer"));
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		try {
			con.setRequestMethod("POST");
			StringBuilder postData = new StringBuilder();
			postData.append("question=");
			postData.append(URLEncoder.encode(question, "UTF-8"));
			postData.append("&answer=");
			postData.append(URLEncoder.encode(answer, "UTF-8"));
			byte[] postDataBytes = postData.toString().getBytes("UTF-8");

			con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			con.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
			con.setDoOutput(true);
			con.getOutputStream().write(postDataBytes);
			BufferedReader in = new BufferedReader(
					new InputStreamReader(con.getInputStream()));
			try {
				String inputLine;
				StringBuffer content = new StringBuffer();
				ArrayList<String> qs = new ArrayList();
				while ((inputLine = in.readLine()) != null) {
					content.append(inputLine);
					content.append("\n");
				}
				return content.toString();
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