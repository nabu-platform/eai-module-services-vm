package be.nabu.eai.module.services.vm;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import be.nabu.eai.developer.managers.base.BaseConfigurationGUIManager;
import be.nabu.eai.developer.managers.util.SimpleProperty;
import be.nabu.eai.module.services.vm.api.ServiceExecutor;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.api.Repository;
import be.nabu.libs.artifacts.api.Artifact;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.services.DefinedServiceInterfaceResolverFactory;
import be.nabu.libs.services.api.ClusteredServiceRunner;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.DefinedServiceInterface;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceResult;
import be.nabu.libs.services.api.ServiceRunnableObserver;
import be.nabu.libs.services.api.ServiceRunner;
import be.nabu.libs.services.pojo.POJOUtils;
import be.nabu.libs.services.vm.api.ExecutorProvider;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.Element;

// make sure that we validate (at design time) that the service has no output (or no mandatory output?) when selecting $all
public class RepositoryExecutorProvider implements ExecutorProvider {

	private static List<String> targets;
	private Repository repository;
	
	public RepositoryExecutorProvider(Repository repository) {
		this.repository = repository;
	}
	
	@Override
	public boolean isBatch(String target) {
		return target != null && (target.equals("$all") || target.endsWith(":$all"));
	}
	
	@Override
	public boolean isAsynchronous(String target) {
		return target != null && (target.equals("$all") || target.endsWith(":$all") || target.equals("$any") || target.endsWith(":$any"));
	}
	
	@Override
	public ServiceRunner getRunner(final String target, Map<String, ?> properties) {
		if (target == null) {
			return repository.getServiceRunner();
		}
		else if (target.equals("$any") || target.endsWith(":$any")) {
			if (repository.getServiceRunner() instanceof ClusteredServiceRunner) {
				return new ServiceRunner() {
					@Override
					public Future<ServiceResult> run(Service service, ExecutionContext context, ComplexContent input, ServiceRunnableObserver...observers) {
						((ClusteredServiceRunner) repository.getServiceRunner()).runAnywhere(service, context, input, target.endsWith(":$any") ? target.substring(0, target.length() - ":$any".length()) : null);
						return null;
					}
				};
			}
			else {
				throw new IllegalStateException("The repository service runner is not a clustered one");
			}
		}
		else if (target.equals("$all") || target.endsWith(":$all")) {
			if (repository.getServiceRunner() instanceof ClusteredServiceRunner) {
				return new ServiceRunner() {
					@Override
					public Future<ServiceResult> run(Service service, ExecutionContext context, ComplexContent input, ServiceRunnableObserver...observers) {
						((ClusteredServiceRunner) repository.getServiceRunner()).runEverywhere(service, context, input, target.endsWith(":$all") ? target.substring(0, target.length() - ":$all".length()) : null);
						return null;
					}
				};
			}
			else {
				throw new IllegalStateException("The repository service runner is not a clustered one");
			}
		}
//		else if (target.equals("$all")) {
//			try {
//				List<ServiceRunner> runners = new ArrayList<ServiceRunner>();
//				// we want the direct runner for our own system, not a remote one
//				runners.add(repository.getServiceRunner());
//				ClusterArtifact ownCluster = Services.getOwnCluster(ServiceRuntime.getRuntime().getExecutionContext());
//				// even if you are not in a cluster or alone in a cluster, run as batch
//				// we don't want "different" behavior depending on the server setup
//				if (ownCluster != null) {
//					for (String peer : Services.getPeers(ownCluster)) {
//						runners.add(ownCluster.getConnection(peer).getRemote());
//					}
//				}
//				return new CombinedServiceRunner(runners);
//			}
//			catch (SocketException e) {
//				throw new RuntimeException(e);
//			}
//			catch (UnknownHostException e) {
//				throw new RuntimeException(e);
//			}
//			catch (IOException e) {
//				throw new RuntimeException(e);
//			}
//		}
//		else if (target.equals("$other")) {
//			try {
//				ClusterArtifact ownCluster = Services.getOwnCluster(ServiceRuntime.getRuntime().getExecutionContext());
//				List<String> peers = Services.getPeers(ownCluster);
//				if (peers.isEmpty()) {
//					return repository.getServiceRunner();
//				}
//				else {
//					return ownCluster.getConnection(peers.get(0)).getRemote();
//				}
//			}
//			catch (SocketException e) {
//				throw new RuntimeException(e);
//			}
//			catch (UnknownHostException e) {
//				throw new RuntimeException(e);
//			}
//			catch (IOException e) {
//				throw new RuntimeException(e);
//			}
//		}
		else {
			String[] split = target.split(":");
			Artifact resolve = repository.resolve(split[0]);
			// we have a ServiceRunner
			if (resolve instanceof DefinedService) {
				return new ServiceRunner() {
					@Override
					public Future<ServiceResult> run(Service service, ExecutionContext context, ComplexContent input, ServiceRunnableObserver...observers) {
						DefinedService runner = (DefinedService) resolve;
						ComplexContent runnerInput = runner.getServiceInterface().getInputDefinition().newInstance();
						runnerInput.set("input",  input);
						runnerInput.set("serviceId", ((DefinedService) service).getId());
						if (properties != null) {
							for (String key : properties.keySet()) {
								runnerInput.set(key, properties.get(key));
							}
						}
						return repository.getServiceRunner().run(runner, context, runnerInput, observers);
					}
				};
			}
			else if (resolve instanceof ServiceRunner) {
				return (ServiceRunner) resolve;
			}
			else {
				throw new RuntimeException("Illegal target: " + target);
			}
		}
	}

