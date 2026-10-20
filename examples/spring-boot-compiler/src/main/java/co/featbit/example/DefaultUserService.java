package co.featbit.example;

import co.featbit.server.integrations.FeatGate;

public class DefaultUserService implements UserService {
    @FeatGate(flag = "getFirstName", value = "v1")
    @FeatGate(flag = "getFirstName", value = "default")
    private String getFirstName() {
        return "albert";
    }

    @FeatGate(flag = "getFirstName", value = "v2")
    private String getFirstName() {
        return "Albert";
    }

    @FeatGate(flag = "getLastName", value = "v1")
    @FeatGate(flag = "getLastName", value = "default")
    private String getLastName() {
        return "einstein";
    }

    @FeatGate(flag = "getLastName", value = "v2")
    private String getLastName() {
        return "E.";
    }

    public String fullName() {
        return getFirstName() + " " + getLastName();
    }
}
