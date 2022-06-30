package qupath.lib.images.servers.slidescore;

import qupath.lib.geom.Point2;

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
}
