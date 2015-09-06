import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;

import javax.ws.rs.core.MediaType;
import java.io.IOException;

public class Watson {
    private static final String URL = "https://watson-wdc01.ihost.com/instance/540/deepqa/v1/question";
    private static final String USERNAME_OPTION_NAME = "username";
    private static final String PASSWORD_OPTION_NAME = "password";
    private static final String QUESTION_OPTION_NAME = "question";

    /**
     * Asks Watson a question with the given credentials and prints the results out to the terminal.
     * <p/>
     * Usage:<br/>
     * {@code Watson -username "[username]" -password "[password]" -question "[question text]"}
     *
     * @param args the given username, password, and question
     *
     * @throws IOException
     */
    public static void main(String args[]) throws IOException {
        final Options options = new Options();
        options.addOption(createRequiredOption(USERNAME_OPTION_NAME, "Username"));
        options.addOption(createRequiredOption(PASSWORD_OPTION_NAME, "Password"));
        options.addOption(createRequiredOption(QUESTION_OPTION_NAME, "Question to ask Watson"));

        final CommandLineParser clp = new DefaultParser();
        CommandLine cmdLine;
        try {
            cmdLine = clp.parse(options, args);
        } catch (ParseException e) {
            // this is not fatal until all command line options are being parsed
            System.out.println(e.getMessage() + "\n");
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("java " + Watson.class.getName(), options);
            return;
        }

        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        final Credentials credentials = new UsernamePasswordCredentials(
                cmdLine.getOptionValue(USERNAME_OPTION_NAME),
                cmdLine.getOptionValue(PASSWORD_OPTION_NAME));
        credentialsProvider.setCredentials(AuthScope.ANY, credentials);

        prettyPrint(askQuestion(credentialsProvider, cmdLine.getOptionValue(QUESTION_OPTION_NAME)));
    }

    /**
     * Asks Watson a question and returns Watson's response using the given credentials to
     * authenticate with the URL.
     *
     * @param credentialsProvider the authentication information
     * @param question the question to ask Watson
     *
     * @return the response from Watson
     *
     * @throws IOException
     */
    public static String askQuestion(CredentialsProvider credentialsProvider,
            String question) throws IOException {
        final HttpClient httpClient = HttpClients.custom().setDefaultCredentialsProvider(
                credentialsProvider).build();
        final HttpPost httpPost = new HttpPost(URL);
        httpPost.setHeader(new BasicHeader("X-SyncTimeout", "30"));
        httpPost.setHeader(new BasicHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON));
        httpPost.setHeader(new BasicHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON));
        final JsonObject questionText = new JsonObject();
        questionText.addProperty("questionText", question);
        final JsonObject content = new JsonObject();
        content.add("question", questionText);
        httpPost.setEntity(new StringEntity(content.toString()));

        final HttpResponse httpResponse = httpClient.execute(httpPost);
        return IOUtils.toString(httpResponse.getEntity().getContent());
    }

    private static Option createRequiredOption(String optionName, String description) {
        final Option option = new Option(optionName, true, description);
        option.setRequired(true);
        return option;
    }

    private static void prettyPrint(String jsonString) {
        final Gson gson = new GsonBuilder().setPrettyPrinting().create();
        final JsonElement jsonElement = new JsonParser().parse(jsonString);
        System.out.println(gson.toJson(jsonElement));
    }
}