	@Override
	public List<String> getTargets() {
		// we don't include the actual names of other hosts in the cluster as this is environment-specific
		List<String> targets = new ArrayList<String>();
//		targets.add("$self");
		// in the beginning self == any, in the future other logic can be used to select a server, for example "heavy" processes could be delegated to servers that are idling
		targets.add("$any");
		targets.add("$all");
		// at some point we should choose a different server based on load etc
//		targets.add("$other");
		// TODO: in the future we could add other clusters than your own with the same toggles, e.g.:
		// cluster1:$any
		// cluster1:$all
		// at that point we need to add the referenced clusters to the "references" so it can get picked up by the deployer etc
		for (ServiceRunner runner : repository.getArtifacts(ServiceRunner.class)) {
			if (runner instanceof Artifact) {
				targets.add(((Artifact) runner).getId() + ":$self");
				targets.add(((Artifact) runner).getId() + ":$any");
//							targets.add(((Artifact) runner).getId() + ":$all");
			}
		}
		DefinedServiceInterface iface = DefinedServiceInterfaceResolverFactory.getInstance().getResolver().resolve(ServiceExecutor.class.getName() + ".execute");		
		for (DefinedService service : repository.getArtifacts(DefinedService.class)) {
			if (POJOUtils.isImplementation(service, iface)) {
				targets.add(service.getId());
			}
		}
		return targets;
	}

	@Override
	public List<Property<?>> getTargetProperties(String target) {
		List<Property<?>> properties = new ArrayList<Property<?>>();
		Artifact resolve = repository.resolve(target);
		if (resolve instanceof DefinedService) {
			Method method = EAIRepositoryUtils.getMethod(ServiceExecutor.class, "execute");
			List<Element<?>> inputExtensions = EAIRepositoryUtils.getInputExtensions((DefinedService) resolve, method);
			if (!inputExtensions.isEmpty()) {
				for (Element<?> element : inputExtensions) {
					properties.addAll(BaseConfigurationGUIManager.createProperty(element));
				}
			}
		}
		for (Property<?> property: properties) {
			if (property instanceof SimpleProperty) {
				((SimpleProperty<?>) property).setEvaluatable(true);
			}
		}
		return properties;
	}

}
