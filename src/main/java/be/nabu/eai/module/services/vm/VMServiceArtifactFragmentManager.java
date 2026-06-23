package be.nabu.eai.module.services.vm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.libs.evaluator.types.api.TypeOperation;
import be.nabu.eai.module.services.iface.ServiceInterfaceManager;
import be.nabu.eai.module.types.structure.StructureManager;
import be.nabu.libs.types.definition.xml.XMLDefinitionMarshaller;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.CreatableArtifactFragmentManager;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.eai.repository.impl.DefinedServiceArtifactFragmentManager;
import be.nabu.eai.repository.impl.EditableAliasArtifactFragment;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.resources.ResourceReadableContainer;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.services.vm.Pipeline;
import be.nabu.libs.services.vm.SimpleVMServiceDefinition;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceContext;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.services.vm.api.StepGroup;
import be.nabu.libs.services.vm.step.LimitedStepGroup;
import be.nabu.libs.services.vm.step.Break;
import be.nabu.libs.services.vm.step.For;
import be.nabu.libs.services.vm.step.Invoke;
import be.nabu.libs.services.vm.step.Link;
import be.nabu.libs.services.vm.step.Sequence;
import be.nabu.libs.types.BaseTypeInstance;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Type;
import be.nabu.libs.types.api.TypeInstance;
import be.nabu.libs.validator.api.Validation;
import be.nabu.libs.validator.api.ValidationMessage;
import be.nabu.utils.io.IOUtils;

public class VMServiceArtifactFragmentManager extends DefinedServiceArtifactFragmentManager<SimpleVMServiceDefinition> implements CreatableArtifactFragmentManager<SimpleVMServiceDefinition> {

