package be.nabu.eai.module.services.vm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


import be.nabu.eai.module.types.structure.StructureManager;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.impl.DefinedServiceArtifactFragmentManager;
import be.nabu.libs.resources.ResourceReadableContainer;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.services.vm.Pipeline;
import be.nabu.libs.services.vm.SimpleVMServiceDefinition;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.services.vm.api.StepGroup;
import be.nabu.libs.services.vm.step.Invoke;
import be.nabu.libs.services.vm.step.Link;
import be.nabu.libs.services.vm.step.Sequence;
import be.nabu.libs.validator.api.Validation;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.utils.io.IOUtils;

public class VMServiceArtifactFragmentManager extends DefinedServiceArtifactFragmentManager<SimpleVMServiceDefinition> {

	private static final String PIPELINE_PATH = "pipeline.xml";
	private static final String SERVICE_PATH = "service.xml";
	private static final String CONTENT_TYPE = "application/xml";
	private static final String ARTIFACT_TYPE = "blox";

	@Override
	public List<ArtifactFragment> listFragments(SimpleVMServiceDefinition artifact) {
		List<ArtifactFragment> fragments = new ArrayList<ArtifactFragment>();
		for (ArtifactFragment fragment : super.listFragments(artifact)) {
			if (fragment != null && ("input.xml".equals(fragment.getPath()) || "output.xml".equals(fragment.getPath()))) {
				fragments.add(new EditableAliasFragment(fragment));
			}
			else {
				fragments.add(fragment);
			}
		}
		fragments.addAll(Arrays.<ArtifactFragment>asList(
			new RepositoryEntryFragment(artifact, PIPELINE_PATH),
			new SanitizedServiceFragment(artifact)
		));
		return fragments;
	}

