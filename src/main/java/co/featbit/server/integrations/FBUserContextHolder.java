package co.featbit.server.integrations;

import co.featbit.commons.model.FBUser;
import com.alibaba.ttl.TransmittableThreadLocal;

public class FBUserContextHolder {
    private static final ThreadLocal<FBUser> userThreadLocal = new ThreadLocal<>();
    private static final TransmittableThreadLocal<FBUser> inheritedUserThreadLocal = new TransmittableThreadLocal<>();

    public static FBUser getCurrentUser() {
        FBUser user = inheritedUserThreadLocal.get();
        if (user == null) {
            user = userThreadLocal.get();
        }
        return user;
    }

    public static void remove() {
        userThreadLocal.remove();
        inheritedUserThreadLocal.remove();
    }

    public static void setCurrentUser(FBUser user, boolean inherit) {
        if (inherit) {
            inheritedUserThreadLocal.set(user);
        } else {
            userThreadLocal.set(user);
        }
    }
}
