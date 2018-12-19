package io.axoniq.cli;

import io.axoniq.cli.json.Application;
import org.apache.commons.cli.CommandLine;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.IOException;
import java.util.stream.Collectors;

/**
 * Author: marc
 */
public class ListApplications extends AxonIQCliCommand {
    public static void run(String[] args) throws IOException {
        // check args
        CommandLine commandLine = processCommandLine(args[0], args, CommandOptions.TOKEN);
        String url = createUrl(commandLine, "/v1/public/applications");

        // get http client
        try (CloseableHttpClient httpclient = createClient(commandLine)) {
            if( jsonOutput(commandLine) ) {
                System.out.println(getJSON(httpclient, url, String.class, 200, option(commandLine, CommandOptions.TOKEN)));
            } else {
                Application[] applications = getJSON(httpclient,
                                                     url,
                                                     Application[].class,
                                                     200,
                                                     option(commandLine, CommandOptions.TOKEN));
                System.out.printf("%-20s %-60s %-20s\n", "Name", "Description", "Roles");

                for (Application app : applications) {
                    System.out.printf("%-20s %-60s %-20s\n",
                                      app.getName(),
                                      app.getDescription() != null ? app.getDescription() : "",
                                      app.getRoles().stream().map(Object::toString).collect(Collectors.joining(",")));
                }
            }
        }
    }

}
