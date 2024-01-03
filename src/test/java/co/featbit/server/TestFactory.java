package co.featbit.server;

import co.featbit.server.exterior.*;

class TestFactory {
    static DataSynchronizerFactory mockDataSynchronizerFactory(DataSynchronizer dataSynchronizer) {
        return (context, dataUpdater) -> dataSynchronizer;
    }

    static InsightProcessorFactory mockInsightProcessorFactory(InsightProcessor insightProcessor) {
        return context -> insightProcessor;
    }

    static DataStorageFactory mockDataStorageFactory(DataStorage dataStorage) {
        return context -> dataStorage;
    }
}
