import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Service
public class SpringAnnotations {
    @Value("${app.name:j2k-eval}")
    private String appName;

    @Value("${app.max-users:100}")
    private int maxUsers;

    @Value("${app.description:default description}")
    private String description;

    public String describe() {
        return appName + " allows " + maxUsers + " active users and says " + description;
    }
}

@RestController
@RequestMapping("/api")
class SpringAnnotationsController {
    private final SpringAnnotations service;

    public SpringAnnotationsController(SpringAnnotations service) {
        this.service = service;
    }

    @GetMapping("/status")
    public String status(@RequestParam("user") String user) {
        return service.describe() + " for " + user;
    }
}
