## Description
<!-- Describe your changes in detail here. -->

## Section 17: Definition of Done
Please verify that you have completed all of the following before requesting a review:

- [ ] **Behavior matches the documentation** (or the documentation was updated first).
- [ ] **Input validated**; authorization enforced; errors use the standard `ApiError` format.
- [ ] **Unit tests** and at least one integration test added and passing.
- [ ] **Flyway migration** added if the schema changed (never edit an applied migration).
- [ ] **No new warnings**; Spotless formatter passes; no secrets or debug code committed.
- [ ] **Swagger annotations** complete per section 19.8 (summary, operationId, tag, examples, errors, security).
- [ ] **Logs** are meaningful and contain no sensitive data/PII.
- [ ] **`docs/PROGRESS.md`** and the main docs updated if scope or design changed.