	@Override
	public List<Validation<?>> updateFragment(SimpleVMServiceDefinition artifact, String path, String oldContent, String newContent) {
		List<Validation<?>> validations = new ArrayList<Validation<?>>();
		ResourceEntry entry = getResourceEntry(artifact);
		try {
			SimpleVMServiceDefinition service = (SimpleVMServiceDefinition) new VMServiceManager().load(entry, validations);
			if (PIPELINE_PATH.equals(path)) {
				Pipeline updated = StructureManager.parseUpdatedStructure(entry, newContent, service.getPipeline(), new Pipeline(null, null), validations);
				SimpleVMServiceDefinition updatedService = withPipeline(service, updated);
				if (!hasErrors(validations)) {
					validations.addAll(new VMServiceManager().save(entry, updatedService));
				}
				return validations;
			}
			if ("input.xml".equals(path) || "output.xml".equals(path)) {
				Pipeline updated = StructureManager.parseUpdatedStructure(entry, readRepositoryResource(service, PIPELINE_PATH), service.getPipeline(), new Pipeline(null, null), validations);
				if (!hasErrors(validations)) {
					StructureManager.parse(entry, newContent.getBytes("UTF-8"), validations, (be.nabu.libs.types.structure.Structure) updated.get("input.xml".equals(path) ? Pipeline.INPUT : Pipeline.OUTPUT).getType());
					StructureManager.inheritRootProperties((be.nabu.libs.types.structure.Structure) service.getPipeline().get("input.xml".equals(path) ? Pipeline.INPUT : Pipeline.OUTPUT).getType(), (be.nabu.libs.types.structure.Structure) updated.get("input.xml".equals(path) ? Pipeline.INPUT : Pipeline.OUTPUT).getType());
					if (!hasErrors(validations)) {
						SimpleVMServiceDefinition updatedService = withPipeline(service, updated);
						if (!hasErrors(validations)) {
							validations.addAll(new VMServiceManager().save(entry, updatedService));
						}
					}
				}
				return validations;
			}
			if (SERVICE_PATH.equals(path)) {
				Sequence updated = VMServiceManager.parseSequence(IOUtils.wrap(newContent.getBytes("UTF-8"), true));
				SimpleVMServiceDefinition candidate = withPipeline(service, service.getPipeline());
				candidate.setRoot(updated);
				mergeStepMetadata(service.getRoot(), updated);
				validateSequence(candidate, updated, validations);
				if (!hasErrors(validations)) {
					updated.setDefinition(service);
					service.setRoot(updated);
					validations.addAll(new VMServiceManager().save(entry, service));
				}
				return validations;
			}
			throw new UnsupportedOperationException("Updating fragments is only supported for pipeline.xml and service.xml on VM services");
		}
		catch (Exception e) {
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, e.getMessage() == null ? e.getClass().getName() : e.getMessage()));
			return validations;
		}
	}

	private SimpleVMServiceDefinition withPipeline(SimpleVMServiceDefinition service, Pipeline pipeline) {
		SimpleVMServiceDefinition updated = new SimpleVMServiceDefinition(pipeline);
		updated.setId(service.getId());
		updated.setExecutorProvider(service.getExecutorProvider());
		updated.setDescription(service.getDescription());
		updated.setRoot(service.getRoot());
		return updated;
	}

	private boolean hasErrors(List<Validation<?>> validations) {
		for (Validation<?> validation : validations) {
			if (validation != null && validation.getSeverity() == ValidationMessage.Severity.ERROR) {
				return true;
			}
		}
		return false;
	}

	private void validateSequence(SimpleVMServiceDefinition service, Sequence sequence, List<Validation<?>> validations) {
		validations.addAll(sequence.validate(EAIResourceRepository.getInstance().getServiceContext()));
	}


	private void mergeStepMetadata(Sequence original, Sequence updated) {
		Map<Step, Step> matches = new IdentityHashMap<Step, Step>();
		matchChildren(original, updated, matches);
		for (Map.Entry<Step, Step> entry : matches.entrySet()) {
			inheritStepMetadata(entry.getKey(), entry.getValue());
		}
	}

	private void matchChildren(StepGroup original, StepGroup updated, Map<Step, Step> matches) {
		List<Step> originalChildren = original.getChildren();
		List<Step> updatedChildren = updated.getChildren();
		boolean[] used = new boolean[originalChildren.size()];
		for (int i = 0; i < updatedChildren.size(); i++) {
			Step updatedChild = updatedChildren.get(i);
			int matchIndex = findMatch(originalChildren, used, updatedChild, i);
			if (matchIndex >= 0) {
				Step originalChild = originalChildren.get(matchIndex);
				used[matchIndex] = true;
				matches.put(originalChild, updatedChild);
				if (originalChild instanceof StepGroup && updatedChild instanceof StepGroup) {
					matchChildren((StepGroup) originalChild, (StepGroup) updatedChild, matches);
				}
			}
		}
	}

	private int findMatch(List<Step> originalChildren, boolean[] used, Step updatedChild, int updatedIndex) {
		for (int i = 0; i < originalChildren.size(); i++) {
			if (!used[i] && isStrictMatch(originalChildren.get(i), updatedChild) && i == updatedIndex) {
				return i;
			}
		}
		for (int i = 0; i < originalChildren.size(); i++) {
			if (!used[i] && isStrictMatch(originalChildren.get(i), updatedChild)) {
				return i;
			}
		}
		return -1;
	}

	private boolean isStrictMatch(Step original, Step updated) {
		if (original == null || updated == null || !original.getClass().equals(updated.getClass())) {
			return false;
		}
		if (!safeEquals(original.getName(), updated.getName()) || !safeEquals(original.getLabel(), updated.getLabel()) || !safeEquals(original.getComment(), updated.getComment()) || !safeEquals(original.getDescription(), updated.getDescription())) {
			return false;
		}
		if (original instanceof Invoke) {
			Invoke left = (Invoke) original;
			Invoke right = (Invoke) updated;
			return safeEquals(left.getServiceId(), right.getServiceId()) && safeEquals(left.getResultName(), right.getResultName()) && safeEquals(left.getTarget(), right.getTarget()) && left.isTemporaryMapping() == right.isTemporaryMapping();
		}
		if (original instanceof Link) {
			Link left = (Link) original;
			Link right = (Link) updated;
			return safeEquals(left.getFrom(), right.getFrom()) && safeEquals(left.getTo(), right.getTo()) && left.isFixedValue() == right.isFixedValue();
		}
		if (original instanceof StepGroup && updated instanceof StepGroup) {
			return ((StepGroup) original).getChildren().size() == ((StepGroup) updated).getChildren().size();
		}
		return true;
	}

	private void inheritStepMetadata(Step original, Step updated) {
		if (updated.getId() == null || updated.getId().trim().isEmpty()) {
			updated.setId(original.getId());
		}
		if (updated.getLineNumber() == null) {
			updated.setLineNumber(original.getLineNumber());
		}
		if (original instanceof Invoke && updated instanceof Invoke) {
			((Invoke) updated).setX(((Invoke) original).getX());
			((Invoke) updated).setY(((Invoke) original).getY());
		}
	}

	private boolean safeEquals(Object left, Object right) {
		return left == null ? right == null : left.equals(right);
	}

	@Override
	public List<Validation<?>> deleteFragment(SimpleVMServiceDefinition artifact, String path) {
		throw new UnsupportedOperationException("Deleting fragments is not supported for VM services");
	}

	@Override
	public List<Validation<?>> createFragment(SimpleVMServiceDefinition artifact, String path, String content) {
		throw new UnsupportedOperationException("Creating fragments is not supported for VM services");
	}

	@Override
	public String getGuidelines(List<String> fragmentTypes) {
		List<String> filtered = new ArrayList<String>();
		if (fragmentTypes == null || fragmentTypes.isEmpty()) {
			filtered.add("# Artifact: blox\n\nFragments:\n- `metadata`: repository metadata around the VM service\n- `input-definition`: editable alias for the pipeline input branch\n- `output-definition`: editable alias for the pipeline output branch\n- `pipeline`: canonical editable pipeline definition\n- `service`: VM step sequence\n");
			filtered.add(super.getGuidelines(Arrays.asList("metadata")));
			filtered.add("## Fragment: pipeline\n\n"
				+ "`pipeline.xml` is the canonical editable VM pipeline definition.\n\n"
				+ "It uses structure semantics, but with VM-specific meaning:\n"
				+ "- `input` and `output` are embedded inside the pipeline\n"
				+ "- `input.xml` and `output.xml` are aliases that patch those embedded branches\n"
				+ "- input and output validation can be toggled on the corresponding pipeline elements\n"
				+ "- inherited pipeline root properties are preserved unless explicitly overridden\n\n"
				+ "Example of toggled input/output validation:\n"
				+ "```xml\n"
				+ "<structure name=\"pipeline\">\n"
				+ "\t<structure name=\"input\" validate=\"true\">\n"
				+ "\t\t<field name=\"customerId\" type=\"java.lang.String\" minOccurs=\"1\"/>\n"
				+ "\t</structure>\n"
				+ "\t<structure name=\"output\" validate=\"false\">\n"
				+ "\t\t<field name=\"result\" type=\"java.lang.String\"/>\n"
				+ "\t</structure>\n"
				+ "</structure>\n"
				+ "```\n\n"
				+ "When editing pipeline fragments, also apply the structure rules from the `structure` artifact, with the extra VM-specific notes above.");
			filtered.add("## Fragment: input-definition\n\n"
				+ "`input.xml` is an editable alias for the pipeline input definition.\n\n"
				+ "Updating it patches only the `input` branch inside `pipeline.xml`.\n"
				+ "Use this for focused input-shape edits when you do not want to rewrite the whole pipeline.");
			filtered.add("## Fragment: output-definition\n\n"
				+ "`output.xml` is an editable alias for the pipeline output definition.\n\n"
				+ "Updating it patches only the `output` branch inside `pipeline.xml`.\n"
				+ "Use this for focused output-shape edits when you do not want to rewrite the whole pipeline.");
			filtered.add("## Fragment: service\n\n"
				+ "`service.xml` is the VM step sequence.\n\n"
				+ "Update behavior:\n"
				+ "- parses the XML into a typed step tree\n"
				+ "- validates typed variable queries against the pipeline\n"
				+ "- verifies invoked services resolve\n"
				+ "- preserves visual/internal metadata like `id`, `x`, `y` and `lineNumber` best effort\n\n"
				+ "TypeScript shape:\n"
				+ "```typescript\n"
				+ "type B=boolean|\"true\"|\"false\"\n\n"
				+ "type S={comment?:string,name?:string,label?:string,disabled?:B,features?:string,description?:string}\n\n"
				+ "type Link=S&{t:\"link\",from?:string,to?:string,mask?:B,optional?:B,patch?:B,fixedValue?:B,sourceNotNull?:B}\n"
				+ "type Drop=S&{t:\"drop\",path:string}\n"
				+ "type Invoke=S&{t:\"invoke\",serviceId:string,resultName?:string,temporaryMapping?:B,invocationOrder?:number|string,target?:string,property?:{key:string,value:string}[],asynchronous?:B,recache?:B,children?:Link[]}\n"
				+ "type Throw=S&{t:\"throw\",code?:string,message?:string,data?:string,alias?:string,realm?:string,authenticationId?:string,whitelist?:B}\n"
				+ "type Break=S&{t:\"break\",count?:number|string,continueExecution?:B}\n\n"
				+ "type C=Sequence|Map|Switch|For|Throw|Break\n\n"
				+ "type Sequence=S&{t:\"sequence\",transactionVariable?:string,suppressException?:B,scopeDefaultTransaction?:B,synchronized?:B,children?:Array<Sequence|Map|Switch|For|Catch|Finally|Throw|Break>}\n"
				+ "type Map=S&{t:\"map\",children?:Array<Invoke|Link|Drop>}\n"
				+ "type Switch=S&{t:\"switch\",query?:string,children?:C[]}\n"
				+ "type For=S&{t:\"for\",variable?:string,index?:string,query?:string,batchSize?:string,into?:string,children?:C[]}\n"
				+ "type Catch=S&{t:\"catch\",variable?:string,suppressException?:B,types?:string[],codes?:string[],stacktraceRegex?:string,children?:C[]}\n"
				+ "type Finally=S&{t:\"finally\",children?:C[]}\n\n"
				+ "type Service=Sequence\n"
				+ "```\n\n"
				+ "Example:\n"
				+ "```xml\n"
				+ "<sequence>\n"
				+ "\t<invoke serviceId=\"example.customer.lookup\" resultName=\"customer\">\n"
				+ "\t\t<link from=\"input/customerId\" to=\"id\"/>\n"
				+ "\t</invoke>\n"
				+ "\t<throw code=\"NOT_FOUND\" message=\"=customer == null ? 'Missing customer' : null\"/>\n"
				+ "</sequence>\n"
				+ "```" );
			return String.join("\n\n", filtered);
		}
		filtered.add("# Artifact: blox\n");
		String metadataGuidance = super.getGuidelines(fragmentTypes);
		if (metadataGuidance != null) {
			filtered.add(metadataGuidance);
		}
		if (fragmentTypes.contains("pipeline")) {
			filtered.add("## Fragment: pipeline\n\n"
				+ "`pipeline.xml` uses structure semantics, but `input` and `output` are embedded inside the pipeline and validation can be toggled on those elements.\n"
				+ "When editing this fragment, also apply the structure rules for structure definitions.");
		}
		if (fragmentTypes.contains("input-definition")) {
			filtered.add("## Fragment: input-definition\n\n"
				+ "`input.xml` patches only the embedded `input` branch inside `pipeline.xml`.");
		}
		if (fragmentTypes.contains("output-definition")) {
			filtered.add("## Fragment: output-definition\n\n"
				+ "`output.xml` patches only the embedded `output` branch inside `pipeline.xml`.");
		}
		if (fragmentTypes.contains("service")) {
			filtered.add("## Fragment: service\n\n"
				+ "`service.xml` updates the VM step sequence. Updates validate typed variable usage and invoked services, then merge back visual/internal metadata best effort.");
		}
		return filtered.isEmpty() ? null : String.join("\n\n", filtered);
	}

	@Override
	public Class<SimpleVMServiceDefinition> getArtifactClass() {
		return SimpleVMServiceDefinition.class;
	}

	@Override
	public String getArtifactType() {
		return ARTIFACT_TYPE;
	}

	@Override
	public String getArtifactCategory() {
		return "service";
	}

	private class EditableAliasFragment implements ArtifactFragment {

		private ArtifactFragment delegate;

		public EditableAliasFragment(ArtifactFragment delegate) {
			this.delegate = delegate;
		}

		@Override
		public boolean isEditable() {
			return true;
		}

		@Override
		public boolean isRemovable() {
			return delegate.isRemovable();
		}

		@Override
		public String getPath() {
			return delegate.getPath();
		}

		@Override
		public String getContent() {
			return delegate.getContent();
		}

		@Override
		public String getContentType() {
			return delegate.getContentType();
		}

		@Override
		public String getArtifactId() {
			return delegate.getArtifactId();
		}

		@Override
		public String getFragmentType() {
			return delegate.getFragmentType();
		}

		@Override
		public Map<String, String> getProperties() {
			return delegate.getProperties();
		}
	}

	private class RepositoryEntryFragment implements ArtifactFragment {

		private SimpleVMServiceDefinition artifact;
		private String path;

		public RepositoryEntryFragment(SimpleVMServiceDefinition artifact, String path) {
			this.artifact = artifact;
			this.path = path;
		}

		@Override
		public boolean isEditable() {
			return true;
		}

		@Override
		public boolean isRemovable() {
			return true;
		}

		@Override
		public String getPath() {
			return path;
		}

		@Override
		public String getContent() {
			return readRepositoryResource(artifact, path);
		}

		@Override
		public String getContentType() {
			return CONTENT_TYPE;
		}

		@Override
		public String getArtifactId() {
			return artifact.getId();
		}

		@Override
		public String getFragmentType() {
			return PIPELINE_PATH.equals(path) ? "pipeline" : path;
		}

		@Override
		public Map<String, String> getProperties() {
			return Collections.emptyMap();
		}
	}

	private class SanitizedServiceFragment implements ArtifactFragment {

		private SimpleVMServiceDefinition artifact;

		public SanitizedServiceFragment(SimpleVMServiceDefinition artifact) {
			this.artifact = artifact;
		}

		@Override
		public boolean isEditable() {
			return true;
		}

		@Override
		public boolean isRemovable() {
			return true;
		}

		@Override
		public String getPath() {
			return SERVICE_PATH;
		}

		@Override
		public String getContent() {
			ResourceEntry entry = getResourceEntry(artifact);
			try {
				Resource resource = EAIRepositoryUtils.getResource(entry, SERVICE_PATH, false);
				try (ResourceReadableContainer readable = new ResourceReadableContainer((ReadableResource) resource)) {
					ByteArrayOutputStream output = new ByteArrayOutputStream();
					VMServiceManager.formatSequence(
						IOUtils.wrap(output),
						VMServiceManager.parseSequence(IOUtils.wrap(IOUtils.toBytes(readable), true)),
						true,
						Arrays.asList("id", "x", "y", "lineNumber")
					);
					return new String(output.toByteArray(), "UTF-8");
				}
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public String getContentType() {
			return CONTENT_TYPE;
		}

		@Override
		public String getArtifactId() {
			return artifact.getId();
		}

		@Override
		public String getFragmentType() {
			return "service";
		}

		@Override
		public Map<String, String> getProperties() {
			return Collections.emptyMap();
		}
	}

	private String readRepositoryResource(SimpleVMServiceDefinition artifact, String path) {
		ResourceEntry entry = getResourceEntry(artifact);
		try {
			Resource resource = EAIRepositoryUtils.getResource(entry, path, false);
			try (ResourceReadableContainer readable = new ResourceReadableContainer((ReadableResource) resource)) {
				return new String(IOUtils.toBytes(readable), "UTF-8");
			}
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private ResourceEntry getResourceEntry(SimpleVMServiceDefinition artifact) {
		return (ResourceEntry) EAIResourceRepository.getInstance().getEntry(artifact.getId());
	}
	
}
