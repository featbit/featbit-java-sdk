package co.featbit.server;


import co.featbit.server.exterior.DataStorageFactory;
import co.featbit.server.exterior.DataSynchronizerFactory;
import co.featbit.server.exterior.HttpConfigurationBuilder;
import co.featbit.server.exterior.InsightProcessorFactory;

public abstract class Factory {

    private Factory() {
        super();
    }

    public static HttpConfigurationBuilder httpConfigFactory() {
        return new FactoryImp.HttpConfigurationBuilderImpl();
    }

    public static StreamingBuilder dataSynchronizerFactory() {
        return new FactoryImp.StreamingBuilderImpl();
    }

    public static DataStorageFactory inMemoryDataStorageFactory() {
        return FactoryImp.InMemoryDataStorageFactory.SINGLETON;
    }

    public static DataSynchronizerFactory externalDataSynchronization() {
        return FactoryImp.NullDataSynchronizerFactory.SINGLETON;
    }

    public static InsightProcessorFactory externalEventTrack() {
        return FactoryImp.NullInsightProcessorFactory.SINGLETON;
    }

    public static InsightProcessorBuilder insightProcessorFactory() {
        return new FactoryImp.InsightProcessBuilderImpl();
    }

}
