package be.nabu.eai.module.services.vm;

import java.io.IOException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.module.services.iface.ServiceInterfaceManager;
import be.nabu.eai.module.types.structure.StructureManager;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.ArtifactManager;
import be.nabu.eai.repository.api.BrokenReferenceArtifactManager;
import be.nabu.eai.repository.api.ModifiableNodeEntry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.api.ValidatableArtifactManager;
import be.nabu.eai.repository.api.VariableRefactorArtifactManager;
import be.nabu.libs.artifacts.api.Artifact;
import be.nabu.libs.evaluator.QueryParser;
import be.nabu.libs.evaluator.QueryPart;
import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.resources.ResourceReadableContainer;
import be.nabu.libs.resources.ResourceWritableContainer;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.api.WritableResource;
import be.nabu.libs.services.DefinedServiceInterfaceResolverFactory;
import be.nabu.libs.services.SimpleExecutionContext.SimpleServiceContext;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.DefinedServiceInterface;
import be.nabu.libs.services.vm.Pipeline;
import be.nabu.libs.services.vm.PipelineInterfaceProperty;
import be.nabu.libs.services.vm.SimpleVMServiceDefinition;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.services.vm.api.StepGroup;
import be.nabu.libs.services.vm.api.VMService;
import be.nabu.libs.services.vm.step.For;
import be.nabu.libs.services.vm.step.Invoke;
import be.nabu.libs.services.vm.step.Link;
import be.nabu.libs.services.vm.step.Sequence;
import be.nabu.libs.services.vm.step.Switch;
import be.nabu.libs.services.vm.step.Throw;
import be.nabu.libs.types.ParsedPath;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.Type;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.binding.api.Window;
import be.nabu.libs.types.binding.xml.XMLBinding;
import be.nabu.libs.types.java.BeanInstance;
import be.nabu.libs.types.java.BeanResolver;
import be.nabu.libs.validator.api.Validation;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.io.api.WritableContainer;

public class VMServiceManager implements ArtifactManager<VMService>, BrokenReferenceArtifactManager<VMService>, VariableRefactorArtifactManager<VMService>, ValidatableArtifactManager<VMService> {
	
	private static Logger logger = LoggerFactory.getLogger(VMServiceManager.class);
	
	@Override
	public VMService load(ResourceEntry entry, List<Validation<?>> messages) throws IOException, ParseException {
		Pipeline pipeline = new ServiceInterfaceManager().loadPipeline(entry, messages);
		// next we load the root sequence
		Sequence sequence = parseSequence(new ResourceReadableContainer((ReadableResource) EAIRepositoryUtils.getResource(entry, "service.xml", false)));
		
		SimpleVMServiceDefinition definition = new SimpleVMServiceDefinition(pipeline);
		definition.setExecutorProvider(new RepositoryExecutorProvider(entry.getRepository()));
		definition.setRoot(sequence);
		definition.setId(entry.getId());
		return definition;
	}

	public static Sequence parseSequence(ReadableContainer<ByteBuffer> readable) throws IOException, ParseException {
		XMLBinding sequenceBinding = new XMLBinding((ComplexType) BeanResolver.getInstance().resolve(Sequence.class), Charset.forName("UTF-8"));
		sequenceBinding.setTrimContent(false);
		Sequence sequence = null;
		try {
			sequence = TypeUtils.getAsBean(sequenceBinding.unmarshal(IOUtils.toInputStream(readable), new Window[0]), Sequence.class);
		}
		finally {
			readable.close();
		}
		return sequence;
	}

	@Override
	public List<Validation<?>> save(ResourceEntry entry, VMService artifact) throws IOException {
		new ServiceInterfaceManager().savePipeline(entry, artifact.getPipeline());
		
		// next we load the root sequence
		formatSequence(new ResourceWritableContainer((WritableResource) EAIRepositoryUtils.getResource(entry, "service.xml", true)), artifact.getRoot());
		if (entry instanceof ModifiableNodeEntry) {
			((ModifiableNodeEntry) entry).updateNode(getReferences(artifact));
		}
		return artifact.getRoot().validate(new SimpleServiceContext());		
	}

