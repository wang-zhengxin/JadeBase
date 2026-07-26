package ai.jadebase.model;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/models")
public class CurrentModelController {

    private final ModelAdminService service;

    public CurrentModelController(ModelAdminService service) {
        this.service = service;
    }

    @GetMapping("/current")
    public ModelAdminService.CurrentModelView current() {
        return service.current();
    }
}
