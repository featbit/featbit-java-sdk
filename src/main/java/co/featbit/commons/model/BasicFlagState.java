package co.featbit.commons.model;

import co.featbit.commons.json.JsonHelper;

/**
 * the abstract class of feature flag state, which contains 2 property:
 * success and message
 * this class and his subclasses are used to communicate between saas/server-side sdk and client side sdk
 */
public abstract class BasicFlagState {
    protected final boolean success;
    protected final String message;

    public BasicFlagState(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    /**
     * if the last evaluation is successful
     *
     * @return true if the evaluation is successful
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * return the message of flag state
     *
     * @return OK if the last evaluation is successful, otherwise return the reason
     */
    public String getMessage() {
        return message;
    }

    /**
     * object converted to json string
     *
     * @return a json string
     */
    public String jsonfy() {
        return JsonHelper.serialize(this);
    }
}
