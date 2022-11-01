package co.featbit.server;

import co.featbit.server.exterior.DataStorage;
import co.featbit.server.exterior.DataStorageFactory;
import co.featbit.server.exterior.DataSynchronizer;
import co.featbit.server.exterior.DataSynchronizerFactory;
import co.featbit.server.exterior.InsightProcessor;
import co.featbit.server.exterior.InsightProcessorFactory;

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
