package qupath.lib.images.servers.slidescore;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;

/**
 * Builder for ImageServers that open slides from a Slide Score server
 * <p>
 * See www.slidescore.com
 * 
 * @author Jan Hudecek
 *
 */
public class SlideScoreImageServerBuilder implements ImageServerBuilder<BufferedImage> {

	private static final Logger logger = LoggerFactory.getLogger(SlideScoreImageServerBuilder.class);
	private static boolean slideScoreUnavailable = false;
	public static String version = "0.5.0.0";
	
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
		return "Image server that open slides from a Slide Score server, v"+version;
	}
	
	@Override
	public Class<BufferedImage> getImageType() {
		return BufferedImage.class;
	}

}