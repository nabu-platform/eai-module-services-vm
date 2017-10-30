package be.nabu.eai.module.services.vm;

import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import nabu.misc.cluster.Services;
import be.nabu.eai.module.cluster.ClusterArtifact;
import be.nabu.eai.repository.api.Repository;
import be.nabu.libs.artifacts.api.Artifact;
import be.nabu.libs.services.CombinedServiceRunner;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.api.ServiceRunner;
import be.nabu.libs.services.vm.api.ExecutorProvider;

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
	public ServiceRunner getRunner(String target) {
		if (target == null || target.equals("$any") || target.equals("$self")) {
			return repository.getServiceRunner();
		}
		else if (target.equals("$all")) {
			try {
				List<ServiceRunner> runners = new ArrayList<ServiceRunner>();
				// we want the direct runner for our own system, not a remote one
				runners.add(repository.getServiceRunner());
				ClusterArtifact ownCluster = Services.getOwnCluster(ServiceRuntime.getRuntime().getExecutionContext());
				// even if you are not in a cluster or alone in a cluster, run as batch
				// we don't want "different" behavior depending on the server setup
				if (ownCluster != null) {
					for (String peer : Services.getPeers(ownCluster)) {
						runners.add(ownCluster.getConnection(peer).getRemote());
					}
				}
				return new CombinedServiceRunner(runners);
			}
			catch (SocketException e) {
				throw new RuntimeException(e);
			}
			catch (UnknownHostException e) {
				throw new RuntimeException(e);
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		else if (target.equals("$other")) {
			try {
				ClusterArtifact ownCluster = Services.getOwnCluster(ServiceRuntime.getRuntime().getExecutionContext());
				List<String> peers = Services.getPeers(ownCluster);
				if (peers.isEmpty()) {
					return repository.getServiceRunner();
				}
				else {
					return ownCluster.getConnection(peers.get(0)).getRemote();
				}
			}
			catch (SocketException e) {
				throw new RuntimeException(e);
			}
			catch (UnknownHostException e) {
				throw new RuntimeException(e);
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		else {
			String[] split = target.split(":");
			Artifact resolve = repository.resolve(split[0]);
			if (resolve instanceof ServiceRunner) {
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
		targets.add("$self");
		// in the beginning self == any, in the future other logic can be used to select a server, for example "heavy" processes could be delegated to servers that are idling
		targets.add("$any");
//					targets.add("$all");
		// at some point we should choose a different server based on load etc
//					targets.add("$other");
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
		return targets;
	}

}