	public static void formatSequence(WritableContainer<ByteBuffer> writable, Sequence sequence) throws IOException {
		XMLBinding sequenceBinding = new XMLBinding((ComplexType) BeanResolver.getInstance().resolve(Sequence.class), Charset.forName("UTF-8"));
		sequenceBinding.setMultilineAttributes(true);
		try {
			sequenceBinding.marshal(IOUtils.toOutputStream(writable), new BeanInstance<Sequence>(sequence));
		}
		finally {
			writable.close();
		}
	}

	@Override
	public Class<VMService> getArtifactClass() {
		return VMService.class;
	}

	@Override
	public List<String> getReferences(VMService artifact) throws IOException {
		Set<String> references = new HashSet<String>();
		// add the dependent interface (if any)
		references.addAll(getInterfaceReferences(artifact));
		// all the type references (including input and output) are in the pipeline
		references.addAll(StructureManager.getComplexReferences(artifact.getPipeline()));
		// another reference are all the services that are invoked
		references.addAll(getReferencesForStep(artifact.getRoot()));
		return new ArrayList<String>(references);
	}

	private static List<String> getInterfaceReferences(VMService artifact) {
		DefinedServiceInterface value = ValueUtils.getValue(PipelineInterfaceProperty.getInstance(), artifact.getPipeline().getProperties());
		List<String> references = new ArrayList<String>();
		if (value != null) {
			references.add(value.getId());
			references.addAll(StructureManager.getComplexReferences(value.getInputDefinition()));
			references.addAll(StructureManager.getComplexReferences(value.getOutputDefinition()));
		}
		return references;
	}
	
	public static List<String> getReferencesForStep(StepGroup steps) {
		List<String> references = new ArrayList<String>();
		for (Step step : steps.getChildren()) {
			if (step instanceof Invoke) {
				String id = ((Invoke) step).getServiceId();
				if (!references.contains(id)) {
					references.add(id);
				}
				String target = ((Invoke) step).getTarget();
				// no runtime interpreted targets or $any, $all...
				if (target != null && !target.startsWith("=") && !target.startsWith("$")) {
					String[] split = target.split(":");
					references.add(split[0]);
				}
			}
			if (step instanceof StepGroup) {
				references.addAll(getReferencesForStep((StepGroup) step));
			}
		}
		return references;
	}
	
	public static void updateInterfaceReferences(VMService artifact, String from, String to) {
		DefinedServiceInterface value = ValueUtils.getValue(PipelineInterfaceProperty.getInstance(), artifact.getPipeline().getProperties());
		// update the interface property
		if (value != null && value.getId().equals(from)) {
			artifact.getPipeline().setProperty(new ValueImpl<DefinedServiceInterface>(PipelineInterfaceProperty.getInstance(), DefinedServiceInterfaceResolverFactory.getInstance().getResolver().resolve(to)));
		}
	}
	
	public static void updateReferences(StepGroup steps, String from, String to) {
		for (Step step : steps.getChildren()) {
			if (step instanceof Invoke) {
				if (from.equals(((Invoke) step).getServiceId())) {
					((Invoke) step).setServiceId(to);
				}
				String target = ((Invoke) step).getTarget();
				if (target != null && target.startsWith(from + ":")) {
					target = to + ":" + target.substring(from.length() + 1);
					((Invoke) step).setTarget(target);
				}
			}
			if (step instanceof StepGroup) {
				updateReferences((StepGroup) step, from, to);
			}
		}
	}

	@Override
	public List<Validation<?>> updateReference(VMService artifact, String from, String to) throws IOException {
		List<Validation<?>> messages = new ArrayList<Validation<?>>();
		updateInterfaceReferences(artifact, from, to);
		messages.addAll(StructureManager.updateReferences(artifact.getPipeline(), from, to));
		updateReferences(artifact.getRoot(), from, to);
		return messages;
	}