	private static final Logger logger = LoggerFactory.getLogger(VMServiceArtifactFragmentManager.class);
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
		Entry entry = EAIResourceRepository.getInstance().getEntry(artifact.getId());
		boolean editable = entry instanceof ResourceEntry && entry.isEditable();
		List<ArtifactFragment> fragments = new ArrayList<ArtifactFragment>();
		for (ArtifactFragment fragment : super.listFragments(artifact)) {
			if (fragment != null && ("input.xml".equals(fragment.getPath()) || "output.xml".equals(fragment.getPath()))) {
				fragments.add(new EditableAliasArtifactFragment(fragment, editable));
			}
			else {
				fragments.add(fragment);
			}
		}
		fragments.addAll(Arrays.<ArtifactFragment>asList(
			new SanitizedPipelineFragment(artifact, editable),
			new SanitizedServiceFragment(artifact, editable)
		));
		return fragments;
	}

	@Override
	public List<Validation<?>> updateFragment(SimpleVMServiceDefinition artifact, String path, String oldContent, String newContent) {
		ResourceEntry entry = (ResourceEntry) EAIResourceRepository.getInstance().getEntry(artifact.getId());
		List<Validation<?>> validations = applyFragment(entry, artifact, path, oldContent, newContent);
		if (!hasErrors(validations)) {
			try {
				validations.addAll(new VMServiceManager().save(entry, artifact));
			}
			catch (Exception e) {
				String message = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
				logger.error("Failed to save VM service fragment '{}' for artifact '{}'", path, artifact.getId(), e);
				validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, message));
			}
		}
		return validations;
	}

	public List<Validation<?>> applyFragment(ResourceEntry entry, SimpleVMServiceDefinition artifact, String path, String oldContent, String newContent) {
		List<Validation<?>> validations = new ArrayList<Validation<?>>();
		try {
			if (PIPELINE_PATH.equals(path)) {
				Pipeline updated = StructureManager.parseUpdatedStructure(entry, newContent, artifact.getPipeline(), new Pipeline(null, null), validations);
				if (!hasErrors(validations)) {
					artifact.setRoot(withPipeline(artifact, updated).getRoot());
					copyPipeline(artifact.getPipeline(), updated);
				}
				return validations;
			}
			if ("input.xml".equals(path) || "output.xml".equals(path)) {
				Pipeline updated = StructureManager.parseUpdatedStructure(entry, EAIRepositoryUtils.readResource(entry, PIPELINE_PATH), artifact.getPipeline(), new Pipeline(null, null), validations);
				if (!hasErrors(validations)) {
					be.nabu.libs.types.structure.Structure target = (be.nabu.libs.types.structure.Structure) updated.get("input.xml".equals(path) ? Pipeline.INPUT : Pipeline.OUTPUT).getType();
					StructureManager.parse(entry, newContent.getBytes("UTF-8"), validations, target);
					StructureManager.inheritRootProperties((be.nabu.libs.types.structure.Structure) artifact.getPipeline().get("input.xml".equals(path) ? Pipeline.INPUT : Pipeline.OUTPUT).getType(), target);
					if (!hasErrors(validations)) {
						copyPipeline(artifact.getPipeline(), updated);
					}
				}
				return validations;
			}
			if (SERVICE_PATH.equals(path)) {
				Sequence updated = VMServiceManager.parseSequence(IOUtils.wrap(newContent.getBytes("UTF-8"), true));
				SimpleVMServiceDefinition candidate = withPipeline(artifact, artifact.getPipeline());
				candidate.setRoot(updated);
				mergeStepMetadata(artifact.getRoot(), updated);
				validateSequence(candidate, updated, validations);
				validateAllowedChildSteps(updated, validations);
				validateForbiddenStepAttributes(updated, validations);
				validateBreakContinueTargets(updated, validations);
				validateUniqueInvokeResultNames(updated, validations);
				validateInvocationOrder(updated, validations);
				validateMapPipelineReadWriteConflicts(updated, validations);
				validateMapDropSetConflicts(updated, validations);
				validateLinkFixedValueConsistency(updated, validations);
				validateLinkCompatibility(candidate, updated, validations);
				normalizeInvokeCoordinates(updated);
				if (!hasErrors(validations)) {
					updated.setDefinition(artifact);
					artifact.setRoot(updated);
				}
				return validations;
			}
			throw new UnsupportedOperationException("Updating fragments is only supported for pipeline.xml and service.xml on VM services");
		}
		catch (Exception e) {
			String message = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
			if (entry == null) {
				message += " [artifactId=" + artifact.getId() + "]";
			}
			logger.error("Failed to update VM service fragment '{}' for artifact '{}'", path, artifact.getId(), e);
			validations.add(new ValidationMessage(ValidationMessage.Severity.ERROR, message));
			return validations;
		}
	}

	public SimpleVMServiceDefinition withPipeline(SimpleVMServiceDefinition service, Pipeline pipeline) {
		SimpleVMServiceDefinition updated = new SimpleVMServiceDefinition(pipeline);
		updated.setId(service.getId());
		updated.setExecutorProvider(service.getExecutorProvider());
		updated.setDescription(service.getDescription());
		updated.setRoot(service.getRoot());
		return updated;
	}

	public void copyPipeline(Pipeline target, Pipeline source) {
		target.removeAll();
		for (be.nabu.libs.types.api.Element<?> child : source) {
			target.add(child);
		}
		target.setProperty(source.getProperties());
	}

	protected boolean hasErrors(List<Validation<?>> validations) {
		for (Validation<?> validation : validations) {
			if (validation != null && validation.getSeverity() == ValidationMessage.Severity.ERROR) {
				return true;
			}
		}
		return false;
	}

	protected void validateSequence(SimpleVMServiceDefinition service, Sequence sequence, List<Validation<?>> validations) {
		validations.addAll(sequence.validate(EAIResourceRepository.getInstance().getServiceContext()));
	}

	protected void validateUniqueInvokeResultNames(StepGroup group, List<Validation<?>> validations) {
		validateUniqueInvokeResultNames(group, validations, new HashMap<String, Invoke>());
	}

	private void validateUniqueInvokeResultNames(StepGroup group, List<Validation<?>> validations, Map<String, Invoke> invokesByResultName) {
		for (Step child : group.getChildren()) {
			if (child instanceof Invoke) {
				Invoke invoke = (Invoke) child;
				String resultName = invoke.getResultName();
				if (resultName != null && !resultName.trim().isEmpty()) {
					Invoke previous = invokesByResultName.putIfAbsent(resultName, invoke);
					if (previous != null) {
						validations.add(addStepValidation(invoke, "invoke resultName '" + resultName + "' is already used by another invoke"));
					}
				}
			}
			if (child instanceof StepGroup) {
				validateUniqueInvokeResultNames((StepGroup) child, validations, invokesByResultName);
			}
		}
	}

	protected void validateInvocationOrder(StepGroup group, List<Validation<?>> validations) {
		if (group instanceof be.nabu.libs.services.vm.step.Map) {
			validations.addAll(((be.nabu.libs.services.vm.step.Map) group).calculateInvocationOrder());
		}
		for (Step child : group.getChildren()) {
			if (child instanceof StepGroup) {
				validateInvocationOrder((StepGroup) child, validations);
			}
		}
	}

	protected void validateAllowedChildSteps(StepGroup group, List<Validation<?>> validations) {
		if (group instanceof LimitedStepGroup) {
			Set<Class<? extends Step>> allowedSteps = ((LimitedStepGroup) group).getAllowedSteps();
			for (Step child : group.getChildren()) {
				if (!allowedSteps.contains(child.getClass())) {
					validations.add(addStepValidation(child, "<" + getStepTag(child) + "> is not allowed in <" + getStepTag((Step) group) + ">"));
				}
				if (child instanceof StepGroup) {
					validateAllowedChildSteps((StepGroup) child, validations);
				}
			}
			return;
		}
		for (Step child : group.getChildren()) {
			if (child instanceof StepGroup) {
				validateAllowedChildSteps((StepGroup) child, validations);
			}
		}
	}

	protected void validateForbiddenStepAttributes(StepGroup group, List<Validation<?>> validations) {
		for (Step child : group.getChildren()) {
			if (child instanceof Invoke || child instanceof Link || child instanceof be.nabu.libs.services.vm.step.Drop) {
				validateForbiddenStepAttributes(child, validations);
			}
			if (child instanceof StepGroup) {
				validateForbiddenStepAttributes((StepGroup) child, validations);
			}
		}
	}

	protected void validateBreakContinueTargets(StepGroup group, List<Validation<?>> validations) {
		for (Step child : group.getChildren()) {
			if (child instanceof Break) {
				validateBreakContinueTarget((Break) child, validations);
			}
			if (child instanceof StepGroup) {
				validateBreakContinueTargets((StepGroup) child, validations);
			}
		}
	}

	protected void validateMapPipelineReadWriteConflicts(StepGroup group, List<Validation<?>> validations) {
		if (group instanceof be.nabu.libs.services.vm.step.Map) {
			validateMapPipelineReadWriteConflicts((be.nabu.libs.services.vm.step.Map) group, validations);
		}
		for (Step child : group.getChildren()) {
			if (child instanceof StepGroup) {
				validateMapPipelineReadWriteConflicts((StepGroup) child, validations);
			}
		}
	}

	private void validateMapPipelineReadWriteConflicts(be.nabu.libs.services.vm.step.Map map, List<Validation<?>> validations) {
		Map<String, List<Link>> plainWritesByPath = new HashMap<String, List<Link>>();
		Map<String, List<Link>> invokeResultWritesByPath = new HashMap<String, List<Link>>();
		Map<Invoke, Set<String>> invokePipelineReads = new IdentityHashMap<Invoke, Set<String>>();
		Map<Invoke, Set<Invoke>> invokeDependencies = new IdentityHashMap<Invoke, Set<Invoke>>();
		Map<String, Invoke> invokesByResultName = new HashMap<String, Invoke>();
		for (Step child : map.getChildren()) {
			if (child instanceof Invoke) {
				Invoke invoke = (Invoke) child;
				String resultName = invoke.getResultName();
				if (resultName != null && !resultName.trim().isEmpty()) {
					invokesByResultName.put(resultName, invoke);
				}
			}
		}
		for (Step child : map.getChildren()) {
			if (child instanceof Link) {
				Link link = (Link) child;
				String target = normalizePath(link.getTo());
				String source = link.isFixedValue() ? null : normalizePath(link.getFrom());
				if (target == null) {
					continue;
				}
				Invoke sourceInvoke = getInvokeForPath(source, invokesByResultName);
				if (sourceInvoke == null) {
					addLinkByPath(plainWritesByPath, target, link);
				}
				else {
					addLinkByPath(invokeResultWritesByPath, target, link);
				}
			}
			else if (child instanceof Invoke) {
				Invoke invoke = (Invoke) child;
				Set<String> reads = new HashSet<String>();
				Set<Invoke> dependencies = new HashSet<Invoke>();
				for (Step invokeChild : invoke.getChildren()) {
					if (!(invokeChild instanceof Link)) {
						continue;
					}
					Link link = (Link) invokeChild;
					if (link.isFixedValue()) {
						continue;
					}
					String source = normalizePath(link.getFrom());
					if (source == null) {
						continue;
					}
					Invoke sourceInvoke = getInvokeForPath(source, invokesByResultName);
					if (sourceInvoke == null) {
						reads.add(source);
					}
					else if (!sourceInvoke.equals(invoke)) {
						dependencies.add(sourceInvoke);
					}
				}
				invokePipelineReads.put(invoke, reads);
				invokeDependencies.put(invoke, dependencies);
			}
		}
		for (Map.Entry<Invoke, Set<String>> entry : invokePipelineReads.entrySet()) {
			Invoke invoke = entry.getKey();
			for (String readPath : entry.getValue()) {
				for (Map.Entry<String, List<Link>> plainWriteEntry : plainWritesByPath.entrySet()) {
					if (pathsOverlap(readPath, plainWriteEntry.getKey())) {
						for (Link link : plainWriteEntry.getValue()) {
							validations.add(addStepValidation(link, "plain pipeline link target '" + link.getTo() + "' conflicts with invoke input read '" + readPath + "' in the same map step"));
						}
					}
				}
				for (Map.Entry<String, List<Link>> invokeWriteEntry : invokeResultWritesByPath.entrySet()) {
					if (!pathsOverlap(readPath, invokeWriteEntry.getKey())) {
						continue;
					}
					for (Link link : invokeWriteEntry.getValue()) {
						Invoke writerInvoke = getInvokeForPath(normalizePath(link.getFrom()), invokesByResultName);
						if (writerInvoke == null || (!writerInvoke.equals(invoke) && !dependsOn(writerInvoke, invoke, invokeDependencies, new HashSet<Invoke>()))) {
							validations.add(addStepValidation(link, "invoke result write to '" + link.getTo() + "' conflicts with invoke input read '" + readPath + "' in the same map step"));
						}
					}
				}
			}
		}
		for (Map.Entry<String, List<Link>> plainWriteEntry : plainWritesByPath.entrySet()) {
			for (Map.Entry<String, List<Link>> invokeWriteEntry : invokeResultWritesByPath.entrySet()) {
				if (pathsOverlap(plainWriteEntry.getKey(), invokeWriteEntry.getKey())) {
					for (Link link : invokeWriteEntry.getValue()) {
						validations.add(addStepValidation(link, "invoke result write to '" + link.getTo() + "' conflicts with a plain pipeline link target in the same map step"));
					}
				}
			}
		}
	}

	private void addLinkByPath(Map<String, List<Link>> linksByPath, String path, Link link) {
		List<Link> links = linksByPath.get(path);
		if (links == null) {
			links = new ArrayList<Link>();
			linksByPath.put(path, links);
		}
		links.add(link);
	}

	private Invoke getInvokeForPath(String path, Map<String, Invoke> invokesByResultName) {
		if (path == null) {
			return null;
		}
		for (Map.Entry<String, Invoke> entry : invokesByResultName.entrySet()) {
			if (isSameOrChildPath(path, entry.getKey())) {
				return entry.getValue();
			}
		}
		return null;
	}

	private boolean dependsOn(Invoke invoke, Invoke dependency, Map<Invoke, Set<Invoke>> invokeDependencies, Set<Invoke> visited) {
		if (!visited.add(invoke)) {
			return false;
		}
		Set<Invoke> dependencies = invokeDependencies.get(invoke);
		if (dependencies == null || dependencies.isEmpty()) {
			return false;
		}
		if (dependencies.contains(dependency)) {
			return true;
		}
		for (Invoke candidate : dependencies) {
			if (dependsOn(candidate, dependency, invokeDependencies, visited)) {
				return true;
			}
		}
		return false;
	}

	protected void validateMapDropSetConflicts(StepGroup group, List<Validation<?>> validations) {
		if (group instanceof be.nabu.libs.services.vm.step.Map) {
			validateMapDropSetConflicts((be.nabu.libs.services.vm.step.Map) group, validations);
		}
		for (Step child : group.getChildren()) {
			if (child instanceof StepGroup) {
				validateMapDropSetConflicts((StepGroup) child, validations);
			}
		}
	}

	private void validateMapDropSetConflicts(be.nabu.libs.services.vm.step.Map map, List<Validation<?>> validations) {
		List<String> dropPaths = new ArrayList<String>();
		for (Step child : map.getChildren()) {
			if (child instanceof be.nabu.libs.services.vm.step.Drop) {
				String path = normalizePath(((be.nabu.libs.services.vm.step.Drop) child).getPath());
				if (path != null) {
					dropPaths.add(path);
				}
			}
		}
		if (dropPaths.isEmpty()) {
			return;
		}
		for (Step child : map.getChildren()) {
			if (child instanceof Link) {
				Link link = (Link) child;
				String target = normalizePath(link.getTo());
				if (target != null) {
					for (String dropPath : dropPaths) {
						if (isSameOrChildPath(target, dropPath)) {
							validations.add(addStepValidation(link, "link target '" + link.getTo() + "' is set in the same map step where '" + dropPath + "' is dropped"));
						}
					}
				}
			}
		}
	}

	private String normalizePath(String path) {
		if (path == null) {
			return null;
		}
		path = path.trim();
		while (path.startsWith("/")) {
			path = path.substring(1);
		}
		while (path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}
		return path.isEmpty() ? null : path;
	}

	private boolean isSameOrChildPath(String target, String dropPath) {
		return target.equals(dropPath) || target.startsWith(dropPath + "/") || target.startsWith(dropPath + ".");
	}

	private boolean pathsOverlap(String left, String right) {
		return isSameOrChildPath(left, right) || isSameOrChildPath(right, left);
	}

	protected void validateLinkFixedValueConsistency(StepGroup group, List<Validation<?>> validations) {
		for (Step child : group.getChildren()) {
			if (child instanceof Link) {
				validateLinkFixedValueConsistency((Link) child, validations);
			}
			if (child instanceof StepGroup) {
				validateLinkFixedValueConsistency((StepGroup) child, validations);
			}
		}
	}

	private void validateLinkFixedValueConsistency(Link link, List<Validation<?>> validations) {
		String from = link.getFrom();
		if (!link.isFixedValue() && from != null && from.startsWith("=")) {
			validations.add(addStepValidation(link, "from starts with '=' but fixedValue is false on <link>"));
		}
	}

	protected void validateLinkCompatibility(SimpleVMServiceDefinition service, StepGroup group, List<Validation<?>> validations) {
		ServiceContext serviceContext = EAIResourceRepository.getInstance().getServiceContext();
		for (Step child : group.getChildren()) {
			if (child instanceof Link) {
				validateLinkCompatibility(service, (Link) child, serviceContext, validations);
			}
			if (child instanceof StepGroup) {
				validateLinkCompatibility(service, (StepGroup) child, validations);
			}
		}
	}

	private void validateLinkCompatibility(SimpleVMServiceDefinition service, Link link, ServiceContext serviceContext, List<Validation<?>> validations) {
		String from = link.getFrom();
		if (from == null || link.isFixedValue()) {
			return;
		}
		try {
			ComplexType sourceContext = link.getParent().getPipeline(serviceContext);
			ComplexType targetContext = getLinkTargetContext(link, serviceContext);
			if (targetContext == null) {
				return;
			}
			TypeInstance fromInstance = getReturnTypeInstance(link, from, sourceContext, validations);
			TypeInstance toInstance = link.getTo() == null ? new BaseTypeInstance(targetContext) : getReturnTypeInstance(link, link.getTo(), targetContext, validations);
			if (fromInstance != null && toInstance != null && !service.isMappable(fromInstance, toInstance) && !isAllowedMaskedMapping(link, fromInstance, toInstance)) {
				validations.add(addStepValidation(link, "link from '" + from + "' to '" + link.getTo() + "' maps incompatible types: " + fromInstance.getType() + " -> " + toInstance.getType()));
			}
		}
		catch (Exception e) {
			String message = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
			validations.add(addStepValidation(link, "could not validate link compatibility from '" + from + "' to '" + link.getTo() + "': " + message));
		}
	}

	private ComplexType getLinkTargetContext(Link link, ServiceContext serviceContext) {
		if (link.getParent() instanceof Invoke) {
			Service targetService = ((Invoke) link.getParent()).getService(serviceContext);
			return targetService == null ? null : targetService.getServiceInterface().getInputDefinition();
		}
		return link.getParent().getPipeline(serviceContext);
	}

	private TypeInstance getReturnTypeInstance(Link link, String query, ComplexType context, List<Validation<?>> validations) throws Exception {
		TypeOperation operation = link.getOperation(query);
		int size = validations.size();
		validations.addAll(operation.validate(context));
		if (validations.size() > size) {
			return null;
		}
		Type returnType = operation.getReturnType(context);
		Value<?>[] properties = operation.getReturnProperties(context);
		return properties == null ? new BaseTypeInstance(returnType) : new BaseTypeInstance(returnType, properties);
	}

	private boolean isAllowedMaskedMapping(Link link, TypeInstance fromInstance, TypeInstance toInstance) {
		return link.getMask() != null && link.getMask() && fromInstance.getType() instanceof ComplexType && toInstance.getType() instanceof ComplexType;
	}

	private void validateBreakContinueTarget(Break breakStep, List<Validation<?>> validations) {
		if (breakStep.getContinueExecution() == null || !breakStep.getContinueExecution()) {
			return;
		}
		int count = breakStep.getCount();
		StepGroup current = breakStep.getParent();
		while (current != null) {
			if (current instanceof Sequence || current instanceof For) {
				count--;
				if (count == 0) {
					if (current instanceof Sequence) {
						validations.add(addStepValidation(breakStep, "continue is not allowed when break targets a <sequence>"));
					}
					return;
				}
			}
			current = current.getParent();
		}
	}

	private void validateForbiddenStepAttributes(Step step, List<Validation<?>> validations) {
		validateForbiddenStepAttribute(step, "label", step.getLabel(), validations);
		validateForbiddenStepAttribute(step, "comment", step.getComment(), validations);
		validateForbiddenStepAttribute(step, "description", step.getDescription(), validations);
		validateForbiddenStepAttribute(step, "name", step.getName(), validations);
		validateForbiddenStepAttribute(step, "features", step.getFeatures(), validations);
		if (step.isDisabled()) {
			validations.add(addStepValidation(step, "disabled is not allowed on <" + getStepTag(step) + ">"));
		}
	}

	private void validateForbiddenStepAttribute(Step step, String attribute, String value, List<Validation<?>> validations) {
		if (value != null && !value.trim().isEmpty()) {
			validations.add(addStepValidation(step, attribute + " is not allowed on <" + getStepTag(step) + ">"));
		}
	}

	private ValidationMessage addStepValidation(Step step, String message) {
		String id = step.getId();
		return new ValidationMessage(ValidationMessage.Severity.ERROR, id == null || id.trim().isEmpty() ? message : message + " [" + id + "]");
	}

	private String getStepTag(Step step) {
		return step.getClass().getSimpleName().toLowerCase();
	}

	protected void normalizeInvokeCoordinates(StepGroup group) {
		if (group instanceof be.nabu.libs.services.vm.step.Map) {
			Map<Integer, Integer> offsets = new HashMap<Integer, Integer>();
			int invokeCounter = 0;
			for (Step child : group.getChildren()) {
				if (child instanceof Invoke) {
					Invoke invoke = (Invoke) child;
					int invocationOrder = invoke.getInvocationOrder();
					Integer offset = offsets.get(invocationOrder);
					if (offset == null) {
						offset = 0;
					}
					else {
						offset++;
					}
					offsets.put(invocationOrder, offset);
					double x = invoke.getX();
					double y = invoke.getY();
					if (x <= 0) {
						invoke.setX(25 + invocationOrder * 250);
					}
					if (y <= 0) {
//						invoke.setY(25 + offset * 150);
						invoke.setY(25 + invokeCounter * 85);
					}
					invokeCounter++;
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

	protected void mergeStepMetadata(Sequence original, Sequence updated) {
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
		updated.setId(original.getId());
		if (updated.getLineNumber() == null) {
			updated.setLineNumber(original.getLineNumber());
		}
		if (original instanceof Invoke && updated instanceof Invoke) {
			Invoke originalInvoke = (Invoke) original;
			Invoke updatedInvoke = (Invoke) updated;
			if (originalInvoke.getX() > 0) {
				updatedInvoke.setX(originalInvoke.getX());
			}
			if (originalInvoke.getY() > 0) {
				updatedInvoke.setY(originalInvoke.getY());
			}
		}
	}

	private boolean safeEquals(Object left, Object right) {
		return left == null ? right == null : left.equals(right);
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


	private class RepositoryEntryFragment implements ArtifactFragment {

		private SimpleVMServiceDefinition artifact;
		private String path;
		private boolean editable;

		public RepositoryEntryFragment(SimpleVMServiceDefinition artifact, String path, boolean editable) {
			this.artifact = artifact;
			this.path = path;
			this.editable = editable;
		}

		@Override
		public boolean isEditable() {
			return editable;
		}

		@Override
		public boolean isRemovable() {
			return editable;
		}

		@Override
		public String getPath() {
			return path;
		}

		@Override
		public String getContent() {
			ResourceEntry entry = (ResourceEntry) EAIResourceRepository.getInstance().getEntry(artifact.getId());
			return EAIRepositoryUtils.readResource(entry, path);
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

		@Override
		public Long getLastModified() {
			return getFragmentLastModified(artifact.getId(), path);
		}
	}

	private class SanitizedPipelineFragment implements ArtifactFragment {

		private SimpleVMServiceDefinition artifact;
		private boolean editable;

		public SanitizedPipelineFragment(SimpleVMServiceDefinition artifact, boolean editable) {
			this.artifact = artifact;
			this.editable = editable;
		}

		@Override
		public boolean isEditable() {
			return editable;
		}

		@Override
		public boolean isRemovable() {
			return editable;
		}

		@Override
		public String getPath() {
			return PIPELINE_PATH;
		}

		@Override
		public String getContent() {
			ResourceEntry entry = (ResourceEntry) EAIResourceRepository.getInstance().getEntry(artifact.getId());
			try {
				XMLDefinitionMarshaller marshaller = new XMLDefinitionMarshaller();
				marshaller.setIgnoreUnknownSuperTypes(true);
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				marshaller.marshal(output, new ServiceInterfaceManager().loadPipeline(entry, new ArrayList<Validation<?>>()));
				return new String(output.toByteArray(), "UTF-8");
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
			return "pipeline";
		}

		@Override
		public Map<String, String> getProperties() {
			return Collections.emptyMap();
		}

		@Override
		public Long getLastModified() {
			return getFragmentLastModified(artifact.getId(), PIPELINE_PATH);
		}
	}

	private class SanitizedServiceFragment implements ArtifactFragment {

		private SimpleVMServiceDefinition artifact;
		private boolean editable;

		public SanitizedServiceFragment(SimpleVMServiceDefinition artifact, boolean editable) {
			this.artifact = artifact;
			this.editable = editable;
		}

		@Override
		public boolean isEditable() {
			return editable;
		}

		@Override
		public boolean isRemovable() {
			return editable;
		}

		@Override
		public String getPath() {
			return SERVICE_PATH;
		}

		@Override
		public String getContent() {
			ResourceEntry entry = (ResourceEntry) EAIResourceRepository.getInstance().getEntry(artifact.getId());
			try {
				Resource resource = ResourceUtils.resolve(entry.getContainer(), SERVICE_PATH);
				if (resource == null) {
					throw new IOException("Can not find " + SERVICE_PATH);
				}
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
			return getDefinedServiceProperties(artifact);
		}

		@Override
		public Long getLastModified() {
			return getFragmentLastModified(artifact.getId(), SERVICE_PATH);
		}
	}

}
