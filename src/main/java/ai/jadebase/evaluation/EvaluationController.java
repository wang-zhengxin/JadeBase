package ai.jadebase.evaluation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/evaluations")
public class EvaluationController {

    private final EvaluationService service;

    public EvaluationController(EvaluationService service) {
        this.service = service;
    }

    @PostMapping
    public EvaluationService.EvaluationReport evaluate(@Valid @RequestBody EvaluationRequest request) {
        int topK = request.topK() == null ? 6 : request.topK();
        return service.evaluate(request.knowledgeBaseId(), topK, request.cases());
    }

    public record EvaluationRequest(@NotNull UUID knowledgeBaseId, @Min(1) @Max(12) Integer topK,
                                    @NotEmpty List<EvaluationService.EvaluationCase> cases) { }
}