	@Override
	public List<Validation<?>> updateBrokenReference(ResourceContainer<?> container, String from, String to) throws IOException {
		List<Validation<?>> messages = new ArrayList<Validation<?>>();
		Resource child = container.getChild("service.xml");
		if (child != null) {
			EAIRepositoryUtils.updateBrokenReference(child, from, to, Charset.forName("UTF-8"));
		}
		new ServiceInterfaceManager().updateBrokenReference(container, from, to);
		return messages;
	}
	
	@Override
	public boolean updateVariableName(VMService artifact, Artifact impactedArtifact, String oldPath, String newPath) {
		logger.info("Updating '" + artifact.getId() + "' cause of change in '" + impactedArtifact.getId() + "' > '" + oldPath + "' becomes '" + newPath + "'");
		// TODO: if the impacted artifact is another service AND the oldPath & newPath start with input/output > refactor invokes!!
		// local change
		if (impactedArtifact == null || artifact.equals(impactedArtifact)) {
			if (oldPath.startsWith("pipeline/")) {
				oldPath = oldPath.substring("pipeline/".length());
			}
			if (newPath.startsWith("pipeline/")) {
				newPath = newPath.substring("pipeline/".length());
			}
			return updateVariableName(artifact, oldPath, newPath);
		}
		// dependency change
		else if (impactedArtifact instanceof Type) {
			// for complex types, the "root" name does not matter but is sent along
			String typeName = ((Type) impactedArtifact).getName();
			if (oldPath.startsWith(typeName + "/")) {
				oldPath = oldPath.substring(typeName.length() + 1);
			}
			if (newPath.startsWith(typeName + "/")) {
				newPath = newPath.substring(typeName.length() + 1);
			}
			return updateVariableName(artifact, (Type) impactedArtifact, oldPath, newPath, artifact.getPipeline(), null, new ArrayList<Type>());
		}
		// if we update the spec of a service, we may need to redraw some mappings
		else if (impactedArtifact instanceof DefinedService && (oldPath.startsWith("input") || oldPath.startsWith("output"))) {
			return updateServiceInterfaceName(artifact, artifact.getRoot(), (DefinedService) impactedArtifact, new ParsedPath(oldPath), new ParsedPath(newPath));
		}
		return false;
	}
	
	private static boolean updateVariableName(VMService artifact, Type impactedArtifact, String oldPath, String newPath, ComplexType currentType, String currentPath, List<Type> blacklisted) {
		boolean updated = false;
		System.out.println("CHECKING " + currentType + " == " + impactedArtifact);
		// any links from this path are impacted
		// because of reloads etc, the object instances might not be the same although they are in fact from the same entry
		// do an id check to verify whether they are not actually the same after all
		if (currentType.equals(impactedArtifact) || !TypeUtils.getUpcastPath(currentType, impactedArtifact).isEmpty()
				|| (currentType instanceof DefinedType && impactedArtifact instanceof DefinedType && ((DefinedType) currentType).getId().equals(((DefinedType) impactedArtifact).getId()))) {
			String relativeOldPath = currentPath == null ? oldPath : currentPath + "/" + oldPath;
			String relativeNewPath = currentPath == null ? newPath : currentPath + "/" + newPath;
			System.out.println("FOUND IMPACTED:" + currentPath + " > " + relativeOldPath + " > " + relativeNewPath);
			updated = updateVariableName(artifact, relativeOldPath, relativeNewPath);
		}
		blacklisted.add(currentType);
		for (Element<?> element : TypeUtils.getAllChildren(currentType)) {
			if (element.getType() instanceof ComplexType && !blacklisted.contains(element.getType())) {
				String childPath = currentPath == null ? element.getName() : currentPath + "/" + element.getName();
				updated |= updateVariableName(artifact, impactedArtifact, oldPath, newPath, (ComplexType) element.getType(), childPath, blacklisted);
			}
		}
		return updated;
	}

