package com.kwri.auto.mcrecruits.run.simulations.referrals;

import com.kwri.auto.mcrecruits.auth.Auth;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.ThrottleStep;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.kwri.auto.mcrecruits.data.User.TEAMSBRAV0_MC_MCA;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;
import static org.apache.http.HttpStatus.SC_CREATED;

public class PostReferralLeadSimulation extends Auth {

    Iterator<Map<String, Object>> nameFeeder =
            Stream.generate((Supplier<Map<String, Object>>) () -> {
                        HashMap<String, Object> hashMap = new HashMap<>();
                        hashMap.put("name", RandomStringUtils.randomAlphabetic(5));
                        return hashMap;
                    }
            ).iterator();

    Iterator<Map<String, Object>> emailFeeder =
            Stream.generate((Supplier<Map<String, Object>>) () -> {
                        HashMap<String, Object> hashMap = new HashMap<>();
                        hashMap.put("email", RandomStringUtils.randomAlphanumeric(5) + "test@gmail.com");
                        return hashMap;
                    }
            ).iterator();

    // === POST Lead scenario ===
    ChainBuilder postLead = exec(session -> session.set("token", token))
            .feed(nameFeeder).feed(emailFeeder)
            .exec(
                    http("POST mc-recruits/api/v3/orgs/:orgId/leads")
                            .post("/mc-recruits/api/v3/orgs/1005487369/leads")
                            .body(ElFileBody("feeders/referral/referralNewInboundLead.json")).asJson()
                            .header("x-kwri-apigee-app-name", "devhub")
                            .header("Authorization", "Bearer " + "#{token}")
                            .header(KW_HEADER_NAME, TEAMSBRAV0_MC_MCA.getLogin() + ", " + this.getClass().toGenericString())
                            .check(status().is(SC_CREATED))
            );

    // === GET ALL RECRUITS ===
    ChainBuilder getAllRecruits = exec(session ->
            session
                    .set("token", token)
                    .set("allRecruitIds", new ArrayList<String>())
                    .set("pageNumber", 1)
                    .set("totalPages", 1) // initialize to avoid NoSuchElementException
    )
            .asLongAs(session -> session.getInt("pageNumber") <= session.getInt("totalPages"), "pageLoop")
            .on(
                    exec(
                            http("GET recruits page #{pageNumber}")
                                    .get("/mc-recruits/api/v1/recruits/all?page[size]=100&page[number]=#{pageNumber}&search[recruit_name][like]=PerfTest")
                                    .header("Authorization", "Bearer #{token}")
                                    .header("orgID", "1005487369")
                                    .check(status().is(200))
                                    // collect all IDs on this page
                                    .check(jsonPath("$.data[*].id").findAll().optional().saveAs("recruitIds"))
                                    .check(jsonPath("$.currentPage").optional().saveAs("currentPage"))
                                    .check(jsonPath("$.totalPages").optional().saveAs("totalPages"))
                    )
                            .exec(session -> {
                                // Get IDs from page
                                Object idsObj = session.get("recruitIds");
                                List<String> recruitIds = new ArrayList<>();
                                if (idsObj instanceof List<?>) {
                                    for (Object idObj : (List<?>) idsObj) {
                                        recruitIds.add(idObj.toString());
                                    }
                                } else if (idsObj != null) {
                                    recruitIds.add(idsObj.toString());
                                }

                                int currentPage = session.contains("currentPage") ? session.getInt("currentPage") : session.getInt("pageNumber");
                                int totalPages = session.contains("totalPages") ? session.getInt("totalPages") : 1;

                                // Accumulate all recruits
                                if (!recruitIds.isEmpty()) {
                                    System.out.println("🧹 Found recruits on page " + currentPage + ": " + recruitIds);
                                    List<String> allIds = session.getList("allRecruitIds");
                                    allIds.addAll(recruitIds);
                                    session = session.set("allRecruitIds", allIds);
                                } else {
                                    System.out.println("✅ No recruits found on page " + currentPage);
                                }

                                // increment page
                                return session.set("pageNumber", currentPage + 1)
                                        .set("totalPages", totalPages);
                            })
            );

    // === DELETE ALL RECRUITS ===
    ChainBuilder deleteRecruits = doIf(session -> {
        List<String> ids = session.getList("allRecruitIds");
        return ids != null && !ids.isEmpty();
    }).then(
            foreach(session -> session.getList("allRecruitIds"), "id").on(
                    exec(
                            http("DELETE recruit #{id}")
                                    .delete("/mc-recruits/api/v1/automation/recruits/#{id}")
                                    .header("Authorization", "Bearer #{token}")
                                    .check(status().in(200, 204))
                    ).pause(200) // small pause to avoid overwhelming the server
            )
    ).exec(session -> {
        List<String> ids = session.getList("allRecruitIds");
        if (ids == null || ids.isEmpty()) {
            System.out.println("✅ No recruits to delete — cleanup complete.");
        } else {
            System.out.println("🧹 Deleted recruits: " + ids.size());
        }
        return session;
    });


    ScenarioBuilder scnGetAuthorizationToken = scenario("Get Authorization Token").exec(login(TEAMSBRAV0_MC_MCA));
    ScenarioBuilder scnPostLeadsRequest = scenario("POST mc-recruits/api/v3/orgs/:orgId/leads").exec(postLead);
    // === Full cleanup scenario ===
    ScenarioBuilder scnCleanup = scenario("Cleanup PerfTest Recruits").pause(10).exec(getAllRecruits).exec(deleteRecruits);

    // === Execution configurations ===
    {
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
                                scnPostLeadsRequest.injectOpen(
                                        incrementUsersPerSec(startingFrom)
                                                .times(times)
                                                .eachLevelLasting(eachLevelLasting)
                                                .separatedByRampsLasting(separatedByRampsLasting)
                                )
                        )
                        .andThen(
                                scnCleanup.injectOpen(atOnceUsers(1))) // ✅ cleanup now consistent
        )
                .throttle(steps.toArray(new ThrottleStep[0]))
                .protocols(v1HttpProtocol)
                .maxDuration(duration + (30)) // small buffer for cleanup
                .assertions(
                        global().responseTime().percentile4().lt(maxResponseTime),
                        global().successfulRequests().percent().gt(successfulRequests)
                );
    }
}
