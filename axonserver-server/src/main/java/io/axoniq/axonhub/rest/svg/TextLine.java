package io.axoniq.axonhub.rest.svg;

/**
 * Created by Sara Pellegrini on 27/04/2018.
 * sara.pellegrini@gmail.com
 */
public class TextLine {

    private final String text;
    private final FontMetricsWrapper font;
    private final String styleClass;

    public TextLine(String text, FontMetricsWrapper font, String styleClass) {
        this.text = text;
        this.font = font;
        this.styleClass = styleClass;
    }

    public int width() {
        return font.stringWidth(text);
    }

    public int height() {return font.getHeight();}

    public int ascent(){ return  font.getAscent();}

    public String styleClass(){return styleClass;}

    public String text() {return text;}


}
