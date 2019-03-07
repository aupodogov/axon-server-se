package io.axoniq.cli;

import io.axoniq.cli.json.RestResponse;
import org.apache.commons.cli.CommandLine;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.IOException;

/**
 * Author: marc
 */
public class InitNode extends AxonIQCliCommand {

    public static void run(String[] args) throws IOException {
        CommandLine commandLine = processCommandLine(args[0], args, CommandOptions.CONTEXT_TO_REGISTER_IN);
        String url = createUrl(commandLine, "/v1/context/init");
        if( commandLine.hasOption(CommandOptions.CONTEXT_TO_REGISTER_IN.getOpt())) {
            url += "?context=" + commandLine.getOptionValue(CommandOptions.CONTEXT_TO_REGISTER_IN.getOpt());
        }

        // get http client
        try (CloseableHttpClient httpclient = createClient(commandLine)) {
            postJSON(httpclient, url, null, 200, null, RestResponse.class);
        }
    }
}
