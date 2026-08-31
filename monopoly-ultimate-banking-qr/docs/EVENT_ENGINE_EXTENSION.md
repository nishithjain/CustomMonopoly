# Edition Rule Extension Process

When adding a genuinely new Event mechanic:

1. Add the new typed action definition to `EventActionType`.
2. Add JSON parsing and payload validation in `EventActionValidator`.
3. Implement the Kotlin handler in `EventEngine`.
4. Register dispatch in `EventEngine.apply()` using the enum (not string keys).
5. Add engine and edition-validation tests.
6. Increment the edition `definitionVersion` when the mechanic ships in an existing edition.

JSON must never contain executable scripts, Kotlin class names, reflection targets, or arbitrary expressions.

New Events that only combine existing supported `actionType` values require edition JSON and validation changes only.
