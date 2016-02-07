package be.nabu.eai.module.services.vm.util;

import be.nabu.libs.services.vm.step.Link;
import be.nabu.eai.module.services.vm.util.Mapping.RemoveMapping;

public class RemoveLinkListener implements RemoveMapping {

	private Link link;
	
	public RemoveLinkListener(Link link) {
		this.link = link;
	}
	
	@Override
	public boolean remove(Mapping mapping) {
		if (link.getParent() != null) {
			link.getParent().getChildren().remove(link);
			return true;
		}
		return false;
	}
}
