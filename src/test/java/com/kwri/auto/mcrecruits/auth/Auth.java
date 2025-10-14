package com.kwri.auto.mcrecruits.auth;

import com.kwri.auto.core.internal.context.GlobalWorldFacade;
import com.kwri.auto.mcrecruits.data.User;
import com.kwri.auto.mcrecruits.run.SimulationBase;
import com.kwri.auto.core.internal.context.GlobalWorld;
import io.gatling.javaapi.core.ChainBuilder;
import io.restassured.response.Response;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.kwri.auto.core.internal.di.BindingGuiceModule;

import static com.kwri.auto.rest.Constants.ACCESS_TOKEN;
import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * This class is responsible for authorisation request and token storage.
 */
public class Auth extends SimulationBase {

    protected String token = "";

    /**
     * This method performs login request to grab access token and store it in variable.
     *
     * @param user user from {@link User} enum
     */
    public ChainBuilder login(User user) {
        return tryMax(2).on(
                exec(session -> {
                    try {
                        GlobalWorldFacade globalWorldFacade = Guice.createInjector(new BindingGuiceModule())
                                .getInstance(GlobalWorldFacade.class);
                        String password = loadProperties().getProperty(user.getPassword());
                        System.out.println("PASSWORD " + password);
                        String oauth2Uri = loadProperties().getProperty("test.oauth2_uri");
                        System.out.println("oauth2_uri " + oauth2Uri);
                        System.out.println("username " + user.getLogin());

                        String body = "{ \"username\": \"" + user.getLogin() + "\", \"password\": \""
                                + password + "\"}";
                        GlobalWorld globalWorld = Guice.createInjector(new BindingGuiceModule())
                                .getInstance(GlobalWorld.class);
                        Response response = globalWorld.login(oauth2Uri, body);
                        System.out.println("RESPONSE" + response.asPrettyString());
                        token = response.jsonPath().get(ACCESS_TOKEN);
                        return session;
                    } catch (Exception e) {
                        System.out.println("Authentication error: " + e.getMessage());
                        return session.markAsFailed();
                    }
                })
        ).exitHereIfFailed();
    }
}
