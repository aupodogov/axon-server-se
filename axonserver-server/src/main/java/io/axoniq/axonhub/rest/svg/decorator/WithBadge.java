package io.axoniq.axonhub.rest.svg.decorator;

import io.axoniq.axonhub.rest.svg.Element;
import io.axoniq.axonhub.rest.svg.attribute.Dimension;
import io.axoniq.axonhub.rest.svg.attribute.Position;

import java.io.PrintWriter;

/**
 * Created by Sara Pellegrini on 27/04/2018.
 * sara.pellegrini@gmail.com
 */
public class WithBadge implements Element {

    private final Element delegate;

    private final int number;

    private final String text;

    public WithBadge(int number, String text, Element delegate) {
        this.number = number;
        this.text = text;
        this.delegate = delegate;
    }

    @Override
    public void printOn(PrintWriter writer) {
        delegate.printOn(writer);
        int x = delegate.position().x();
        int y = delegate.position().y();
        int width = delegate.dimension().width();
        writer.printf("<svg x=\"%s\" y=\"%s\" height=\"25\" width=\"25\">%n", x + width-18, y-3);
        writer.printf("<title>%s</title>%n", text);
        writer.println("<circle cx=\"50%\" cy=\"50%\" r=\"12\" stroke=\"black\" stroke-width=\"1\" fill=\"red\" />");
        writer.println("<text x=\"50%\" y=\"50%\" text-anchor=\"middle\" fill=\"white\" dy=\".3em\" font-size=\"10\">"+number+"</text>");
        writer.println("</svg>");
    }

    @Override
    public Position position() {
        return delegate.position();
    }

    @Override
    public Dimension dimension() {
        return delegate.dimension();
    }
}
