package be.nabu.eai.module.services.vm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import be.nabu.eai.module.services.vm.RepositoryExecutorProvider;
import be.nabu.eai.module.types.structure.StructureManager;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.CreatableArtifactFragmentManager;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.resources.RepositoryEntry;
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

public class VMServiceArtifactFragmentManager extends DefinedServiceArtifactFragmentManager<SimpleVMServiceDefinition> implements CreatableArtifactFragmentManager<SimpleVMServiceDefinition> {

	private static final String PIPELINE_PATH = "pipeline.xml";
	private static final String SERVICE_PATH = "service.xml";
	private static final String CONTENT_TYPE = "application/xml";
	private static final String ARTIFACT_TYPE = "blox";

	@Override
	public Entry createArtifact(Entry parent, String name) {
		try {
			RepositoryEntry entry = ((RepositoryEntry) parent).createNode(name, new VMServiceManager(), true);
			SimpleVMServiceDefinition service = new SimpleVMServiceDefinition(new Pipeline(new be.nabu.libs.types.structure.Structure(), new be.nabu.libs.types.structure.Structure()));
			service.setExecutorProvider(new RepositoryExecutorProvider(entry.getRepository()));
			service.setId(entry.getId());
			new VMServiceManager().save(entry, service);
			return entry;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

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
				normalizeInvokeCoordinates(updated);
				validateInvocationOrder(updated, validations);
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

	private void validateInvocationOrder(StepGroup group, List<Validation<?>> validations) {
		if (group instanceof Map) {
			validations.addAll(((be.nabu.libs.services.vm.step.Map) group).calculateInvocationOrder());
		}
		for (Step child : group.getChildren()) {
			if (child instanceof StepGroup) {
				validateInvocationOrder((StepGroup) child, validations);
			}
		}
	}

	private void normalizeInvokeCoordinates(StepGroup group) {
		if (group instanceof Map) {
			for (Step child : group.getChildren()) {
				if (child instanceof Invoke) {
					Invoke invoke = (Invoke) child;
					if (invoke.getX() == 0) {
						invoke.setX(25 + invoke.getInvocationOrder() * 100);
					}
					if (invoke.getY() == 0) {
						invoke.setY(25 + invoke.getInvocationOrder() * 75);
					}
				}
				if (child instanceof StepGroup) {
					normalizeInvokeCoordinates((StepGroup) child);
				}
			}
			return;
		}
		for (Step child : group.getChildren()) {
			if (child instanceof StepGroup) {
				normalizeInvokeCoordinates((StepGroup) child);
			}
		}
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
		List<String> sections = new ArrayList<String>();
		sections.add(loadGuidelinesResource("/guidelines/blox.md"));
		String metadataGuidance = super.getGuidelines(Arrays.asList("metadata"));
		if (metadataGuidance != null && !metadataGuidance.trim().isEmpty()) {
			sections.add(metadataGuidance.trim());
		}
		return String.join("\n\n", sections).trim();
	}

	private String loadGuidelinesResource(String resourcePath) {
		return EAIRepositoryUtils.loadCachedClasspathResource(VMServiceArtifactFragmentManager.class, resourcePath);
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
