package be.nabu.eai.module.services.vm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;


import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.impl.DefinedServiceArtifactFragmentManager;
import be.nabu.libs.resources.ResourceReadableContainer;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.services.vm.SimpleVMServiceDefinition;
import be.nabu.libs.validator.api.Validation;
import be.nabu.utils.io.IOUtils;

public class VMServiceArtifactFragmentManager extends DefinedServiceArtifactFragmentManager<SimpleVMServiceDefinition> {

	private static final String PIPELINE_PATH = "pipeline.xml";
	private static final String SERVICE_PATH = "service.xml";
	private static final String CONTENT_TYPE = "application/xml";
	private static final String ARTIFACT_TYPE = "blox";

	@Override
	public List<ArtifactFragment> listFragments(SimpleVMServiceDefinition artifact) {
		List<ArtifactFragment> fragments = new ArrayList<ArtifactFragment>(super.listFragments(artifact));
		fragments.addAll(Arrays.<ArtifactFragment>asList(
			new RepositoryEntryFragment(artifact, PIPELINE_PATH),
			new SanitizedServiceFragment(artifact)
		));
		return fragments;
	}

	@Override
	public List<Validation<?>> updateFragment(SimpleVMServiceDefinition artifact, String path, String content) {
		throw new UnsupportedOperationException("Updating fragments is not supported for VM services");
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
	public String getGuidelines() {
		return "VM services expose read-only metadata, input, output, pipeline and sanitized service fragments from the repository.";
	}

	@Override
	public Class<SimpleVMServiceDefinition> getArtifactClass() {
		return SimpleVMServiceDefinition.class;
	}

	@Override
	protected String getArtifactType(SimpleVMServiceDefinition artifact) {
		return ARTIFACT_TYPE;
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
		public String getArtifactType() {
			return VMServiceArtifactFragmentManager.this.getArtifactType(artifact);
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
		public String getArtifactType() {
			return VMServiceArtifactFragmentManager.this.getArtifactType(artifact);
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
