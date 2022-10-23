package co.featbit.server.exterior;

/**
 * Interface for a factory that creates an implementation of {@link DefaultSender}.
 */
public interface InsightEventSenderFactory {
    /**
     * create an implementation of {@link DefaultSender}.
     *
     * @param context allows access to the client configuration
     * @return an {@link DefaultSender}
     */
    DefaultSender createInsightEventSender(Context context);
}