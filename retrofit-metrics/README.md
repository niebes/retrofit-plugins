# retrofit metrics

this plugin creates metrics with tags.

| tag           | purpose                                          |
| ------------- |:-------------------------------------------------|
| base_url      | base_url                                         |
| uri           | uri with placeholders                            |
| method        | http method                                      |
| async         | true for `execute()` false for `enqueue()`       |
| status        | response statusor `Exception`                    |
| series        | response status family or `EXCEPTION             |
| exception     | `simpleName` of the response exception or `None` |

You'll need to capture those metrics with a metrics library of your choice.

refer to implementations instead:
 - [Micrometer](../retrofit-metrics-micrometer)
