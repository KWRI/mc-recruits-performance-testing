package com.kwri.auto.aem.run.simulations.members.id;

import com.kwri.auto.aem.auth.Auth;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.FeederBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.ThrottleStep;

import java.util.ArrayList;
import java.util.List;

import static com.kwri.auto.aem.data.Endpoints.MEMBERS;
import static io.gatling.javaapi.core.CoreDsl.csv;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.holdFor;
import static io.gatling.javaapi.core.CoreDsl.incrementConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.reachRps;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;
import static org.apache.http.HttpStatus.SC_OK;

/**
 * This is simulation for GET /members/:id end-point with x-userInfo header as auth method.
 */
public class MembersByIdViaHeaderNoAuthSimulation extends Auth {

    FeederBuilder<String> feeder = csv("feeders/members/activeKwUids.csv").random();

    //  Builder initialization
    ChainBuilder getMembersByKwUid = exec(session -> session)
            .feed(feeder)
            .exec(
                    http("GET /members/:id")
                            .get(MEMBERS.getUrl("#{kwUid}"))
                            .header("x-userinfo", "{\"kwuid\": \"#{kwUid}\","
                                    + " \"name\": \"AvengersAemPerformance\"}")
                            .check(status().is(SC_OK)));

    //    Scenario Initialization

    ScenarioBuilder scnGetMembersByKwUidRequest = scenario("GET /members/:id").exec(getMembersByKwUid);

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
                scnGetMembersByKwUidRequest.injectClosed(
                        incrementConcurrentUsers(incrementConcurrentUsers)
                                .times(times)
                                .eachLevelLasting(eachLevelLasting)
                                .separatedByRampsLasting(separatedByRampsLasting)
                                .startingFrom(startingFrom)
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
