package be.nabu.eai.module.services.vm;

import java.io.IOException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import be.nabu.eai.module.services.iface.ServiceInterfaceManager;
import be.nabu.eai.module.types.structure.StructureManager;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.api.ArtifactManager;
import be.nabu.eai.repository.api.ModifiableNodeEntry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.resources.ResourceReadableContainer;
import be.nabu.libs.resources.ResourceWritableContainer;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.WritableResource;
import be.nabu.libs.services.DefinedServiceInterfaceResolverFactory;
import be.nabu.libs.services.SimpleExecutionContext.SimpleServiceContext;
import be.nabu.libs.services.api.DefinedServiceInterface;
import be.nabu.libs.services.vm.Pipeline;
import be.nabu.libs.services.vm.PipelineInterfaceProperty;
import be.nabu.libs.services.vm.SimpleVMServiceDefinition;
import be.nabu.libs.services.vm.api.Step;
import be.nabu.libs.services.vm.api.StepGroup;
import be.nabu.libs.services.vm.api.VMService;
import be.nabu.libs.services.vm.step.Invoke;
import be.nabu.libs.services.vm.step.Sequence;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexType;
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

public class VMServiceManager implements ArtifactManager<VMService> {

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

}
