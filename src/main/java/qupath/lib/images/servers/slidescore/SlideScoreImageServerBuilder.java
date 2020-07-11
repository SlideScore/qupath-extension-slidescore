package qupath.lib.images.servers.slidescore;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Scanner;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.slidescore.SlideScoreImageServer;

/**
 * Builder for ImageServers that open slides from a Slide Score server
 * <p>
 * See www.slidescore.com
 * 
 * @author Jan Hudecek
 *
 */
public class SlideScoreImageServerBuilder implements ImageServerBuilder<BufferedImage> {

	final private static Logger logger = LoggerFactory.getLogger(SlideScoreImageServerBuilder.class);
	private static boolean slideScoreUnavailable = false; 
	
	@Override
	public ImageServer<BufferedImage> buildServer(URI uri, String...args) {
		if (slideScoreUnavailable)
			return null;
		try {
			return new SlideScoreImageServer(uri.toString());
		} catch (UnsatisfiedLinkError e) {
			logger.error("Could not access Slide Score, restart QuPath before trying again", e);
			// Log that we couldn't create the link
			slideScoreUnavailable = true;
		} catch (Exception e) {
			logger.error("Unable to open '"+uri+"' with SlideScore: {}",  e);
		}
		return null;
	}


	@Override
	public UriImageSupport<BufferedImage> checkImageSupport(URI uri, String...args) {
		float supportLevel = supportLevel(uri, args);
		Collection<ServerBuilder<BufferedImage>> builders = new ArrayList<>();
		
		if (supportLevel > 0f) {
			if (slideScoreUnavailable)
				return null;
			try {
				var server = new SlideScoreImageServer(uri.toString());
				builders.add(server.getBuilder());
				return UriImageSupport.createInstance(this.getClass(), supportLevel, builders);
			} catch (UnsatisfiedLinkError e) {
				logger.error("Could not access Slide Score, restart QuPath before trying again", e);
				// Log that we couldn't create the link
				slideScoreUnavailable = true;
			} catch (Exception e) {
				logger.error("Unable to open '"+uri+"' with SlideScore: {}",  e);
			}
			return null;
		}
		return null;
	}
	
	
	private static float supportLevel(URI uri, String...args) {
		String host = uri.getHost();
		String scheme = uri.getScheme();
		if (scheme.startsWith("http") && uri.toString().endsWith("SlideScoreMetadata.json")) {
			return 1;
		}
		return 0;
	}

	@Override
	public String getName() {
		return "Slide Score";
	}

	@Override
	public String getDescription() {
		return "Image server that open slides from a Slide Score server";
	}
	
	@Override
	public Class<BufferedImage> getImageType() {
		return BufferedImage.class;
	}

}