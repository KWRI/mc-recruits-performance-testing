package com.kwri.auto.mcrecruits.run.simulations.tags;

import com.kwri.auto.mcrecruits.auth.Auth;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.ThrottleStep;

import java.util.ArrayList;
import java.util.List;

import static com.kwri.auto.mcrecruits.data.Endpoints.TAGS;
import static com.kwri.auto.mcrecruits.data.User.TEAMSBRAV0_MC_MCA;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.core.OpenInjectionStep.atOnceUsers;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;
import static org.apache.http.HttpStatus.SC_OK;

/**
 * This is simulation for GET /mc-recruits/api/v3/tags end-point.
 */
public class TagsSimulation extends Auth {

    //  Builder initialization
    ChainBuilder getTags = exec(session -> session.set("token", token))
            .exec(
                            http("GET /mc-recruits/api/v3/tags")
                                    .get(TAGS.getUrl())
                                    .header("x-kwri-apigee-app-name", "devhub")
                                    .header("Authorization", "Bearer " + "#{token}")
                                    .header(KW_HEADER_NAME, TEAMSBRAV0_MC_MCA.getLogin() + ", " + this.getClass().toGenericString())
                                    .check(status().is(SC_OK))
            );

    //    Scenario Initialization
    ScenarioBuilder scnGetAuthorizationToken = scenario("Get Authorization Token").exec(login(TEAMSBRAV0_MC_MCA));
    ScenarioBuilder scnGetMembersRequest = scenario("GET /mc-recruits/api/v3/tags").exec(getTags);

    //    Execution configurations
    {
        //    Control stable RPS to avoid user stacking
        List<ThrottleStep> steps = new ArrayList<>();
        steps.add(reachRps(startingFrom).in(0));
        steps.add(holdFor(eachLevelLasting));

        for (int i = 1; i <= times; i++) {
            steps.add(reachRps(startingFrom + (i * incrementConcurrentUsers))
                    .in(separatedByRampsLasting));
            steps.add(holdFor(eachLevelLasting));
        }

        setUp(
                scnGetAuthorizationToken.injectOpen(atOnceUsers(1))
                        .andThen(
                                scnGetMembersRequest.injectClosed(
                                        incrementConcurrentUsers(incrementConcurrentUsers)
                                                .times(times)
                                                .eachLevelLasting(eachLevelLasting)
                                                .separatedByRampsLasting(separatedByRampsLasting)
                                                .startingFrom(startingFrom)
                                )
                        )
        ).throttle(steps.toArray(new ThrottleStep[0]))
                .protocols(v1HttpProtocol)
                .maxDuration(duration)
                .assertions(
                        global().responseTime().percentile4().lt(maxResponseTime),
                        global().successfulRequests().percent().gt(successfulRequests)
                );
    }
}