	public static boolean updateVariableName(VMService artifact, String oldPath, String newPath) {
		return updateVariableName(artifact.getRoot(), new ParsedPath(oldPath), new ParsedPath(newPath), false);
	}
	
	private static boolean updateServiceInterfaceName(VMService artifact, StepGroup group, DefinedService service, ParsedPath oldPath, ParsedPath newPath) {
		boolean updated = false;
		for (Step step : group.getChildren()) {
			// the invoke is impacted
			if (step instanceof Invoke && ((Invoke) step).getServiceId().equals(service.getId())) {
				System.out.println("FOUND IMPACTED INVOKE OF: " + service.getId());
				// we have changed an input parameter name, input mappings are inside the invoke
				if (oldPath.getName().equals("input") && newPath.getName().equals("input")) {
					// the paths in the invoke are relative to the service input
					updated |= updateVariableName((Invoke) step, oldPath.getChildPath(), newPath.getChildPath(), true);
				}
				// we have changed an output parameter name, these mappings are drawn from temporary variables
				else if (oldPath.getName().equals("output") && newPath.getName().equals("output")) {
					// the output is mapped to a temporary result set
					ParsedPath relativeOldPath = new ParsedPath(((Invoke) step).getResultName());
					relativeOldPath.setChildPath(oldPath.getChildPath());
					ParsedPath relativeNewPath = new ParsedPath(((Invoke) step).getResultName());
					relativeNewPath.setChildPath(newPath.getChildPath());
					updated |= updateVariableName(artifact.getRoot(), relativeOldPath, relativeNewPath, false);
				}
			}
			if (step instanceof StepGroup) {
				updated |= updateServiceInterfaceName(artifact, (StepGroup) step, service, oldPath, newPath);
			}
		}
		return updated;
	}
	
