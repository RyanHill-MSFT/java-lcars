package com.lcars.webpubsub;

import com.azure.messaging.webpubsub.WebPubSubServiceClient;
import com.azure.messaging.webpubsub.WebPubSubServiceClientBuilder;
import com.azure.messaging.webpubsub.models.GetClientAccessTokenOptions;
import com.azure.messaging.webpubsub.models.WebPubSubClientAccessToken;
import com.azure.messaging.webpubsub.models.WebPubSubContentType;

import io.github.cdimascio.dotenv.Dotenv;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

public class App {
    private static void ConfigureRoutes(Javalin app, WebPubSubServiceClient service) {
        app.get("/negotiate", ctx -> {
            String userId = ctx.queryParam("id");
            if (userId == null) {
                ctx.status(400);
                ctx.result("Missing user id");
                return;
            }

            GetClientAccessTokenOptions option = new GetClientAccessTokenOptions();
            option.setUserId(userId);
            WebPubSubClientAccessToken token = service.getClientAccessToken(option);

            ctx.result(token.getUrl());
            return;
        });
    }

    private static void SetEventHandler(Javalin app, WebPubSubServiceClient service) {
        app.post("/eventhander", ctx ->{
           String event = ctx.header("ce-type");
           if("azure.webpubsub.sys.connected".equals(event)) {
               String id = ctx.header("ce-userId");
               service.sendToAll(String.format("[SYSTEM] %s joined",id), WebPubSubContentType.TEXT_PLAIN);
           } else if("azure.webpubsub.user.message".equals(event)) {
               String id = ctx.header("ce-userId");
               String message = ctx.body();
               service.sendToAll(String.format("[%s] %s", id, message), WebPubSubContentType.TEXT_PLAIN);
           }
           ctx.status(200);
        });
    }

    public static void main(String[] args) {
        Dotenv dotenv = null;
        dotenv = Dotenv.configure().load();

        // create the service client
        String connectionString = dotenv.get("WEBPUBSUB_ENDPOINT");
        String hub = dotenv.get("WEBPUBSUB_HUB");
        WebPubSubServiceClient service = new WebPubSubServiceClientBuilder().connectionString(connectionString).hub(hub)
                .buildClient();

        // start the server
        Javalin app = Javalin.create(config -> {
            config.addStaticFiles("/public", Location.CLASSPATH);
        }).start(8080);

        ConfigureRoutes(app, service);

        SetEventHandler(app, service);
    }
}
