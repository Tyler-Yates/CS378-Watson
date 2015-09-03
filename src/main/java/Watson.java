import com.google.gson.Gson;
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
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Watson {

    static final Gson gson = new Gson();
    static final JsonParser parser = new JsonParser();

    static final Pattern ratePattern = Pattern.compile("rate (\\d*) thumbs(Up|Down)");
    static final Pattern commentPattern = Pattern.compile("comment (\\d*) (.*)");

    public static void main(String[] args) throws Exception {
        final Options options = new Options();
        Option opt = new Option("baseurl", true, "url to WEA server");
        opt.setRequired(true);
        options.addOption(opt);

        opt = new Option("user", true, "User initiating the chat");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("password", true, "Password for the given user");
        opt.setRequired(true);
        options.addOption(opt);

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

        // Setup with your own credentials.  This uses basic auth, more than likely you will need
        // something more advanced
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(cmdLine.getOptionValue("user"),
                        cmdLine.getOptionValue("password")));

        CloseableHttpClient httpClient = HttpClients.custom().setDefaultCredentialsProvider(
                credentialsProvider).build();


        Watson chat = new Watson(httpClient, cmdLine.getOptionValue("baseurl"));
        chat.chat(cmdLine.hasOption("user") ? cmdLine.getOptionValue("user") : "smp");

    }

    CloseableHttpClient httpClient;
    String basePath = "http://localhost:8080/watson/wea/v2";
    String conversationId;

    JsonObject currentResponse;


    Watson(CloseableHttpClient p_httpClient, String p_basePath) {
        httpClient = p_httpClient;
        basePath = p_basePath;
    }

    void chat(String user) throws Exception {

        conversationId = startConversation(user)[0];

        String line;
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("> ");
        while (!(line = reader.readLine()).equalsIgnoreCase("quit")) {
            if (line.startsWith("ask ")) {
                getResponse(line.substring(4).trim());
                log("Watson responded with " + currentResponse.getAsJsonArray(
                        "responses").size() + " items");
                showItem(0);
            } else if (line.startsWith("show ")) {
                showItem(Integer.parseInt(line.substring(5).trim()));
            } else if (line.startsWith("rate ")) {
                Matcher m = ratePattern.matcher(line);
                m.find();
                feedback(Integer.parseInt(m.group(1)), m.group(2).equals("Up") ? 1 : -1, null);
            } else if (line.startsWith("comment ")) {
                Matcher m = commentPattern.matcher(line);
                m.find();
                feedback(Integer.parseInt(m.group(1)), 0, m.group(2));
            }
            System.out.print("> ");
        }
        endConversation();

    }


    String[] startConversation(String user) throws IOException {
        HttpPost req = new HttpPost(basePath + "/conversation");
        req.setHeader("user_token", user); // Add the user_token header
        StringEntity entity = new StringEntity("{}");
        entity.setContentType("application/json");
        req.setEntity(entity);  // Add empty JSON object for body.
        ResponseHandler<String[]> handler = new ResponseHandler<String[]>() {
            @Override
            public String[] handleResponse(HttpResponse resp) throws IOException {
                if (resp.getStatusLine().getStatusCode() == 200) {
                    System.out.println(IOUtils.toString(resp.getEntity().getContent(),
                            StandardCharsets.US_ASCII.toString()));

                    JsonObject json = (JsonObject) parser.parse(
                            new InputStreamReader(resp.getEntity().getContent()));
                    String[] data = new String[2];
                    data[0] = json.getAsJsonPrimitive("conversation_id").getAsString();
                    data[1] = json.getAsJsonArray("greeting").get(0).getAsString();
                    return data;
                } else {
                    throw new IOException("Unable to start conversation : " + EntityUtils.toString(
                            resp.getEntity()));
                }
            }

        };
        return httpClient.execute(req, handler);
    }

    void endConversation() throws IOException {
        HttpPost req = new HttpPost(basePath + "/conversation/" + conversationId + "/end");
        StringEntity entity = new StringEntity("{}");
        entity.setContentType("application/json");
        req.setEntity(entity);  // Add empty JSON object for body.
        ResponseHandler<String> handler = new ResponseHandler<String>() {
            @Override
            public String handleResponse(HttpResponse resp) throws IOException {
                if (resp.getStatusLine().getStatusCode() == 200) {
                    EntityUtils.consume(resp.getEntity());
                    return "";
                } else {
                    throw new IOException("Unable to end conversation : " + EntityUtils.toString(
                            resp.getEntity()));
                }
            }

        };
        httpClient.execute(req, handler);
    }


    void getResponse(String question) throws IOException {
        HttpPost req = new HttpPost(basePath + "/conversation/" + conversationId);
        JsonObject reqData = new JsonObject();
        reqData.addProperty("message", question);
        StringEntity entity = new StringEntity(gson.toJson(reqData));
        entity.setContentType("application/json");
        req.setEntity(entity);

        ResponseHandler<JsonObject> handler = new ResponseHandler<JsonObject>() {
            @Override
            public JsonObject handleResponse(HttpResponse resp) throws IOException {
                if (resp.getStatusLine().getStatusCode() == 200) {
                    return (JsonObject) parser.parse(
                            new InputStreamReader(resp.getEntity().getContent()));
                } else {
                    throw new IOException(
                            "Unable to getResponse : " + EntityUtils.toString(resp.getEntity()));
                }
            }

        };
        currentResponse = httpClient.execute(req, handler);
    }


    void feedback(int itemNum, int rating, String comment) throws Exception {
        JsonObject itemJson = currentResponse.getAsJsonArray("responses").get(
                itemNum).getAsJsonObject();

        HttpPost req = new HttpPost(basePath + "/feedback");
        final JsonObject reqData = new JsonObject();
        reqData.addProperty("conversation_id", conversationId);
        reqData.addProperty("message_id",
                currentResponse.getAsJsonPrimitive("message_id").getAsString());
        reqData.addProperty("response_id",
                itemJson.getAsJsonPrimitive("response_id").getAsString());
        if (rating != 0) {
            reqData.addProperty("rating", rating);
        }
        if (comment != null) {
            reqData.addProperty("comment", comment);
        }
        StringEntity entity = new StringEntity(gson.toJson(reqData));
        entity.setContentType("application/json");
        req.setEntity(entity);

        ResponseHandler<String> handler = new ResponseHandler<String>() {
            @Override
            public String handleResponse(HttpResponse resp) throws IOException {
                if (resp.getStatusLine().getStatusCode() == 200) {
                    EntityUtils.consume(resp.getEntity());
                    return "";
                } else {
                    throw new IOException(
                            "Unable to send feedback : " + reqData + " :: " + EntityUtils.toString(
                                    resp.getEntity()));
                }
            }

        };
        httpClient.execute(req, handler);
    }


    void showItem(int n) {
        JsonObject json = currentResponse.getAsJsonArray("responses").get(n).getAsJsonObject();
        if (json.get("title") != null) {
            log(json.getAsJsonPrimitive("title").getAsString() + "\n" + json.getAsJsonPrimitive(
                    "text").getAsString());
        } else {
            log(json.getAsJsonPrimitive("text").getAsString());
        }
    }

    void log(String msg) {
        System.out.println("\n<< " + msg);
    }
}