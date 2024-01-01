package co.featbit.server;

import co.featbit.server.exterior.FBClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class Loggers {
    static final String BASE_LOGGER_NAME = FBClient.class.getName();
    static final Logger CLIENT = LoggerFactory.getLogger(BASE_LOGGER_NAME);
    private static final String DATA_SYNCHRONIZER_LOGGER_NAME = BASE_LOGGER_NAME + ".DataSynchronizer";
    static final Logger UPDATE_PROCESSOR = LoggerFactory.getLogger(DATA_SYNCHRONIZER_LOGGER_NAME);
    private static final String DATA_STORAGE_LOGGER_NAME = BASE_LOGGER_NAME + ".DataStorage";
    static final Logger DATA_STORAGE = LoggerFactory.getLogger(DATA_STORAGE_LOGGER_NAME);
    private static final String EVALUATION_LOGGER_NAME = BASE_LOGGER_NAME + ".Evaluation";
    static final Logger EVALUATION = LoggerFactory.getLogger(EVALUATION_LOGGER_NAME);
    private static final String EVENTS_LOGGER_NAME = BASE_LOGGER_NAME + ".Events";
    static final Logger EVENTS = LoggerFactory.getLogger(EVENTS_LOGGER_NAME);
    private static final String UTILS_LOGGER_NAME = BASE_LOGGER_NAME + ".Utils";
    static final Logger UTILS = LoggerFactory.getLogger(UTILS_LOGGER_NAME);
    private static final String TEST_LOGGER_NAME = BASE_LOGGER_NAME + ".Test";
    static final Logger TEST = LoggerFactory.getLogger(TEST_LOGGER_NAME);

    Loggers() {
        super();
    }

}