	private static boolean updateVariableName(StepGroup group, ParsedPath oldPath, ParsedPath newPath, boolean remapInvokes) {
		boolean updated = false;
		for (Step step : group.getChildren()) {
			// update label
			if (step.getLabel() != null) {
				String rewritten = rewriteQuery(step.getLabel(), oldPath, newPath);
				if (!rewritten.equals(step.getLabel())) {
					updated = true;
					System.out.println("Updating step 'label' from '" + step.getLabel() + "' to '" + rewritten + "'");
					step.setLabel(rewritten);
				}
			}
			if (step instanceof Link) {
				String originalFrom = ((Link) step).getFrom();
				String rewrittenFrom;
				if (originalFrom != null) {
					if (!originalFrom.startsWith("=")) {
						// if the input is a fixed value and does not start with a "=", it can not contain references, do not rewrite
						if (((Link) step).isFixedValue()) {
							rewrittenFrom = originalFrom;
						}
						else {
							ParsedPath parsed = new ParsedPath(originalFrom);
							rewrite(parsed, oldPath, newPath);
							rewrittenFrom = parsed.toString();
							// we shouldn't turn absolute references into relative ones!
							if (originalFrom.startsWith("/") && !rewrittenFrom.startsWith("/")) {
								rewrittenFrom = "/" + rewrittenFrom;
							}
						}
					}
					else {
						rewrittenFrom = "=" + rewriteQuery(originalFrom.substring(1), oldPath, newPath);
					}
					if (!rewrittenFrom.equals(originalFrom)) {
						updated = true;
						System.out.println("Updating link 'from' from '" + originalFrom + "' to '" + rewrittenFrom + "'");
						((Link) step).setFrom(rewrittenFrom);
					}
				}
				// the "to" in an invoke step is relative to the invoke statement, we don't rewrite it here
				String originalTo = ((Link) step).getTo();
				if (originalTo != null && (!(step.getParent() instanceof Invoke) || remapInvokes)) {
					String rewrittenTo;
					if (!originalTo.startsWith("=")) {
						ParsedPath parsed = new ParsedPath(originalTo);
						rewrite(parsed, oldPath, newPath);
						rewrittenTo = parsed.toString();
						// we shouldn't turn absolute references into relative ones!
						if (originalTo.startsWith("/") && !rewrittenTo.startsWith("/")) {
							rewrittenTo = "/" + rewrittenTo;
						}
					}
					else {
						rewrittenTo = "=" + rewriteQuery(originalTo.substring(1), oldPath, newPath);
					}
					if (!rewrittenTo.equals(originalTo)) {
						updated = true;
						System.out.println("Updating link 'to' from '" + originalTo + "' to '" + rewrittenTo + "'");
						((Link) step).setTo(rewrittenTo);
					}
				}
			}
			else if (step instanceof For) {
				String original = ((For) step).getQuery();
				if (original != null) {
					ParsedPath parsed = new ParsedPath(original);
					rewrite(parsed, oldPath, newPath);
					String rewritten = parsed.toString();
					// we shouldn't turn absolute references into relative ones!
					if (original.startsWith("/") && !rewritten.startsWith("/")) {
						rewritten = "/" + rewritten;
					}
					if (!rewritten.equals(original)) {
						updated = true;
						System.out.println("Updating for 'query' from '" + original + "' to '" + rewritten + "'");
						((For) step).setQuery(rewritten);
					}
				}
			}
			else if (step instanceof Throw) {
				String original = ((Throw) step).getMessage();
				if (original != null) {
					String rewritten;
					if (!original.startsWith("=")) {
						// @2017-06-02: the message is a string if not interpolate, ignore
//						ParsedPath parsed = new ParsedPath(original);
//						rewrite(parsed, oldPath, newPath);
//						rewritten = parsed.toString();
						rewritten = original;
					}
					else {
						rewritten = "=" + rewriteQuery(original.substring(1), oldPath, newPath);
					}
					if (!rewritten.equals(original)) {
						updated = true;
						System.out.println("Updating throw 'message' from '" + original + "' to '" + rewritten + "'");
						((Throw) step).setMessage(rewritten);
					}
				}
			}
			else if (step instanceof Switch) {
				String original = ((Switch) step).getQuery();
				if (original != null) {
					String rewritten = rewriteQuery(original, oldPath, newPath);
					if (!rewritten.equals(original)) {
						updated = true;
						System.out.println("Updating switch 'query' from '" + original + "' to '" + rewritten + "'");
						((Switch) step).setQuery(rewritten);
					}
				}
			}
			if (step instanceof StepGroup) {
				updated |= updateVariableName((StepGroup) step, oldPath, newPath, remapInvokes);
			}
		}
		return updated;
	}
	
	private static String flatten(ParsedPath path) {
		StringBuilder builder = new StringBuilder();
		while (path != null) {
			if (!builder.toString().isEmpty()) {
				builder.append("/");
			}
			builder.append(path.getName());
			path = path.getChildPath();
		}
		return builder.toString();
	}
	
