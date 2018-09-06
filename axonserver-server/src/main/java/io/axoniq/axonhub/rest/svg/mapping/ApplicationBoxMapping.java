package io.axoniq.axonhub.rest.svg.mapping;

import io.axoniq.axonhub.rest.svg.BoxRegistry;
import io.axoniq.axonhub.rest.svg.Element;
import io.axoniq.axonhub.rest.svg.Fonts;
import io.axoniq.axonhub.rest.svg.PositionMapping;
import io.axoniq.axonhub.rest.svg.TextLine;
import io.axoniq.axonhub.rest.svg.attribute.Position;
import io.axoniq.axonhub.rest.svg.decorator.Clickable;
import io.axoniq.axonhub.rest.svg.decorator.DoubleLine;
import io.axoniq.axonhub.rest.svg.element.TextBox;
import io.axoniq.axonhub.rest.svg.jsfunction.ShowDetail;

import java.util.List;

import static java.util.Arrays.asList;

/**
 * Created by Sara Pellegrini on 01/05/2018.
 * sara.pellegrini@gmail.com
 */
public class ApplicationBoxMapping implements PositionMapping<Application> {

    public static final String CLIENT = "client";
    private final BoxRegistry<String> hubNodes;

    private final Fonts fonts;

    public ApplicationBoxMapping(BoxRegistry<String> hubNodes, Fonts fonts) {
        this.hubNodes = hubNodes;
        this.fonts = fonts;
    }

    @Override
    public Element map(Application item, Position position) {
        List<TextLine> lines = asList(new TextLine("Application", fonts.type(), "type"),
                                          new TextLine(item.name(), fonts.component(), "component"),
                                          new TextLine(item.instancesString(), fonts.client(), CLIENT));
        ShowDetail showDetail = new ShowDetail(item.component(), CLIENT, item.context(), item.name());
        TextBox client = new TextBox(lines, position, CLIENT);
        item.connectedHubNodes().forEach(hubNode -> client.connectTo(hubNodes.get(hubNode), "applicationToHub"));
        return new Clickable(new DoubleLine(client, item.instances()>1), showDetail);
    }
}
