# Artifact: blox

Fragments:
- `metadata.xml`: repository metadata around the VM service
- `input.xml`: editable alias for the pipeline input branch
- `output.xml`: editable alias for the pipeline output branch
- `pipeline.xml`: canonical editable pipeline definition
- `service.xml`: VM step sequence

TypeScript shape for `service.xml`:
```typescript
type B=boolean|"true"|"false"

type S={comment?:string,name?:string,label?:string,disabled?:B,features?:string,description?:string}

type Link={t:"link",from?:string,to?:string,mask?:B,optional?:B,patch?:B,fixedValue?:B,sourceNotNull?:B}
type Drop={t:"drop",path:string}
type Invoke={t:"invoke",serviceId:string,resultName?:string,temporaryMapping?:B,invocationOrder?:number|string,target?:string,property?:{key:string,value:string}[],recache?:B,children?:Link[]}
type Throw=S&{t:"throw",code?:string,message?:string,data?:string,alias?:string,realm?:string,authenticationId?:string,whitelist?:B}
type Break=S&{t:"break",count?:number|string,continueExecution?:B}

type C=Sequence|Map|Switch|For|Throw|Break

type Sequence=S&{t:"sequence",transactionVariable?:string,suppressException?:B,scopeDefaultTransaction?:B,synchronized?:B,children?:Array<Sequence|Map|Switch|For|Catch|Finally|Throw|Break>}
type Map=S&{t:"map",children?:Array<Invoke|Link|Drop>}
type Switch=S&{t:"switch",query?:string,children?:C[]}
type For=S&{t:"for",variable?:string,index?:string,query?:string,batchSize?:string,into?:string,children?:C[]}
type Catch=S&{t:"catch",variable?:string,suppressException?:B,types?:string[],codes?:string[],stacktraceRegex?:string,children?:C[]}
type Finally=S&{t:"finally",children?:C[]}

type Service=Sequence
```

Example:
```xml
<sequence>
	<map label="input/customerId != null">
		<invoke serviceId="example.customer.lookup" resultName="customer">
			<link from="input/customerId" to="id"/>
		</invoke>
	</map>
</sequence>
```

### pipeline.xml

- uses structure semantics (check skill for `artifact:structure`)
- defines static variables including input and output
- dynamic variables (e.g. for `variable`) are injected at runtime and don't exist in pipeline.xml
- variables must have unique names and can not be reassigned
- enable input or output validation by setting `validate="true"`, for example `<structure name="input" validate="true">`

### <map>

- collection of <link>, <invoke> and <drop>, grouped for clarity

### <link>

A `link` has a `from` attribute, accepting:

1) Fixed Values: Auto-cast to target types. Prefix with = for math (e.g., =a + b). No Java, method calls or ternaries. The left operand dictates the type ("1" + 1 yields "11"; 1 + "1" yields 2). Defaults: double (decimals), long (integers). Use b for exactness (1b = BigInteger, 1.0b = BigDecimal)
You MUST set `fixedValue` to true for this.

2) Queries against the pipeline: XPath-like syntax with Java operators (e.g., customers[name == "test" && vat == "something"]). Condition queries always return lists; index queries (e.g., customers[1]) return single items. Linking a list to a singular target is permitted ONLY if the list contains exactly one item at runtime. This feature can be used as a guard.

The `to` attribute defines the target and requires explicit indices where applicable (e.g., employees[0]/name
Scalars are automatically converted when possible.

Use `drop` to unset a value. Never in same map step that sets the value.

Use `mask` instead of standard <link> when two structures share fields but lack a shared object hierarchy. It recursively auto-casts types and ignores non-overlapping fields.

Conditional links must be in a map step with that condition. If it conflicts with the map step they are in, move them to a new map step.

### <invoke>

- Calls artifacts in the artifactGroup `service` (contracts defined in input.xml/output.xml).
- Inputs map via <link> statements inside the <invoke>.
- Outputs are stored in a dynamic pipeline variable named via `resultName`.
- Dependent invokes within the same map step require a higher `invocationOrder` than their prerequisites (default is 0).

Conditional invokes must be in a map step with that condition. If it conflicts with the map step they are in, move them to a new map step.

### <for>

- Iterates over a `query`, a fixed number (e.g., 1000), or a boolean condition (loops until false).
- Variables: Injects `variable` (current item) and `index` dynamically into the pipeline for the loop's scope.
- `into` attribute: Aggregates loop iteration outputs directly into a target list. Preferred over `nabu.utils.List.add`.
- `batch` attribute: Fetches records in chunks; variables become a list instead of a single item.

Anti-Pattern: Avoid DB selects inside loops. Pre-select data and use queries for small iterations or `nabu.utils.List.hash` to create a keyed lookup map for large iterations. Unique map keys yield single-item lists, safely linkable to singular targets within the loop.

### label

- `label`: A boolean execution condition for a step. Null or empty lists evaluate to false (e.g., !myRecords). Sequential steps with labels evaluate independently.
- `switch`: Wraps steps to execute only the first matching label.
	- With `query`: Evaluates query == label.
	- Without `query`: Evaluates the full condition in each label (acts as if/else).
	- An empty label acts as the default fallback.

### comment/description

- `comments`: Developer-facing explanations for step logic.
- `description`: Runtime-resolved logs. Prefix with = to evaluate variables.

## Exceptions

- `throw`:
	- `message`: Static error string (no variables).
	- `description`: Detailed context (variables allowed).
	- `data`: Arbitrary context (use = for variables).
	- `code`: Required structural identifier (e.g., CONTRACT-EXPIRED). Numeric codes (e.g., 404) map to HTTP status codes on API calls.
	- Errors and data remain hidden from APIs unless whitelist is active.
- `catch`/`finally`: Rarely used. Resource management (streams, locks) is automatic. Let errors bubble up.

### break

Exits a `for` or `sequence`. `count` dictates break depth (default 1). Use `continueExecution="true"` to skip to the next for iteration.

### sequence

`sequence`: Grouping block and mandatory root of any service. Functions inherently as a try/catch block.

Transactions: No autocommit. The root service manages the global transaction (success = commit, exception = rollback). For localized control, apply `scopeDefaultTransaction="true"` to a sequence.

Locking: Set `synchronized="true"` on a sequence for exclusive, cluster-wide execution. The lock releases when the sequence completes.