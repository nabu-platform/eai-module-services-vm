package be.nabu.eai.module.services.vm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import be.nabu.eai.developer.api.InterfaceLister;
import be.nabu.eai.developer.util.InterfaceDescriptionImpl;

public class VMServiceInterfaceLister implements InterfaceLister {

	private static Collection<InterfaceDescription> descriptions = null;
	
	@Override
	public Collection<InterfaceDescription> getInterfaces() {
		if (descriptions == null) {
			synchronized(VMServiceInterfaceLister.class) {
				if (descriptions == null) {
					List<InterfaceDescription> descriptions = new ArrayList<InterfaceDescription>();
					descriptions.add(new InterfaceDescriptionImpl("Flow Service", "Invoke Executor", "be.nabu.eai.module.services.vm.api.ServiceExecutor.execute"));
					VMServiceInterfaceLister.descriptions = descriptions;
				}
			}
		}
		return descriptions;
	}

}
