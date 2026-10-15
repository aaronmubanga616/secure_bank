package dev.securebank.fraud;

import dev.securebank.auth.Actor;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/fraud")
public class FraudController {
  private final FraudReviewService reviews;
  private final Actor actor;

  public FraudController(FraudReviewService reviews, Actor actor) {
    this.reviews = reviews;
    this.actor = actor;
  }

  @GetMapping
  public List<Map<String, Object>> list(@RequestParam(required = false) String status) {
    return reviews.list(status);
  }

  @PostMapping("/{id}/review")
  public void review(@PathVariable UUID id, @RequestBody FraudReviewService.Review input) {
    reviews.review(actor.id(), id, input);
  }
}
