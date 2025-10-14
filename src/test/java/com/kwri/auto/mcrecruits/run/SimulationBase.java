package com.kwri.auto.mcrecruits.run;

import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static io.gatling.javaapi.http.HttpDsl.http;

/**
 * This class contains base for simulations and requests.
 */
public class SimulationBase extends Simulation {

    final protected String KW_HEADER_NAME = "x-kw-test-data";
    protected static String env = System.getProperty("env", "qa");
    protected Properties properties = loadProperties();

    //    default assertions values
    protected int maxResponseTime = Integer.parseInt(properties.getProperty("performance.maxResponseTime"));
    protected double successfulRequests = Double.parseDouble(properties.getProperty("performance.successfulRequests"));

    //    Configurable parameters
    protected int incrementConcurrentUsers = Integer.getInteger("incrementConcurrentUsers", 1);
    protected int times = Integer.getInteger("times", 1);
    protected long eachLevelLasting = Integer.getInteger("eachLevelLasting", 1) * 60;
    protected int separatedByRampsLasting = Integer.getInteger("separatedByRampsLasting", 1);
    protected int startingFrom = Integer.getInteger("startingFrom", 1);
    protected long duration = (times != 0 ? eachLevelLasting * times : eachLevelLasting)
            + (long) separatedByRampsLasting * (times != 0 ? times - 1 : 1);

    /**
     * This method loads properties based on environment.
     */
    public Properties loadProperties() {
        Properties properties = new Properties();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        InputStream stream = loader.getResourceAsStream(env.toLowerCase() + "_assertions.properties");
        InputStream passwordsStream = loader.getResourceAsStream("passwords.properties");
        InputStream testPropertiesStream = loader.getResourceAsStream(env.toLowerCase() + ".test.properties");
        try {
            properties.load(stream);
            properties.load(passwordsStream);
            properties.load(testPropertiesStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return properties;
    }

    /**
     * This method serve base url based on environment simulation executed at.
     */
    protected static String getUrl() {
        switch (env) {
            case "qa":
                return "https://qa-kong.command-api.kw.com/";
            case "dev":
                return "https://dev-kong.command-api.kw.com/";
            case "prod":
                return "https://kong.command-api.kw.com/";
            default:
                throw new IllegalArgumentException("Unsupported env value: " + env);
        }
    }

    /**
     * This method serve default http protocol for all requests in simulation.
     */
    protected HttpProtocolBuilder v1HttpProtocol =
            http.baseUrl(getUrl())
                    .acceptHeader("application/json")
                    .userAgentHeader("Teams Bravo mc-recruits Performance")
                    .disableCaching();

}
