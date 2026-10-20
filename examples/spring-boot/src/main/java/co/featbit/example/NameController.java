package co.featbit.example;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class NameController {
    private final UserService userService;

    public NameController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/name")
    public Map<String, String> getName() {
        return Map.of("firstName", userService.getFirstName(), "lastName", userService.getLastName());
    }
}
