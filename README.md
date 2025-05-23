# FeatBit Server-Side SDK for Java

## Introduction

This is the Java Server-Side SDK for the 100% open-source feature flags management
platform [FeatBit](https://github.com/featbit/featbit).

The FeatBit Server-Side SDK for Java is designed primarily for use in multi-user systems such as web servers and
applications.

## Data synchronization

We use websocket to make the local data synchronized with the FeatBit server, and then store them in memory by
default. Whenever there is any change to a feature flag or its related data, this change will be pushed to the SDK and
the average synchronization time is less than 100 ms. Be aware the websocket connection may be interrupted due to
internet outage, but it will be resumed automatically once the problem is gone.

If you want to use your own data source, see [Offline Mode](#offline-mode).

## Get Started

JAVA Server Side SDK is based on Java SE 8 and is available on Maven Central. You can add it to your project using the following dependency.

### Installation

- Install the SDK using Maven

```xml
<dependencies>
    <!-- https://mvnrepository.com/artifact/co.featbit/featbit-java-sdk -->
    <dependency>
      <groupId>co.featbit</groupId>
      <artifactId>featbit-java-sdk</artifactId>
      <version>1.4.4</version>
    </dependency>
</dependencies>
```

- Install the SDK using Gradle
```
implementation 'co.featbit:featbit-java-sdk:1.4.4'
```

### Prerequisite

Before using the SDK, you need to obtain the environment secret and SDK URLs. 

Follow the documentation below to retrieve these values

- [How to get the environment secret](https://docs.featbit.co/sdk/faq#how-to-get-the-environment-secret)
- [How to get the SDK URLs](https://docs.featbit.co/sdk/faq#how-to-get-the-sdk-urls)
  
### Quick Start

> Note that the _**envSecret**_, _**streamUrl**_ and _**eventUrl**_ are required to initialize the SDK.

The following code demonstrates basic usage of the SDK.

```java
import co.featbit.commons.model.FBUser;
import co.featbit.commons.model.EvalDetail;
import co.featbit.server.FBClientImp;
import co.featbit.server.FBConfig;
import co.featbit.server.exterior.FBClient;

import java.io.IOException;

class Main {
    public static void main(String[] args) throws IOException {
        String envSecret = "<replace-with-your-env-secret>";
        String streamUrl = "ws://localhost:5100";
        String eventUrl = "http://localhost:5100";

        FBConfig config = new FBConfig.Builder()
                .streamingURL(streamUrl)
                .eventURL(eventUrl)
                .build();

        FBClient client = new FBClientImp(envSecret, config);
        if (client.isInitialized()) {
            // The flag key to be evaluated
            String flagKey = "use-new-algorithm";

            // The user
            FBUser user = new FBUser.Builder("bot-id")
                    .userName("bot")
                    .build();

            // Evaluate a boolean flag for a given user
            Boolean flagValue = client.boolVariation(flagKey, user, false);
            System.out.printf("flag %s, returns %b for user %s%n", flagKey, flagValue, user.getUserName());

            // Evaluate a boolean flag for a given user with evaluation detail
            EvalDetail<Boolean> ed = client.boolVariationDetail(flagKey, user, false);
            System.out.printf("flag %s, returns %b for user %s, reason: %s%n", flagKey, ed.getVariation(), user.getUserName(), ed.getReason());
        }

        // Close the client to ensure that all insights are sent out before the app exits
        client.close();
        System.out.println("APP FINISHED");
    }
}
```

### Examples

- [Java Demo](https://github.com/featbit/featbit-samples/blob/main/samples/dino-game/demo-java/src/main/java/co/featbit/demo/JavaDemo.java)

## SDK

### FBClient

Applications **SHOULD instantiate a single FBClient instance** for the lifetime of the application. In the case where an application
needs to evaluate feature flags from different environments, you may create multiple clients, but they should still be
retained for the lifetime of the application rather than created per request or per thread.

#### Boostrapping
The `FBClientImp` constructor will return when it successfully connects, or when the timeout set
by `FBConfig.Builder#startWaitTime(Duration)`
(default: 15 seconds) expires, whichever comes first. If it has not succeeded in connecting when the timeout elapses,
you will receive the client in an uninitialized state where feature flags will return default values; it will still
continue trying to connect in the background unless there has been an `java.net.ProtocolException` or you close the
client(using `close()`). You can detect whether initialization has succeeded by calling `isInitialized()`. 

If `isInitialized()` returns True, it means SDK has succeeded at some point in connecting to feature flag center,
otherwise client has not yet connected to feature flag center, or has permanently failed. In this state, feature flag evaluations will always return default values.

```java
FBConfig config = new FBConfig.Builder()
    .streamingURL(streamUrl)
    .eventURL(eventUrl)
    .startWaitTime(Duration.ofSeconds(10))
    .build();

FBClient client = new FBClientImp(envSecret, config);
if(client.isInitialized()){
    // the client is ready
}
```

If you prefer to have the constructor return immediately, and then wait for initialization to finish at some other
point, you can use `getDataUpdateStatusProvider()`, which provides an asynchronous way, as follows:

```java
FBConfig config = new FBConfig.Builder()
    .streamingURL(streamUrl)
    .eventURL(eventUrl)
    .startWaitTime(Duration.ZERO)
    .build();
FBClient client = new FBClientImp(sdkKey, config);

// later, when you want to wait for initialization to finish:
boolean inited = client.getDataUpdateStatusProvider().waitForOKState(Duration.ofSeconds(10))
if (inited) {
    // the client is ready
}
```

`waitForOKState` method that will block until the client has successfully connected, or until the timeout expires.

> To check if the client is ready is optional. Even if the client is not ready, you can still evaluate feature flags, but the default value will be returned if SDK is not yet initialized.


### FBConfig and Components

`streamingURL`: URL of your feature management platform to synchronise feature flags, user segments, etc.

`eventURL`: URL of your feature management platform to send analytics events

`streamingURL` and `eventURL` are required.

`startWaitTime`: how long the constructor will block awaiting a successful data sync. Setting this to a zero or negative
duration will not block and cause the constructor to return immediately.

`offline`: Set whether SDK is offline. when set to **true** no connection to your feature management platform anymore

`disableEvents`: Set whether disable to send events. when set to **true** no sending any events to your feature management platform

Here is an example of creating a client with default configurations:

```java
FBConfig config = new FBConfig.Builder()
        .streamingURL(streamUrl)
        .eventURL(eventUrl)
        .build();

FBClient client = new FBClientImp(envSecret, config);
```

`FBConfig` provides advanced configuration options for setting the SDK component or you want to customize the behavior
of build-in components.

`HttpConfigFactory`: Interface for a factory that creates an `HttpConfig`. SDK sets the SDK's networking configuration,
using a factory object. This object by defaut is a configuration builder obtained from `Factory#httpConfigFactory()`.
With `HttpConfig`, Sets connection/read/write timeout, proxy or insecure/secure socket.

```java
HttpConfigFactory factory = Factory.httpConfigFactory()
        .connectTime(Duration.ofMillis(3000))
        .httpProxy("my-proxy", 9000)

FBConfig config = new FBConfig.Builder()
        .httpConfigFactory(factory)
        .build();
```


`DataStorageFactory` Interface for a factory that creates some implementation of `DataStorage`, that holds feature flags, 
user segments or any other related data received by the SDK. SDK sets the implementation of the data storage, using `Factory#inMemoryDataStorageFactory()`
to instantiate a memory data storage. Developers can customize the data storage to persist received data in redis, mongodb, etc.

```java
FBConfig config = new FBConfig.Builder()
        .dataStorageFactory(factory)
        .build();
```

`DataSynchronizerFactory` SDK sets the implementation of the `DataSynchronizer` that receives feature flag data from  your feature management platform, 
using a factory object. The default is `Factory#dataSynchronizerFactory()`, which will create a streaming, using websocket.
If Developers would like to know what the implementation is, they can read the javadoc and source code.

`InsightProcessorFactory` SDK sets the implementation of `InsightProcessor` to be used for processing analytics events, using a factory object. 
The default is `Factory#insightProcessorFactory()`. If Developers would like to know what the implementation is, 
they can read the javadoc and source code.

### FBUser

A collection of attributes that can affect flag evaluation, usually corresponding to a user of your application.
This object contains built-in properties(`key`, `userName`). The `key` and `userName` are required.
The `key` must uniquely identify each user; this could be a username or email address for authenticated users, or a ID for anonymous users.
The `userName` is used to search your user quickly. You may also define custom properties with arbitrary names and values.

```java
FBUser user = new FBUser.Builder("key")
        .userName("name")
        .custom("property", "value")
        .build();
```

### Evaluation

SDK calculates the value of a feature flag for a given user, and returns a flag value/an object that describes the way 
that the value was determined.

SDK will initialize all the related data(feature flags, segments etc.) in the bootstrapping and receive the data updates
in real time, as mentioned in [Bootstrapping](#boostrapping).

After initialization, the SDK has all the feature flags in the memory and all evaluation is done _**locally and
synchronously**_, the average evaluation time is < _**10**_ ms.

There is a `variation` method that returns a flag value, and a `variationDetail` method that returns an object
describing how the value was determined for each type.

- variation/variationDetail(for string)
- boolVariation/boolVariationDetail
- doubleVariation/doubleVariationDetail
- longVariation/longVariationDetail
- intVariation/intVariationDetail
- jsonVariation/jsonVariationDetail

The `EvalDetail` and `AllFlagStates` will all details of latest evaluation including the error reason.

`FBClient#getAllLatestFlagsVariations(user)` returns all variations for a given user. You can retrieve the flag value or details
with following methods in `AllFlagStates`:

- getString/getStringDetail
- getBoolean/getBooleanDetail
- getDouble/getDoubleDetail
- getLong/getLongDetail
- getInteger/getIntegerDetail
- getJsonObject/getJsonDetail

Here is an example to retrieve the flag value or details for a string type flag:

```java
// Evaluation details
EvalDetail<String> detail = client.variationDetail("flag key", user, "Not Found");

// Flag value
String value = client.variation("flag key", user, "Not Found");

// get all variations for a given user
AllFlagStates states = client.getAllLatestFlagsVariations(user);
// get all flag keys
Collection<String> flagKeys = states.getFlagKeys();
// flag evaluations 
EvalDetail<String> detail = states.getStringDetail("flag key", user, "Not Found");
String value = states.getString("flag key", user, "Not Found");
```

> If evaluation called before Java SDK client initialized, you set the wrong flag key/user for the evaluation or the related feature flag
is not found SDK will return the default value you set. `EvalDetail` will explain the details of the latest evaluation including error raison.

### Flag Tracking
You can register registers a listener to be notified of feature flag changes in general.

Note that a flag value change listener is bound to a specific user and flag key.

The flag value change listener will be notified whenever the SDK receives any change to any feature flag's configuration, or to a user segment that is referenced by a feature flag. 
To register a flag value change listener, use 'FlagTracker#addFlagValueChangeListener' method.

The flag value change listener just call the `onFlagValueChange` method **_only if_** the flag value changes.

```java
client.getFlagTracker().addFlagValueChangeListener(flagKey, user, event -> {
    // do something
});
```


### Offline Mode
In some situations, you might want to stop making remote calls to FeatBit. Here is how:

```java
FBConfig config = new FBConfig.Builder()
        .streamingURL(streamUrl)
        .eventURL(eventUrl)
        .offline(true)
        .build();

FBClient client = new FBClientImp(envSecret, config);
```
When you put the SDK in offline mode, no insight message is sent to the server and all feature flag evaluations return
fallback values because there are no feature flags or segments available. If you want to use your own data source,
SDK allows users to populate feature flags and segments data from a JSON string. Here is an example: [fbclient_test_data.json](src/test/resources/fbclient_test_data.json).

The format of the data in flags and segments is defined by FeatBit and is subject to change. Rather than trying to
construct these objects yourself, it's simpler to request existing flags directly from the FeatBit server in JSON format
and use this output as the starting point for your file. Here's how:

```shell
# replace http://localhost:5100 with your evaluation server url
curl -H "Authorization: <your-env-secret>" http://localhost:5100/api/public/sdk/server/latest-all > featbit-bootstrap.json
```

Then, you can use this file to initialize the SDK:

```java
FBConfig config = new FBConfig.Builder()
        .streamingURL(streamUrl)
        .eventURL(eventUrl)
        .offline(true)
        .build();

FBClient client = new FBClientImp(envSecret, config);

// init from json string in offline mode
String json = Resources.toString(Resources.getResource("featbit-bootstrap.json"), Charsets.UTF_8);
if(client.initFromJsonFile(json)){
    // the client is ready
}
```

### Experiments (A/B/n Testing)
We support automatic experiments for pageviews and clicks, you just need to set your experiment on our SaaS platform, then you should be able to see the result in near real time after the experiment is started.

In case you need more control over the experiment data sent to our server, we offer a method to send custom event.
```java
client.trackMetric(user, eventName, numericValue);
```
**numericValue** is not mandatory, the default value is **1**.

Make sure `trackMetric` is called after the related feature flag is called, otherwise the custom event won't be included into the experiment result.
