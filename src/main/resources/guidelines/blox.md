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
- all variables OUTSIDE of `input` and `output` are internal and can not be seen by other services. The `input` contains the data you get from other services, the `output` contains the data you give back to other services.

You must only use defined structures on the pipeline, do not create anonymous structures within the pipeline.

### <map>

- collection of <link>, <invoke> and <drop>, grouped for clarity

### <link>

A `link` has a `from` attribute, accepting:

1) Fixed Values: Auto-cast to target types. Prefix with = for math (e.g., =a + b). No Java, method calls or ternaries. The left operand dictates the type ("1" + 1 yields "11"; 1 + "1" yields 2). Defaults: double (decimals), long (integers). Use b for exactness (1b = BigInteger, 1.0b = BigDecimal)
You MUST set `fixedValue` to true for this.

2) Queries against the pipeline: XPath-like syntax with Java operators (e.g., customers[name == "test" && vat == "something"]). Condition queries always return lists; index queries (e.g., customers[1]) return single items. Linking a list to a singular target is permitted ONLY if the list contains exactly one item at runtime. This feature can be used as a guard to ensure that at runtime only one item exists so in the right circumstances prefer no index over index [0].

The `to` attribute defines the target and requires explicit indices where applicable (e.g., employees[0]/name
Scalars are automatically converted when possible.

Use `drop` to unset a value. `NEVER` in same map step that sets the value. You do not need to drop a value in order to overwrite it, only to prevent a wrong value from being used in the future.

When linking structures to one another without `mask`, they MUST be compatible structure definitions this means they must either be the same structure definition or share a parent structure definition.
Use `mask` instead of standard <link> when two structures share fields but lack a shared object hierarchy. It recursively auto-casts types and ignores non-overlapping fields.

Conditional links must be in a map step with that condition. If it conflicts with the map step they are in, move them to a new map step.

Scalar types can NOT be linked to structures and structures can NOT be linked to scalars.

Representing whitespace in a fixed value _must_ be done like `=" "`. The raw whitespace will not survive the XML parsing otherwise.

Links that map data FROM invoke TO the pipeline are executed AFTER all invokes are done, this means that data can NOT be used to feed another invoke in the same map step.
Fixed values on the pipeline are executed AFTER all invokes are done, this means that data can NOT be used to feed an invoke in the same map step.

### <invoke>

- Calls artifacts in the artifactGroup `service` (contracts defined in input.xml/output.xml).
- Inputs map via <link> statements inside the <invoke>.
- Outputs are stored in a dynamic pipeline variable named via `resultName`. This variable is never seen by the user and should have a unique name that does not conflict with normal variables names. So dont use basic names like `list` or `sorted`. Use long names that start with `result` and should never collide with actual pipeline variables like `resultFromSortingInvoke`. Two invokes can NOT have the same `resultName` within a single service.
- Dependent invokes within the same map step require a higher `invocationOrder` than their prerequisites (default is 0).
- Independent invokes can have the same `invocationOrder`
- Invokes should have the lowest possible `invocationOrder` based on their dependencies.

Conditional invokes must be in a map step with that condition. If it conflicts with the map step they are in, move them to a new map step.

### <for>

- Iterates over a `query`, a fixed number (e.g., 1000), or a boolean condition (loops until false). If query is set to `true`, it loops indefinitely. Assigns the current iteration to a variable with the configured name of `variable`.
- Variables: Injects `variable` (current item) and `index` dynamically into the pipeline for the loop's scope. These MUST NOT be defined on the pipeline.
- `into` attribute: Aggregates loop iteration outputs directly into a target list. Preferred over `nabu.utils.List.add`.
- `batch` attribute: Fetches records in chunks; variables become a list instead of a single item.

Anti-Pattern: Avoid DB selects inside loops. Pre-select data and use queries for small iterations or `nabu.utils.List.hash` to create a keyed lookup map for large iterations. Unique map keys yield single-item lists, safely linkable to singular targets within the loop.

Example:

```
<for query="myList"
	into="output/parts"
	variable="myVariable">
	<map comment="Map it to the parts">
		<link>
			<from>myVariable/unnamed0</from>
			<to>output/parts[0]</to>
		</link>
	</map>
</for>
```

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
It is important to stress that the break can ALSO break sequences.
So if you have a sequence inside a for loop and want to continue with the next loop iteration, you need break count 2, for example:

```xml
<for query="gauges"
	variable="gauge">
	<sequence label="gauge/lastRun != null &amp;&amp; gauge/pollInterval != null">
		<break 	label="runNext &gt; lastRunBefore">
			<count>2</count>
			<continueExecution>true</continueExecution>
		</break>
	</sequence>
</for>
```

### sequence

`sequence`: Grouping block and mandatory root of any service. Functions inherently as a try/catch block.

Transactions: No autocommit. The root service manages the global transaction (success = commit, exception = rollback). For localized control, apply `scopeDefaultTransaction="true"` to a sequence.

Locking: Set `synchronized="true"` on a sequence for exclusive, cluster-wide execution. The lock releases when the sequence completes.

### Interfaces

A service can implement either a java interface or a defined specification artifact.
To implement a java interface, pointing to the class is not enough, you have to point to the specific method in that class, for example in the `pipeline.xml`:

```xml
<structure interface="be.nabu.libs.services.api.ServiceLevelAgreementListProvider.getAllAgreements" name="pipeline">
	<structure name="input"/>
	<structure name="output"/>
</structure>
```

There, `be.nabu.libs.services.api.ServiceLevelAgreementListProvider` is the actual java interface and `getAllAgreements` is the method within that interface.
For an artifact specification, just set the id of the artifact as interface.
When an interface is configured, the `input.xml` and `output.xml` are automatically updated to reflect that. You do NOT need to add the variables again manually to the input/output.

## Evaluation engine

- operations are left-typed
	- the right operand gets coerced to the left operand’s type
	- therefore operand order matters when the two sides are not already the same type