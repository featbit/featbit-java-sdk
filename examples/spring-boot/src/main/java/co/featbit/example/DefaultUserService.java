package co.featbit.example;

import co.featbit.server.integrations.FeatGate;

public class DefaultUserService implements UserService {
    @FeatGate(flag = "getFirstName", variants = @FeatGate.Variant(value = "v2", method = "formattedFirstName"))
    public String getFirstName() {
        return "albert";
    }

    private String formattedFirstName() {
        return "Albert";
    }

    @FeatGate(flag = "getLastName", variants = @FeatGate.Variant(value = "v2", method = "formattedLastName"))
    public String getLastName() {
        return "einstein";
    }

    private String formattedLastName() {
        return "E.";
    }
}
