package co.featbit.server.exterior;

/**
 * Interface for a factory that creates an implementation of {@link InsightProcessor}.
 */

public interface InsightProcessorFactory {

    /**
     * creates an implementation of {@link InsightProcessor}
     *
     * @param context allows access to the client configuration
     * @return an {@link InsightProcessor}
     */
    InsightProcessor createInsightProcessor(Context context);

}
