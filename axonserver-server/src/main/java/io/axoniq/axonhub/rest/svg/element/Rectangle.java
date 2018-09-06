package io.axoniq.axonhub.rest.svg.element;

import io.axoniq.axonhub.rest.svg.Element;
import io.axoniq.axonhub.rest.svg.attribute.Dimension;
import io.axoniq.axonhub.rest.svg.attribute.Position;
import io.axoniq.axonhub.rest.svg.attribute.StyleClass;

import java.io.PrintWriter;

/**
 * Created by Sara Pellegrini on 27/04/2018.
 * sara.pellegrini@gmail.com
 */
public class Rectangle implements Element {

    private final Position coordinates;

    private final Dimension size;

    private final StyleClass styleClass;

    public Rectangle(Position coordinates, Dimension size, StyleClass styleClass) {
        this.coordinates = coordinates;
        this.size = size;
        this.styleClass = styleClass;
    }

    @Override
    public void printOn(PrintWriter writer) {
        writer.printf("<rect ");
        coordinates.printOn(writer);
        size.printOn(writer);
        styleClass.printOn(writer);
        writer.println("rx=\"5\" ry=\"5\"/>");
    }

    public int x() {
        return this.coordinates.x();
    }

    public int y() {
        return this.coordinates.y();
    }

    public int width() {
        return this.size.width();
    }

    public int height() {
        return this.size.height();
    }

    public Rectangle shift(int xOffset, int yOffset){
        return new Rectangle(this.coordinates.shift(xOffset, yOffset), this.size, this.styleClass);
    }

    @Override
    public Position position() {
        return coordinates;
    }

    @Override
    public Dimension dimension() {
        return size;
    }
}