	private static void rewrite(ParsedPath pathToRewrite, ParsedPath oldPath, ParsedPath newPath) {
		if (pathToRewrite.getName().equals(oldPath.getName())) {
			// you can update either the name of the variable itself or the location (in the parent hierarchy)
			// if you update the variable itself, the old path must end here for it to be renamed
			// if you update the a parent path hierarchy, the rest of the path needs to match
			String originalPath = flatten(pathToRewrite);
			String flatOldPath = flatten(oldPath);
			if (originalPath.equals(flatOldPath) || originalPath.startsWith(flatOldPath + "/") || oldPath.getChildPath() == null) {
				pathToRewrite.setName(newPath.getName());
				// if in the new path we don't have a child path, but we did in the old, update the current link to remove the bits that no longer match
				if (oldPath.getChildPath() != null && originalPath.startsWith(flatOldPath) && newPath.getChildPath() == null) {
					while (oldPath.getChildPath() != null && pathToRewrite.getChildPath() != null) {
						pathToRewrite.setChildPath(pathToRewrite.getChildPath().getChildPath());
						oldPath = oldPath.getChildPath();
					}
				}
				else if (oldPath.getChildPath() == null && newPath.getChildPath() != null) {
					// reinstate new path to make sure we don't interfer with others
					newPath = new ParsedPath(newPath.toString());
					ParsedPath originalChildPath = pathToRewrite.getChildPath();
					pathToRewrite.setChildPath(newPath.getChildPath());
					while (newPath.getChildPath() != null) {
						newPath = newPath.getChildPath();
					}
					newPath.setChildPath(originalChildPath);
				}
				// otherwise if we move it deeper into the hierarchy, update that
				else if (pathToRewrite.getChildPath() == null && oldPath.getChildPath() == null && newPath.getChildPath() != null) {
					pathToRewrite.setChildPath(newPath.getChildPath());
				}
			}
			if (pathToRewrite.getIndex() != null) {
				String rewriteQuery = rewriteQuery(pathToRewrite.getIndex(), oldPath, newPath);
				if (!rewriteQuery.equals(pathToRewrite.getIndex())) {
					System.out.println("Index updated from: '" + pathToRewrite.getIndex() + "' to '" + rewriteQuery + "'");
					pathToRewrite.setIndex(rewriteQuery);
				}
			}
			if (pathToRewrite.getChildPath() != null && oldPath.getChildPath() != null && newPath.getChildPath() != null) {
				rewrite(pathToRewrite.getChildPath(), oldPath.getChildPath(), newPath.getChildPath());
			}
		}
	}
	
	public static void main(String...args) {
//		System.out.println(rewriteQuery("0 - input/doc/unnamed0 * -1", new ParsedPath("input/doc/haha"), new ParsedPath("input/doc/test")));
		System.out.println(rewriteQuery("connectionId = /input/connectionId", new ParsedPath("taskProvider/transientErrors/description"), new ParsedPath("taskProvider/transientErrors/message")));
	}
	
	private static String rewriteQuery(String query, ParsedPath oldPath, ParsedPath newPath) {
		try {
			List<QueryPart> parse = QueryParser.getInstance().parse(query);
			int depth = 0;
			StringBuilder builder = new StringBuilder();
			int lastEnd = 0;
			for (QueryPart part : parse) {
				// try to recreate the original whitespace
				for (int i = lastEnd; i < part.getToken().getStart(); i++) {
					builder.append(" ");
				}
				lastEnd = part.getToken().getEnd();
				if (part.getType() == QueryPart.Type.VARIABLE) {
					if (depth == 0) {
						ParsedPath path = new ParsedPath(part.getToken().getContent());
						rewrite(path, oldPath, newPath);
						String rewrittenPath = path.toString();
						// we shouldn't turn absolute references into relative ones!
						if (part.getToken().getContent().startsWith("/") && !rewrittenPath.startsWith("/")) {
							rewrittenPath = "/" + rewrittenPath;
						}
						builder.append(rewrittenPath);
					}
					else {
						logger.warn("[!] Found variables at depth that may require rewriting, this is not supported yet");
						builder.append(part.getToken().getContent());
					}
				}
				else {
					// print the original content
					builder.append(part.getToken().getContent());
					if (part.getType() == QueryPart.Type.INDEX_START) {
						depth++;
					}
					else if (part.getType() == QueryPart.Type.INDEX_STOP) {
						depth--;
					}
				}
				
			}
			return builder.toString();
		}
		catch (ParseException e) {
			logger.warn("Can not rewrite query '" + query + "' because it is not valid", e);
			return query;
		}
	}

	@Override
	public List<? extends Validation<?>> validate(VMService service) {
		return service.getRoot().validate(EAIResourceRepository.getInstance().getServiceContext());	
	}
}

