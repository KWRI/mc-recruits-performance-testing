## Overview

[![Overall](https://img.shields.io/endpoint?style=flat&url=https%3A%2F%2Fapp.opslevel.com%2Fapi%2Fservice_level%2FwEg8QXkC0oXavVATcVCoJpR4LWDMlgbCCuxt1Nh6S0M)](https://app.opslevel.com/services/aem-performance-testing/maturity-report)

The project contains performance tests for mc-recruits microservice https://github.com/KWRI/mc-recruits-performance-testing

## How to run tests from command line

Open terminal window in the project folder and execute the next command:

```java
 mvn clean
gatling:test -Dgatling.simulationClass=com.kwri.auto.$SIMULATION_CLASS -Denv=$ENVIRONMENT -DincrementConcurrentUsers=$INCREMENT_CONCURRENT_USERS -Dtimes=$TIMES -DeachLevelLasting=$EACH_LEVEL_LASTING -DseparatedByRampsLasting=$SEPARATED_BY_RAMPS_LASTING -DstartingFrom=$STARTING_FROM
```

Parameters used in command:

* -Denv=$ENVIRONMENT - environment where to run simulation
* -DincrementConcurrentUsers=$INCREMENT_CONCURRENT_USERS - increment of users for each step
* -Dtimes=$TIMES - amount of steps
* -DeachLevelLasting=$EACH_LEVEL_LASTING - duration for each step (in minutes)
* -DseparatedByRampsLasting=$SEPARATED_BY_RAMPS_LASTING - duration for ramp up between steps (in seconds)
* -DstartingFrom=$STARTING_FROM - initial amount of users (for first step)

# How to run tests from Harness

## Trigger CI Build with postman

1. Create new post request
2. Request URL = https://app.harness.io/gateway/pipeline/api/webhook/custom/v2?accountIdentifier=gomTD9umQGauvy9KLFG5WQ&orgIdentifier=Command&projectIdentifier=CoreCapabilities&pipelineIdentifier=aemperformancetesting_build&triggerIdentifier=on_demand
3. As authorization use No Auth
4. In order to see who launched the simulation, you need to generate a Harness API key and add the X-Api-Key header to the request
5. Configure your request body with following parameters:

- branch: most likely main unless you want to run your simulation from different branch
- ENV - determines on which environment simulations will be performed
- SIMULATION_CLASS - path to simulation class you want to execute
- INCREMENT_CONCURRENT_USERS - increment of users for each step
- TIMES - amount of steps
- EACH_LEVEL_LASTING - duration for each step (in minutes)
- SEPARATED_BY_RAMPS_LASTING - duration for ramp up between steps (in seconds)
- STARTING_FROM - initial amount of users (for first step)
- mvnTestCompileOnly - `false` to run simulation and `true` to run code check only

See example below, which will run `DemoSim` from `main` branch in `qa` environment. There will be 4 steps (based on
`Times`),
each step will last 1 minute, each step will have amount of users incremented by 5 (based on `IncrementConcurrentUsers`),
steps are separated by 10 seconds ramp up (based on `SeparatedByRampsLasting`), initially it will start from 5 users

```
{
    "branch": "main",
    "ENV": "qa",
    "SIMULATION_CLASS": "aem.run.simulations.DemoSim",
    "INCREMENT_CONCURRENT_USERS": "5",
    "TIMES": "4",
    "EACH_LEVEL_LASTING": "1",
    "SEPARATED_BY_RAMPS_LASTING": "10",
    "STARTING_FROM": "5",
    "mvnTestCompileOnly": "false"
}
```

[Example](http://perf-test-result.cloud.kw.com/qa/demosim-20250108175533055/index.html) of Gatling report with
parameters above.

## Simulation structure

Below you can find breakdown of typical simulation

```java
public class DemoSim extends Auth {

  //  Builder initialization

  ChainBuilder getOrgs = exec(session -> session.set("token", token))
          .exec(
                  http("GET /orgs")
                          .get(ORGS.getUrl())
                          .header("Authorization", "Bearer " + "#{token}")
                          .header(KW_HEADER_NAME, TEAMSBRAV0_MC_MCA.getLogin() + ", " + this.getClass().toGenericString())
                          .check(status().is(SC_OK))
                          .check(jsonPath("$.pagination.total").not("0")));

  //    Scenario Initialization

  ScenarioBuilder scnGetAuthorizationToken = scenario("Get AuthorizationToken").exec(login(TEAMSBRAV0_MC_MCA));
  ScenarioBuilder scnGetOrgsRequest = scenario("GET /orgs").exec(getOrgs);

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
                            scnGetOrgsRequest.injectClosed(
                                    incrementConcurrentUsers(incrementConcurrentUsers)
                                            .times(times)
                                            .eachLevelLasting(eachLevelLasting)
                                            .separatedByRampsLasting(separatedByRampsLasting)
                                            .startingFrom(startingFrom)
                            )
                    )
    ).throttle(steps.toArray(new ThrottleStep[0]))

            .protocols(httpProtocol)
            .maxDuration(duration)
            .assertions(
                    global().responseTime().percentile4().lt(maxResponseTime),
                    global().successfulRequests().percent().gt(successfulRequests)
            );
  }
}
```

`public class DemoSim extends Auth` - each simulation should extend Auth class as it contains essentials for simulation
creation.

Below you can see the initialization of request(s) you will include into your simulation. In this case we will have only
1 request (except login) and it will be GET /orgs request.

```java
ChainBuilder getOrgs = exec(session -> session.set("token", token))
        .exec(
                http("GET /orgs")
                        .get(ORGS.getUrl())
                        .header("Authorization", "Bearer " + "#{token}")
                        .header(KW_HEADER_NAME, TEAMSBRAV0_MC_MCA.getLogin() + ", " + this.getClass().toGenericString())
                        .check(status().is(SC_OK))
                        .check(jsonPath("$.pagination.total").not("0")));
```

* `http()` - description for request
* `.get()` - http method which takes url as parameter
* `.header()` - header if needed
* `.check()` - method to add variety of assertions for each response of request, such as status code validations, data
  validation, etc.

Next step - initialize scenarios to inject.

```java
ScenarioBuilder scnGetAuthorizationToken = scenario("Get AuthorizationToken").exec(login(TEAMSBRAV0_MC_MCA));
ScenarioBuilder scnGetOrgsRequest = scenario("GET /orgs").exec(getOrgs);
```

And finally - injection parameters

```java
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
        scnGetOrgsRequest.injectClosed(
                incrementConcurrentUsers(incrementConcurrentUsers)
                                                .times(times)
                                                .eachLevelLasting(eachLevelLasting)
                                                .separatedByRampsLasting(separatedByRampsLasting)
                                                .startingFrom(startingFrom)
                                )
                                        )
                                        ).throttle(steps.toArray(new ThrottleStep[0]))

        .protocols(httpProtocol)
                .maxDuration(duration)
                .assertions(
        global().responseTime().percentile4().lt(maxResponseTime),
        global().successfulRequests().percent().gt(successfulRequests)
                );
                        }
```

* `scnGetAuthorizationToken.injectOpen(atOnceUsers(1))` - first of all we want to make login request to get token for
  our request. Because of `atOnceUsers(1)` it will make only 1 request by 1 user.
* `.andThen()` - allows you to chain your injections sequentially.
* `scnGetOrgsRequest.injectOpen()` - injection of request under test. As it was mentioned above - we are using open
  model in here.
    * `incrementConcurrentUsers()` - increment of users for each step
    * `.times()` - amount of steps
    * `.eachLevelLasting()` - duration for each step (in minutes)
    * `.separatedByRampsLasting()` - duration for ramp up between steps (in seconds)
    * `.startingFrom()` - initial amount of users (for first step)
* `.protocols(httpProtocol)` - apply default protocol defined in `SimulationBase` to all requests
* `.maxDuration()` - sets upper limit for simulation. It's needed in order to not make simulations last forever :)
* `.assertions()` - block for assertions
    * `global().responseTime().percentile4().lt(maxResponseTime),` - global assert (based on all results) which will
      verify that 4th percentile is not violating specified latency
    * `global().successfulRequests().percent().gt(successfulRequests)` - global assert (based on all results) which will
      verify that there's sufficient amount of successful responses

If you want to run several different requests concurrent you can do it as following
```java
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
                                scnGetOrgByIdRequest.injectClosed(
                                        incrementConcurrentUsers(incrementConcurrentUsers)
                                                .times(times)
                                                .eachLevelLasting(eachLevelLasting)
                                                .separatedByRampsLasting(separatedByRampsLasting)
                                                .startingFrom(startingFrom)
                                ),
                                scnGetOrgsRequest.injectClosed(
                                        incrementConcurrentUsers(incrementConcurrentUsers)
                                                .times(times)
                                                .eachLevelLasting(eachLevelLasting)
                                                .separatedByRampsLasting(separatedByRampsLasting)
                                                .startingFrom(startingFrom)
                                )
                        )
        ).throttle(steps.toArray(new ThrottleStep[0]))

                .protocols(httpProtocol)
                .maxDuration(duration)
                .assertions(
                        global().responseTime().percentile4().lt(maxResponseTime),
                        global().successfulRequests().percent().gt(successfulRequests)
                );
    }
```
### RPS restrictions
Following part of the code is responsible for restriction of RPS. This is needed in order to avoid user stacking issue.
Basically this part creates RPS configurations based on properties in your injection model. 
```java
List<ThrottleStep> steps = new ArrayList<>();
        steps.add(reachRps(startingFrom).in(0));
        steps.add(holdFor(eachLevelLasting));

        for (int i = 1; i <= times; i++) {
        steps.add(reachRps(startingFrom + (i * incrementConcurrentUsers))
        .in(separatedByRampsLasting));
        steps.add(holdFor(eachLevelLasting));
        }
```
```java
 .throttle(steps.toArray(new ThrottleStep[0]))
```

### Feeders

In addition to your simulation - you can make your data dynamic with feeders. You can create different versions of
feeders,
based on model, files, etc. More about it [here](https://docs.gatling.io/reference/script/core/session/feeders/).

Example of usage:

```java
FeederBuilder<String> feeder = csv("feeders/org/activeOrgIds.csv").random();

//  Builder initialization
ChainBuilder getOrgById = exec(session -> session.set("token", token))
        .feed(feeder)
        .exec(
                http("GET /orgs/:id")
                        .get(ORGS.getUrl("#{orgId}"))
                        .header("Authorization", "Bearer " + "#{token}")
                        .header(KW_HEADER_NAME, TEAMSBRAV0_MC_MCA.getLogin() + ", " + this.getClass().toGenericString())
                        .check(status().is(SC_OK)));
```

* `FeederBuilder<String> feeder = csv("feeders/org/activeOrgIds.csv").random();` - initialization of feeder
  based on csv file.
    * Feeders could be based on different file types (csv,tsv,json) or you can initialize them by random generator.
      More about it in documentation linked above.
    * In this case `random` selection was selected. There are more strategies like random, circular, etc.
* `.feed(feeder)` - by this we are adding our feeder to request builder. There could be several feeders.
* `.get(ORGS.getUrl("#{orgId}"))` - right here we are using value from feeder by `"#{orgId}"` where `orgId` - is a
  column key from .csv file. Because we have selected `random` strategy - each request will randomly select element from
  file and add it to each of your request.

## Logging
In order to debug your simulations locally you can turn on console logging in `logback-test.xml` file. Just uncomment
`<logger name="io.gatling.http.engine.response" level="TRACE" />` and select desired level of logging. Make sure you
will not push uncommented value to remote.
