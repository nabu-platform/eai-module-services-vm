package be.nabu.eai.module.services.vm;

import java.io.IOException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

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

	@Override
	public VMService load(ResourceEntry entry, List<Validation<?>> messages) throws IOException, ParseException {
		Pipeline pipeline = new ServiceInterfaceManager().loadPipeline(entry, messages);
		// next we load the root sequence
		Sequence sequence = parseSequence(new ResourceReadableContainer((ReadableResource) EAIRepositoryUtils.getResource(entry, "service.xml", false)));
		
		SimpleVMServiceDefinition definition = new SimpleVMServiceDefinition(pipeline);
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
		List<String> references = new ArrayList<String>();
		// add the dependent interface (if any)
		references.addAll(getInterfaceReferences(artifact));
		// all the type references (including input and output) are in the pipeline
		references.addAll(StructureManager.getComplexReferences(artifact.getPipeline()));
		// another reference are all the services that are invoked
		references.addAll(getReferencesForStep(artifact.getRoot()));
		return references;
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
		System.out.println("Updating '" + artifact.getId() + "' cause of change in '" + impactedArtifact.getId() + "' > '" + oldPath + "' becomes '" + newPath + "'");
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
			return updateVariableName(artifact, (Type) impactedArtifact, oldPath, newPath, artifact.getPipeline(), null);
		}
		// if we update the spec of a service, we may need to redraw some mappings
		else if (impactedArtifact instanceof DefinedService && (oldPath.startsWith("input") || oldPath.startsWith("output"))) {
			return updateServiceInterfaceName(artifact, artifact.getRoot(), (DefinedService) impactedArtifact, new ParsedPath(oldPath), new ParsedPath(newPath));
		}
		return false;
	}
	
	private static boolean updateVariableName(VMService artifact, Type impactedArtifact, String oldPath, String newPath, ComplexType currentType, String currentPath) {
		boolean updated = false;
		// any links from this path are impacted
		if (currentType.equals(impactedArtifact) || !TypeUtils.getUpcastPath(currentType, impactedArtifact).isEmpty()) {
			String relativeOldPath = currentPath == null ? oldPath : currentPath + "/" + oldPath;
			String relativeNewPath = currentPath == null ? newPath : currentPath + "/" + newPath;
			updated = updateVariableName(artifact, relativeOldPath, relativeNewPath);
		}
		for (Element<?> element : TypeUtils.getAllChildren(currentType)) {
			if (element.getType() instanceof ComplexType) {
				String childPath = currentPath == null ? element.getName() : currentPath + "/" + element.getName();
				updated |= updateVariableName(artifact, impactedArtifact, oldPath, newPath, (ComplexType) element.getType(), childPath);
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
				ParsedPath parsed = new ParsedPath(step.getLabel());
				try {
					rewrite(parsed, oldPath, newPath);
					String rewritten = parsed.toString();
					if (!rewritten.equals(step.getLabel())) {
						updated = true;
						System.out.println("Updating step 'label' from '" + step.getLabel() + "' to '" + rewritten + "'");
						step.setLabel(rewritten);
					}
				}
				catch (ParseException e) {
					e.printStackTrace();
				}
			}
			if (step instanceof Link) {
				if (((Link) step).getFrom() != null && !((Link) step).getFrom().startsWith("=")) {
					ParsedPath parsed = new ParsedPath(((Link) step).getFrom());
					try {
						rewrite(parsed, oldPath, newPath);
						String rewritten = parsed.toString();
						if (!rewritten.equals(((Link) step).getFrom())) {
							updated = true;
							System.out.println("Updating link 'from' from '" + ((Link) step).getFrom() + "' to '" + rewritten + "'");
							((Link) step).setFrom(rewritten);
						}
					}
					catch (ParseException e) {
						e.printStackTrace();
					}
				}
				// the "to" in an invoke step is relative to the invoke statement, we don't rewrite it here
				if (((Link) step).getTo() != null && !((Link) step).getTo().startsWith("=") && (!(step.getParent() instanceof Invoke) || remapInvokes)) {
					ParsedPath parsed = new ParsedPath(((Link) step).getTo());
					try {
						rewrite(parsed, oldPath, newPath);
						String rewritten = parsed.toString();
						if (!rewritten.equals(((Link) step).getTo())) {
							updated = true;
							System.out.println("Updating link 'to' from '" + ((Link) step).getTo() + "' to '" + rewritten + "'");
							((Link) step).setTo(rewritten);
						}
					}
					catch (ParseException e) {
						e.printStackTrace();
					}
				}
			}
			else if (step instanceof For) {
				if (((For) step).getQuery() != null) {
					ParsedPath parsed = new ParsedPath(((For) step).getQuery());
					try {
						rewrite(parsed, oldPath, newPath);
						String rewritten = parsed.toString();
						if (!rewritten.equals(((For) step).getQuery())) {
							updated = true;
							System.out.println("Updating for 'query' from '" + ((For) step).getQuery() + "' to '" + rewritten + "'");
							((For) step).setQuery(rewritten);
						}
					}
					catch (ParseException e) {
						e.printStackTrace();
					}
				}
			}
			else if (step instanceof Throw) {
				if (((Throw) step).getMessage() != null && !((Throw) step).getMessage().startsWith("=")) {
					ParsedPath parsed = new ParsedPath(((Throw) step).getMessage());
					try {
						rewrite(parsed, oldPath, newPath);
						String rewritten = parsed.toString();
						if (!rewritten.equals(((Throw) step).getMessage())) {
							updated = true;
							System.out.println("Updating throw 'message' from '" + ((Throw) step).getMessage() + "' to '" + rewritten + "'");
							((Throw) step).setMessage(rewritten);
						}
					}
					catch (ParseException e) {
						e.printStackTrace();
					}
				}
			}
			else if (step instanceof Switch) {
				if (((Switch) step).getQuery() != null) {
					ParsedPath parsed = new ParsedPath(((Switch) step).getQuery());
					try {
						rewrite(parsed, oldPath, newPath);
						String rewritten = parsed.toString();
						if (!rewritten.equals(((Switch) step).getQuery())) {
							updated = true;
							System.out.println("Updating switch 'query' from '" + ((Switch) step).getQuery() + "' to '" + rewritten + "'");
							((Switch) step).setQuery(rewritten);
						}
					}
					catch (ParseException e) {
						e.printStackTrace();
					}
				}
			}
			if (step instanceof StepGroup) {
				updated |= updateVariableName((StepGroup) step, oldPath, newPath, remapInvokes);
			}
		}
		return updated;
	}
	
	private static void rewrite(ParsedPath pathToRewrite, ParsedPath oldPath, ParsedPath newPath) throws ParseException {
		if (pathToRewrite.getName().equals(oldPath.getName())) {
			pathToRewrite.setName(newPath.getName());
			if (pathToRewrite.getChildPath() != null && oldPath.getChildPath() != null && newPath.getChildPath() != null) {
				rewrite(pathToRewrite.getChildPath(), oldPath.getChildPath(), newPath.getChildPath());
			}
		}
	}

	@Override
	public List<? extends Validation<?>> validate(VMService service) {
		return service.getRoot().validate(EAIResourceRepository.getInstance().getServiceContext());	
	}
}

